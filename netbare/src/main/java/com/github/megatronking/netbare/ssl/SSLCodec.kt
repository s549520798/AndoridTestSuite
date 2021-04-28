/*  NetBare - An android network capture and injection library.
 *  Copyright (C) 2018-2019 Megatron King
 *  Copyright (C) 2018-2019 GuoShi
 *
 *  NetBare is free software: you can redistribute it and/or modify it under the terms
 *  of the GNU General Public License as published by the Free Software Found-
 *  ation, either version 3 of the License, or (at your option) any later version.
 *
 *  NetBare is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 *  PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with NetBare.
 *  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.megatronking.netbare.ssl

import android.os.Build
import androidx.annotation.RequiresApi
import com.github.megatronking.netbare.NetBareLog
import com.github.megatronking.netbare.ssl.SSLCodec.CodecCallback
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLException

/**
 * A base class for encrypting and decrypting SSL packets. Use [CodecCallback] to
 * observe actions and receive output packets.
 *
 *
 * SSL handshake steps:
 *
 * client          server          message
 * ======          ======          =======
 * wrap()          ...             ClientHello
 * ...             unwrap()        ClientHello
 * ...             wrap()          ServerHello/Certificate
 * unwrap()        ...             ServerHello/Certificate
 * wrap()          ...             ClientKeyExchange
 * wrap()          ...             ChangeCipherSpec
 * wrap()          ...             Finished
 * ...             unwrap()        ClientKeyExchange
 * ...             unwrap()        ChangeCipherSpec
 * ...             unwrap()        Finished
 * ...             wrap()          ChangeCipherSpec
 * ...             wrap()          Finished
 * unwrap()        ...             ChangeCipherSpec
 * unwrap()        ...             Finished
 *
 * @author Megatron King
 * @since 2018-11-15 17:46
 */
abstract class SSLCodec internal constructor(private val mSSLEngineFactory: SSLEngineFactory?) {
    private var mEngineClosed = false
    private var mHandshakeStarted = false
    private var mHandshakeFinished = false
    private val mPlaintextBuffers: Queue<ByteBuffer>

    /**
     * Create an [SSLEngine] instance to encode and decode SSL packets.
     *
     * @param factory A factory produces [SSLEngine].
     * @return An instance of [SSLEngine].
     * @throws IOException If an I/O error has occurred.
     */
    @Throws(IOException::class)
    protected abstract fun createEngine(factory: SSLEngineFactory?): SSLEngine?

    /**
     * Handshake with the client or server and try to decode a SSL encrypt packet.
     *
     * @param buffer The SSL encrypt packet.
     * @param callback A callback to observe actions and receive output packets.
     * @throws IOException If an I/O error has occurred.
     */
    @Throws(IOException::class)
    open fun decode(buffer: ByteBuffer?, callback: CodecCallback) {
        val verifyResult = SSLUtils.verifyPacket(buffer)
        if (!mHandshakeStarted) {
            if (verifyResult == SSLUtils.PACKET_NOT_ENCRYPTED) {
                callback.onDecrypt(buffer)
                return
            }
        }
        if (verifyResult == SSLUtils.PACKET_NOT_ENOUGH) {
            callback.onPending(buffer)
            return
        }
        decode(createEngine(mSSLEngineFactory), buffer, callback)
    }

    /**
     * Try to encrypt a plaintext packet. If SSL handshake has finished, then encode it,
     * otherwise add it to queue and wait handshake finished.
     *
     * @param buffer The plaintext packet.
     * @param callback A callback to observe actions and receive output packets.
     * @throws IOException If an I/O error has occurred.
     */
    @Throws(IOException::class)
    fun encode(buffer: ByteBuffer, callback: CodecCallback) {
        if (!buffer.hasRemaining()) {
            return
        }
        if (mHandshakeFinished) {
            wrap(createEngine(mSSLEngineFactory), buffer, callback)
        } else {
            mPlaintextBuffers.offer(buffer)
        }
    }

