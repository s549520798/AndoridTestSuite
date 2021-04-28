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
/*
 * Copyright (C) 2014 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.megatronking.netbare.ws

import com.github.megatronking.netbare.NetBareUtils
import java.io.IOException
import java.io.InputStream
import java.net.ProtocolException
import java.nio.ByteBuffer
import java.util.*

/**
 * A web socket frame reader.
 *
 * @author Megatron King
 * @since 2019/1/18 23:52
 */
class WebSocketReader(
    private val mInput: InputStream,
    private val mClient: Boolean,
    private val mCallback: WebSocketCallback
) {
    private val mMaskKey: ByteArray?
    private var mClosed = false
    private var mOpcode = 0
    private var mFrameLength: Long = 0
    private var mFinalFrame = false
    private var mControlFrame = false
    private val mMessageSegments: MutableList<ByteArray>

    /**
     * Process the next protocol frame.
     */
    @Throws(IOException::class)
    fun processNextFrame() {
        readHeader()
        if (mControlFrame) {
            readControlFrame()
        } else {
            readMessageFrame()
        }
    }

    /**
     * Close the input stream.
     */
    fun close() {
        mClosed = true
        NetBareUtils.closeQuietly(mInput)
    }

    @Throws(IOException::class)
    private fun readHeader() {
        if (mClosed) {
            throw IOException("The stream is closed.")
        }

        // Each frame starts with two bytes of data.
        //
        // 0 1 2 3 4 5 6 7    0 1 2 3 4 5 6 7
        // +-+-+-+-+-------+  +-+-------------+
        // |F|R|R|R| OP    |  |M| LENGTH      |
        // |I|S|S|S| CODE  |  |A|             |
        // |N|V|V|V|       |  |S|             |
        // | |1|2|3|       |  |K|             |
        // +-+-+-+-+-------+  +-+-------------+

        // Read first byte
        val b0 = mInput.read() and 0xff
        mOpcode = b0 and WebSocketProtocol.B0_MASK_OPCODE
        mFinalFrame = b0 and WebSocketProtocol.B0_FLAG_FIN != 0
        mControlFrame = b0 and WebSocketProtocol.OPCODE_FLAG_CONTROL != 0

        // Control frames must be final frames (cannot contain continuations).
        if (mControlFrame && !mFinalFrame) {
            throw ProtocolException("Control frames must be final.")
        }
        val reservedFlag1 = b0 and WebSocketProtocol.B0_FLAG_RSV1 != 0
        val reservedFlag2 = b0 and WebSocketProtocol.B0_FLAG_RSV2 != 0
        val reservedFlag3 = b0 and WebSocketProtocol.B0_FLAG_RSV3 != 0
        if (reservedFlag1 || reservedFlag2 || reservedFlag3) {
            // Reserved flags are for extensions which we currently do not support.
            throw ProtocolException("Reserved flags are unsupported.")
        }
        val b1 = mInput.read() and 0xff
        val isMasked = b1 and WebSocketProtocol.B1_FLAG_MASK != 0
        if (isMasked == mClient) {
            // Masked payloads must be read on the server. Unmasked payloads must be read on the client.
            throw ProtocolException(if (mClient) "Server-sent frames must not be masked." else "Client-sent frames must be masked.")
        }

        // Get frame length, optionally reading from follow-up bytes if indicated by special values.
        mFrameLength = (b1 and WebSocketProtocol.B1_MASK_LENGTH).toLong()
        if (mFrameLength == WebSocketProtocol.PAYLOAD_SHORT.toLong()) {
            mFrameLength = readShort() and 0xffffL // Value is unsigned.
        } else if (mFrameLength == WebSocketProtocol.PAYLOAD_LONG.toLong()) {
            mFrameLength = readLong()
            if (mFrameLength < 0) {
                throw ProtocolException(
                    "Frame length 0x" + java.lang.Long.toHexString(mFrameLength) + " > 0x7FFFFFFFFFFFFFFF"
                )
            }
        }
        if (mControlFrame && mFrameLength > WebSocketProtocol.PAYLOAD_BYTE_MAX) {
            throw ProtocolException(
                "Control frame must be less than " +
                        WebSocketProtocol.PAYLOAD_BYTE_MAX + "B."
            )
        }
        if (isMasked) {
            // Read the masking key as bytes so that they can be used directly for unmasking.
            readFully(mMaskKey)
        }
    }

    @Throws(IOException::class)
    private fun readControlFrame() {
        if (mFrameLength >= Int.MAX_VALUE) {
            throw IOException("Not support a frame length > " + Int.MAX_VALUE)
        }
        val callback = mCallback ?: return
        val byteBuffer: ByteBuffer
        byteBuffer = if (mFrameLength > 0) {
            val frame = ByteArray(mFrameLength.toInt())
            readFully(frame)
            if (!mClient) {
                WebSocketProtocol.toggleMask(frame, mMaskKey)
            }
            ByteBuffer.wrap(frame)
        } else {
            ByteBuffer.allocate(0)
        }
        when (mOpcode) {
            WebSocketProtocol.OPCODE_CONTROL_PING -> callback.onReadPing(readFully(byteBuffer))
            WebSocketProtocol.OPCODE_CONTROL_PONG -> callback.onReadPong(readFully(byteBuffer))
            WebSocketProtocol.OPCODE_CONTROL_CLOSE -> {
                var code = WebSocketProtocol.CLOSE_NO_STATUS_CODE
                var reason = ""
                val bufferSize = byteBuffer.remaining().toLong()
                if (bufferSize == 1L) {
                    throw ProtocolException("Malformed close payload length of 1.")
                } else if (bufferSize != 0L) {
                    code = byteBuffer.short.toInt()
                    reason =
                        String(byteBuffer.array(), byteBuffer.position(), byteBuffer.remaining())
                    val codeExceptionMessage = WebSocketProtocol.closeCodeExceptionMessage(code)
                    if (codeExceptionMessage != null) {
                        throw ProtocolException(codeExceptionMessage)
                    }
                }
                callback.onReadClose(code, reason)
            }
            else -> throw ProtocolException("Unknown control opcode: " + Integer.toHexString(mOpcode))
        }
    }

    @Throws(IOException::class)
    private fun readMessageFrame() {
        val opcode = mOpcode
        if (opcode != WebSocketProtocol.OPCODE_TEXT && opcode != WebSocketProtocol.OPCODE_BINARY) {
            throw ProtocolException("Unknown opcode: " + Integer.toHexString(opcode))
        }
        readMessage()
        val callback = mCallback ?: return
        if (mMessageSegments.isEmpty()) {
            throw ProtocolException("Message frame segment is empty!")
        }
        var total = 0
        for (segment in mMessageSegments) {
            total += segment.size
        }
        val byteBuffer = ByteBuffer.allocate(total)
        for (segment in mMessageSegments) {
            byteBuffer.put(segment)
        }
        byteBuffer.flip()
        mMessageSegments.clear()
        if (opcode == WebSocketProtocol.OPCODE_TEXT) {
            callback.onReadMessage(String(byteBuffer.array()))
        } else {
            callback.onReadMessage(byteBuffer.array())
        }
    }

    @Throws(IOException::class)
    private fun readMessage() {
        while (true) {
            if (mClosed) {
                throw IOException("The stream is closed.")
            }
            if (mFrameLength <= 0) {
                return
            }
            if (mFrameLength >= Int.MAX_VALUE) {
                throw IOException("Not support a frame length > " + Int.MAX_VALUE)
            }
            val frame = ByteArray(mFrameLength.toInt())
            readFully(frame)
            if (!mClient) {
                WebSocketProtocol.toggleMask(frame, mMaskKey)
            }
            mMessageSegments.add(frame)
            if (mFinalFrame) {
                break // We are exhausted and have no continuations.
            }
            readUntilNonControlFrame()
            if (mOpcode != WebSocketProtocol.OPCODE_CONTINUATION) {
                throw ProtocolException(
                    "Expected continuation opcode. Got: " + Integer.toHexString(
                        mOpcode
                    )
                )
            }
        }
    }

    @Throws(IOException::class)
    private fun readUntilNonControlFrame() {
        while (!mClosed) {
            readHeader()
            if (!mControlFrame) {
                break
            }
            readControlFrame()
        }
    }

    @Throws(IOException::class)
    private fun readShort(): Short {
        return (mInput.read() and 0xFF shl 8
                or (mInput.read() and 0xFF)).toShort()
    }

    @Throws(IOException::class)
    private fun readLong(): Long {
        return (mInput.read() and 0xFFL shl 56 or (mInput.read() and 0xFFL shl 48
                ) or (mInput.read() and 0xFFL shl 40
                ) or (mInput.read() and 0xFFL shl 32
                ) or (mInput.read() and 0xFFL shl 24
                ) or (mInput.read() and 0xFFL shl 16
                ) or (mInput.read() and 0xFFL shl 8
                ) or (mInput.read() and 0xFFL)).toLong()
    }

    @Throws(IOException::class)
    private fun readFully(bytes: ByteArray?) {
        for (i in bytes!!.indices) {
            bytes[i] = mInput.read().toByte()
        }
    }

    private fun readFully(byteBuffer: ByteBuffer): ByteArray {
        val data = ByteArray(byteBuffer.remaining())
        byteBuffer[data]
        return data
    }

    init {

        // Masks are only a concern for server writers.
        mMaskKey = if (mClient) null else ByteArray(4)
        mMessageSegments = ArrayList(1)
    }
}