    @Throws(IOException::class)
    private fun decode(engine: SSLEngine?, input: ByteBuffer?, callback: CodecCallback) {
        // Give up decrypt SSL packet.
        if (engine == null) {
            callback.onProcess(input)
            return
        }
        startDecode(engine, input, callback)
    }

    @Throws(IOException::class)
    private fun startDecode(engine: SSLEngine, input: ByteBuffer?, callback: CodecCallback) {
        if (mEngineClosed) {
            return
        }
        if (mHandshakeFinished) {
            val isRenegotiation = input!![input.position()].toInt() ==
                    SSLUtils.SSL_CONTENT_TYPE_HANDSHAKE
            unwrap(engine, input, callback)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                // Fixed a SSL renegotiation issue for API 27+
                // However below API 27, system SSLEngine doesn't support renegotiation.
                if (isRenegotiation) {
                    handshakeWrap(engine, callback)
                }
            }
        } else {
            handshake(engine, input, callback)
        }
        // Start wrap plaintext to engine if possible.
        if (mHandshakeFinished && !mPlaintextBuffers.isEmpty()) {
            var plaintextBuffer: ByteBuffer?
            while (!mPlaintextBuffers.isEmpty()) {
                plaintextBuffer = mPlaintextBuffers.poll()
                if (plaintextBuffer != null && plaintextBuffer.hasRemaining()) {
                    wrap(engine, plaintextBuffer, callback)
                }
            }
        }
    }

    /* package */
    @Throws(IOException::class)
    fun handshake(engine: SSLEngine, input: ByteBuffer?, callback: CodecCallback) {
        if (!mHandshakeStarted) {
            engine.beginHandshake()
            mHandshakeStarted = true
        }
        var status = engine.handshakeStatus
        while (!mHandshakeFinished) {
            if (mEngineClosed) {
                throw IOException("Handshake failed: Engine is closed.")
            }
            if (status == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                // Should never happen
                throw IOException("Handshake failed: Invalid handshake status: $status")
            } else if (status == SSLEngineResult.HandshakeStatus.FINISHED) {
                mHandshakeFinished = true
                NetBareLog.i("SSL handshake finished!")
                if (input!!.hasRemaining()) {
                    decode(engine, input, callback)
                }
            } else if (status == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                status = handshakeWrap(engine, callback).handshakeStatus
            } else if (status == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
                // Wait next encrypted buffer.
                if (!input!!.hasRemaining()) {
                    break
                }
                status = handshakeUnwrap(engine, input, callback).handshakeStatus
            } else if (status == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                runDelegatedTasks(engine)
            }
        }
    }

    @Throws(IOException::class)
    private fun handshakeWrap(engine: SSLEngine, callback: CodecCallback): SSLEngineResult {
        var result: SSLEngineResult
        var status: SSLEngineResult.Status
        var output = allocate()
        while (true) {
            result = engineWrap(engine, allocate(0), output)
            status = result.status
            output.flip()
            if (output.hasRemaining()) {
                callback.onEncrypt(output)
            }
            if (status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                output = allocate(engine.session.applicationBufferSize)
            } else {
                if (status == SSLEngineResult.Status.CLOSED) {
                    mEngineClosed = true
                }
                break
            }
        }
        return result
    }

    @Throws(IOException::class)
    private fun handshakeUnwrap(
        engine: SSLEngine, input: ByteBuffer?,
        callback: CodecCallback
    ): SSLEngineResult {
        var result: SSLEngineResult
        var status: SSLEngineResult.Status
        var output = allocate()
        while (true) {
            result = engineUnwrap(engine, input, output)
            status = result.status
            output.flip()
            val producedSize = output.remaining()
            if (producedSize > 0) {
                callback.onDecrypt(output)
            }
            if (status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                var bufferSize = engine.session.applicationBufferSize - producedSize
                if (bufferSize < 0) {
                    bufferSize = engine.session.applicationBufferSize
                }
                output = allocate(bufferSize)
            } else if (status == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                // Store the remaining packet and wait next encrypted buffer.
                if (input!!.hasRemaining()) {
                    callback.onPending(
                        ByteBuffer.wrap(
                            input.array(), input.position(),
                            input.remaining()
                        )
                    )
                    // Clear all data.
                    input.position(0)
                    input.limit(0)
                }
                break
            } else if (status == SSLEngineResult.Status.CLOSED) {
                mEngineClosed = true
                break
            } else {
                // It is status OK.
                break
            }
        }
        return result
    }

    @Throws(IOException::class)
    private fun unwrap(engine: SSLEngine, input: ByteBuffer?, callback: CodecCallback) {
        var output: ByteBuffer? = null
        while (true) {
            if (output == null) {
                output = allocate()
            }
            val result = engineUnwrap(engine, input, output)
            val status = result.status
            output.flip()
            val producedSize = output.remaining()
            if (producedSize > 0) {
                callback.onDecrypt(output)
                output = null
            }
            if (status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                var bufferSize = engine.session.applicationBufferSize - producedSize
                if (bufferSize < 0) {
                    bufferSize = engine.session.applicationBufferSize
                }
                output = allocate(bufferSize)
            } else if (status == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                // Store the remaining packet and wait next encrypted buffer.
                if (input!!.hasRemaining()) {
                    callback.onPending(
                        ByteBuffer.wrap(
                            input.array(), input.position(),
                            input.remaining()
                        )
                    )
                    // Clear all data.
                    input.position(0)
                    input.limit(0)
                }
                break
            } else if (status == SSLEngineResult.Status.CLOSED) {
                mEngineClosed = true
                break
            } else {
                if (!input!!.hasRemaining()) {
                    break
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun wrap(engine: SSLEngine?, input: ByteBuffer, callback: CodecCallback) {
        var output = allocate()
        while (true) {
            val result = engineWrap(engine, input, output)
            val status = result.status
            output.flip()
            if (output.hasRemaining()) {
                callback.onEncrypt(output)
            }
            if (status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                output = allocate(engine!!.session.applicationBufferSize)
            } else {
                if (status == SSLEngineResult.Status.CLOSED) {
                    mEngineClosed = true
                }
                break
            }
        }
        if (!mEngineClosed && input.hasRemaining()) {
            wrap(engine, input, callback)
        }
    }

    @Throws(SSLException::class)
    private fun engineWrap(
        engine: SSLEngine?,
        input: ByteBuffer,
        output: ByteBuffer
    ): SSLEngineResult {
        return engine!!.wrap(input, output)
    }

    @Throws(SSLException::class)
    private fun engineUnwrap(
        engine: SSLEngine,
        input: ByteBuffer?,
        output: ByteBuffer?
    ): SSLEngineResult {
        var output = output
        val position = input!!.position()
        var result: SSLEngineResult
        // Fixed issue #4
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) {
            // In Android 8.1, the BUFFER_OVERFLOW in the unwrap method will throw an
            // SSLException-EOFException. We catch this error and increase the output buffer
            // capacity.
            while (true) {
                val inputRemaining = input.remaining()
                try {
                    result = engine.unwrap(input, output)
                    break
                } catch (e: SSLException) {
                    if (!output!!.hasRemaining()) {
                        // Copy
                        val outputTemp = ByteBuffer.allocate(output.capacity() * 2)
                        output.flip()
                        outputTemp.put(output)
                        output = outputTemp
                    } else {
                        // java.io.EOFException: Read error is an Android 8.1 system bug,
                        // it will cause #4 and #11. We swallowed this exception and not throw.
                        if (e.cause is EOFException && inputRemaining == 31 && input.remaining() == 0 && output.remaining() == output.capacity()) {
                            // Create a new SSLEngineResult.
                            result = SSLEngineResult(
                                SSLEngineResult.Status.OK,
                                SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING,
                                inputRemaining, 0
                            )
                            break
                        } else {
                            throw e
                        }
                    }
                }
            }
        } else {
            result = if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
                // Compat an Android 5.0 crash when calling
                // 'com.android.org.conscrypt.OpenSSLBIOSink.available()'
                try {
                    engine.unwrap(input, output)
                } catch (e: NullPointerException) {
                    throw SSLException(e)
                }
            } else {
                engine.unwrap(input, output)
            }
        }

        // This is a workaround for a bug in Android 5.0. Android 5.0 does not correctly update
        // the SSLEngineResult.bytesConsumed() in some cases and just return 0.
        //
        // See:
        //     - https://android-review.googlesource.com/c/platform/external/conscrypt/+/122080
        //     - https://github.com/netty/netty/issues/7758
        if (result.bytesConsumed() == 0) {
            val consumed = input.position() - position
            if (consumed != result.bytesConsumed()) {
                // Create a new SSLEngineResult with the correct bytesConsumed().
                result = SSLEngineResult(
                    result.status, result.handshakeStatus,
                    consumed, result.bytesProduced()
                )
            }
        }
        return result
    }

    private fun runDelegatedTasks(engine: SSLEngine) {
        while (true) {
            val task = engine.delegatedTask ?: break
            task.run()
        }
    }

    private fun allocate(size: Int): ByteBuffer {
        return ByteBuffer.allocate(size)
    }

    private fun allocate(): ByteBuffer {
        return ByteBuffer.allocate(DEFAULT_BUFFER_SIZE)
    }

    /**
     * A callback to receive [SSLCodec] results.
     */
    interface CodecCallback {
        /**
         * The packet is not a perfect SSL encrypted packet, should wait next packet and decode
         * together.
         *
         * @param buffer The buffer should be pended in a queue.
         */
        fun onPending(buffer: ByteBuffer?)

        /**
         * The packet is unable to decrypt or encrypt, should send them to tunnel immediately.
         *
         * @param buffer Packets should send to tunnel.
         * @throws IOException If an I/O error has occurred.
         */
        @Throws(IOException::class)
        fun onProcess(buffer: ByteBuffer?)

        /**
         * Output an encrypted packet.
         *
         * @param buffer The encrypted packet.
         * @throws IOException If an I/O error has occurred.
         */
        @Throws(IOException::class)
        fun onEncrypt(buffer: ByteBuffer?)

        /**
         * Output an decrypted packet, it is a plaintext.
         *
         * @param buffer The decrypted packet.
         * @throws IOException If an I/O error has occurred.
         */
        @Throws(IOException::class)
        fun onDecrypt(buffer: ByteBuffer?)
    }

    companion object {
        /**
         * Change cipher spec.
         */
        const val SSL_CONTENT_TYPE_CHANGE_CIPHER_SPEC = SSLUtils.SSL_CONTENT_TYPE_CHANGE_CIPHER_SPEC

        /**
         * Alert.
         */
        const val SSL_CONTENT_TYPE_ALERT = SSLUtils.SSL_CONTENT_TYPE_ALERT

        /**
         * Handshake.
         */
        const val SSL_CONTENT_TYPE_HANDSHAKE = SSLUtils.SSL_CONTENT_TYPE_HANDSHAKE

        /**
         * Application data.
         */
        const val SSL_CONTENT_TYPE_APPLICATION_DATA = SSLUtils.SSL_CONTENT_TYPE_APPLICATION_DATA

        /**
         * HeartBeat Extension.
         */
        const val SSL_CONTENT_TYPE_EXTENSION_HEARTBEAT =
            SSLUtils.SSL_CONTENT_TYPE_EXTENSION_HEARTBEAT

        /**
         * Should larger than generated pem certificate file size.
         */
        private const val DEFAULT_BUFFER_SIZE = 20 * 1024
    }

    init {
        mPlaintextBuffers = ConcurrentLinkedDeque()
    }
}