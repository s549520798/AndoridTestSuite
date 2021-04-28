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
package com.github.megatronking.netbare.http2

import com.github.megatronking.netbare.NetBareXLog
import com.github.megatronking.netbare.http.*
import com.github.megatronking.netbare.ssl.SSLRefluxCallback
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.experimental.and

/**
 * Decodes HTTP2 request and response packets.
 *
 * @author Megatron King
 * @since 2019/1/5 14:19
 */
class Http2DecodeInterceptor(
    private val mRefluxCallback: SSLRefluxCallback<HttpRequest, HttpResponse>,
    private val mZygoteRequest: HttpZygoteRequest,
    private val mZygoteResponse: HttpZygoteResponse
) : HttpPendingIndexedInterceptor() {
    private val mHttpIds: MutableMap<Int, HttpId>
    private val mRequestStream: Http2Stream
    private val mResponseStream: Http2Stream
    private var mHpackRequestReader: Hpack.Reader? = null
    private var mHpackResponseReader: Hpack.Reader? = null
    private var mLog: NetBareXLog? = null
    @Throws(IOException::class)
    override fun intercept(
        chain: HttpRequestChain, buffer: ByteBuffer,
        index: Int
    ) {
        if (chain.request().httpProtocol() == HttpProtocol.HTTP_2) {
            if (!buffer.hasRemaining()) {
                return
            }
            if (mLog == null) {
                val request = chain.request()
                mLog = NetBareXLog(request!!.protocol(), request.ip(), request.port())
            }
            if (mHpackRequestReader == null) {
                mHpackRequestReader = Hpack.Reader()
            }
            decode(mergeRequestBuffer(buffer), mHpackRequestReader!!, object : DecodeCallback {
                override fun onPending(buffer: ByteBuffer?) {
                    pendRequestBuffer(buffer!!)
                }

                @Throws(IOException::class)
                override fun onResult(buffer: ByteBuffer, isFinished: Boolean) {
                    val streamId = mRequestStream.id
                    if (streamId < 0) {
                        throw IOException("Http2 stream id is < 0")
                    }
                    var id = mHttpIds[streamId]
                    if (id == null) {
                        id = HttpId(streamId)
                        mHttpIds[streamId] = id
                    }
                    mZygoteRequest.zygote(id)
                    if (isFinished) {
                        mZygoteRequest.onStreamFinished()
                    }
                    if (!buffer.hasRemaining()) {
                        return
                    }
                    chain.process(buffer)
                }

                @Throws(IOException::class)
                override fun onSkip(buffer: ByteBuffer?) {
                    mRefluxCallback.onRequest(chain.request(), buffer)
                }
            }, mRequestStream, object : Http2Updater {
                override fun onSettingsUpdate(http2Settings: Http2Settings) {
                    mZygoteRequest.onSettingsUpdate(http2Settings)
                    if (http2Settings.headerTableSize > 0) {
                        if (mHpackResponseReader == null) {
                            mHpackResponseReader = Hpack.Reader()
                        }
                        mHpackResponseReader!!.setHeaderTableSizeSetting(http2Settings.headerTableSize)
                    }
                }

                override fun onStreamFinished() {
                    mZygoteRequest.onStreamFinished()
                }
            })
        } else {
            chain.process(buffer)
        }
    }

    @Throws(IOException::class)
    override fun intercept(
        chain: HttpResponseChain, buffer: ByteBuffer,
        index: Int
    ) {
        if (chain.response().httpProtocol() == HttpProtocol.HTTP_2) {
            if (!buffer.hasRemaining()) {
                return
            }
            if (mLog == null) {
                val response = chain.response()
                mLog = NetBareXLog(response!!.protocol(), response.ip(), response.port())
            }
            if (mHpackResponseReader == null) {
                mHpackResponseReader = Hpack.Reader()
            }
            decode(mergeResponseBuffer(buffer), mHpackResponseReader!!, object : DecodeCallback {
                override fun onPending(buffer: ByteBuffer?) {
                    pendResponseBuffer(buffer!!)
                }

                @Throws(IOException::class)
                override fun onResult(buffer: ByteBuffer, isFinished: Boolean) {
                    val streamId = mResponseStream.id
                    if (streamId < 0) {
                        throw IOException("Http2 stream id is < 0")
                    }
                    var id = mHttpIds[streamId]
                    if (id == null) {
                        id = HttpId(streamId)
                        mHttpIds[streamId] = id
                    }
                    mZygoteResponse.zygote(id)
                    if (isFinished) {
                        mZygoteResponse.onStreamFinished()
                    }
                    if (!buffer.hasRemaining()) {
                        return
                    }
                    chain.process(buffer)
                }

                @Throws(IOException::class)
                override fun onSkip(buffer: ByteBuffer?) {
                    mRefluxCallback.onResponse(chain.response(), buffer)
                }
            }, mResponseStream, object : Http2Updater {
                override fun onSettingsUpdate(http2Settings: Http2Settings) {
                    mZygoteResponse.onSettingsUpdate(http2Settings)
                    if (http2Settings.headerTableSize > 0) {
                        if (mHpackRequestReader == null) {
                            mHpackRequestReader = Hpack.Reader()
                        }
                        mHpackRequestReader!!.setHeaderTableSizeSetting(http2Settings.headerTableSize)
                    }
                }

                override fun onStreamFinished() {
                    mZygoteResponse.onStreamFinished()
                }
            })
        } else {
            chain.process(buffer)
        }
    }

    @Throws(IOException::class)
    private fun decode(
        buffer: ByteBuffer?, reader: Hpack.Reader, callback: DecodeCallback,
        stream: Http2Stream, updater: Http2Updater
    ) {
        // HTTP2 frame structure
        //  0                   1                   2                   3
        //  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
        // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        // |                 Length (24)                   |
        // +---------------+---------------+---------------+
        // |   Type (8)    |   Flags (8)   |
        // +-+-+-----------+---------------+-------------------------------+
        // |R|                 Stream Identifier (31)                      |
        // +=+=============================================================+
        // |                   Frame Payload (0...)                      ...
        // +---------------------------------------------------------------+
        if (buffer!!.remaining() < Http2.FRAME_HEADER_LENGTH) {
            callback.onPending(buffer)
            return
        }
        val length = readMedium(buffer)
        if (length < 0 || length > Http2.INITIAL_MAX_FRAME_SIZE) {
            // Values greater than 214 (16,384) MUST NOT be sent unless the receiver has set a
            // larger value for SETTINGS_MAX_FRAME_SIZE.
            throw IOException("Http2 frame size error: $length")
        }
        // Check payload length
        if (length + 6 > buffer.remaining()) {
            mLog!!.w(
                "No enough http2 frame length, expect: %d actual: %d", length,
                buffer.remaining() - 6
            )
            // Packet not enough for one frame, wait next packet.
            // Revert position.
            buffer.position(buffer.position() - 3)
            callback.onPending(buffer)
            return
        } else if (length + 6 < buffer.remaining()) {
            mLog!!.w(
                "Multi http2 frames in one buffer, first frame length : %d, buffer length: %d",
                length + 9, buffer.remaining() + 3
            )
            // Separate multi-frames
            val newBuffer = ByteBuffer.allocate(length + 9)
            newBuffer.put(buffer.array(), buffer.position() - 3, newBuffer.capacity())
            newBuffer.flip()
            decode(newBuffer, reader, callback, stream, updater)
            // Process the left data
            buffer.position(buffer.position() + length + 6)
            decode(buffer, reader, callback, stream, updater)
            return
        }
        val type = buffer.get() and 0xff.toByte()
        val flags = buffer.get() and 0xff.toByte()
        val streamId = buffer.int and 0x7fffffff
        val frameType: FrameType? = FrameType.parse(type)
        if (frameType == null) {
            mLog!!.e("Unexpected http2 frame type: $type")
            // Discard frames that have unknown or unsupported types.
            return
        }
        if (stream.id != -1) {
            if (streamId != stream.id && frameType == FrameType.CONTINUATION) {
                throw IOException("Http2 TYPE_CONTINUATION streamId changed!")
            }
        }
        mLog!!.i(
            "Decode a http2 frame: " + frameType + " stream(" + streamId +
                    ") length(" + length + ")"
        )
        stream.id = streamId
        when (frameType) {
            FrameType.DATA -> {
                decodeData(buffer, length, flags, streamId, callback)
                return
            }
            FrameType.HEADERS, FrameType.CONTINUATION -> {
                decodeHeaders(buffer, reader, length, flags, streamId, callback)
                return
            }
            FrameType.SETTINGS -> decodeSettings(buffer, length, flags, streamId, updater)
            FrameType.GOAWAY -> decodeGoAway(buffer, length, flags, streamId)
            else -> {
            }
        }
        // Encrypt and send it to remote server directly.
        buffer.position(buffer.position() - Http2.FRAME_HEADER_LENGTH)
        callback.onSkip(buffer)
    }

    private fun readMedium(buffer: ByteBuffer?): Int {
        return buffer!!.get() and 0xff.toByte() shl 16 or (buffer.get() and 0xff.toByte() shl 8
                ) or (buffer.get() and 0xff.toByte())
    }

    @Throws(IOException::class)
    private fun decodeSettings(
        buffer: ByteBuffer?, length: Int, flags: Byte, streamId: Int,
        receiver: Http2Updater
    ) {
        if (streamId != 0) {
            throw IOException("Http2 TYPE_SETTINGS streamId != 0")
        }
        if (flags and Http2.FLAG_ACK != 0.toByte()) {
            if (length != 0) {
                throw IOException("Http2 FRAME_SIZE_ERROR ack frame should be empty!")
            }
            mLog!!.i("Http2 ack the settings")
            return
        }
        if (length % 6 != 0) {
            throw IOException("Http2 TYPE_SETTINGS length %% 6 != 0: $length")
        }
        val initPosition = buffer!!.position()
        val settings = Http2Settings()
        var i = 0
        while (i < length) {
            var id: Int = buffer.short.toInt() and 0xFFFF
            val value = buffer.int
            when (id) {
                1 -> mLog!!.i("Http2 SETTINGS_HEADER_TABLE_SIZE: $value")
                2 -> if (value != 0 && value != 1) {
                    throw IOException("Http2 PROTOCOL_ERROR SETTINGS_ENABLE_PUSH != 0 or 1")
                }
                3 -> {
                    id = 4 // Renumbered in draft 10.
                    mLog!!.i("Http2 SETTINGS_MAX_CONCURRENT_STREAMS: $value")
                }
                4 -> {
                    id = 7 // Renumbered in draft 10.
                    if (value < 0) {
                        throw IOException("Http2 PROTOCOL_ERROR SETTINGS_INITIAL_WINDOW_SIZE > 2^31 - 1")
                    }
                    mLog!!.i("Http2 SETTINGS_INITIAL_WINDOW_SIZE: $value")
                }
                5 -> {
                    if (value < Http2.INITIAL_MAX_FRAME_SIZE || value > 16777215) {
                        throw IOException("Http2 PROTOCOL_ERROR SETTINGS_MAX_FRAME_SIZE: $value")
                    }
                    mLog!!.i("Http2 INITIAL_MAX_FRAME_SIZE: $value")
                }
                6 -> {
                }
                else -> {
                }
            }
            settings[id] = value
            i += 6
        }
        // Reverse the position and sent to terminal.
        buffer.position(initPosition)
        receiver.onSettingsUpdate(settings)
    }

    @Throws(IOException::class)
    private fun decodeHeaders(
        buffer: ByteBuffer?, reader: Hpack.Reader, length: Int, flags: Byte,
        streamId: Int, callback: DecodeCallback
    ) {
        // +---------------+
        // |Pad Length? (8)|
        // +-+-------------+-----------------------------------------------+
        // |E|                 Stream Dependency? (31)                     |
        // +-+-------------+-----------------------------------------------+
        // |  Weight? (8)  |
        // +-+-------------+-----------------------------------------------+
        // |                   Header Block Fragment (*)                 ...
        // +---------------------------------------------------------------+
        // |                           Padding (*)                       ...
        // +---------------------------------------------------------------+
        var length = length
        if (streamId == 0) {
            throw IOException("Http2 PROTOCOL_ERROR: TYPE_HEADERS streamId == 0")
        }
        val padding =
            if (flags and Http2.FLAG_PADDED != 0) (buffer!!.get() and 0xff) as Short else 0
        if (flags and Http2.FLAG_PRIORITY != 0) {
            // Skip priority.
            buffer!!.position(buffer.position() + 5)
        }
        length = lengthWithoutPadding(length, flags, padding)
        val endStream = flags and Http2.FLAG_END_STREAM != 0
        if (length > 0) {
            decodeHeaderBlock(buffer, reader, flags, callback)
        } else {
            // Notify stream is end
            callback.onResult(ByteBuffer.allocate(0), endStream)
            if (endStream) {
                callback.onSkip(endStream(FrameType.HEADERS, streamId))
            }
        }
    }

    @Throws(IOException::class)
    private fun decodeHeaderBlock(
        buffer: ByteBuffer?, reader: Hpack.Reader, flags: Byte,
        callback: DecodeCallback
    ) {
        try {
            reader.readHeaders(buffer, flags, callback)
        } catch (e: IndexOutOfBoundsException) {
            throw IOException("Http2 decode header block failed.")
        }
    }

    @Throws(IOException::class)
    private fun decodeData(
        buffer: ByteBuffer?, length: Int, flags: Byte, streamId: Int,
        callback: DecodeCallback
    ) {
        var length = length
        if (streamId == 0) {
            throw IOException("Http2 PROTOCOL_ERROR: TYPE_DATA streamId == 0")
        }
        val gzipped = flags and Http2.FLAG_COMPRESSED != 0
        if (gzipped) {
            throw IOException("Http2 PROTOCOL_ERROR: FLAG_COMPRESSED without SETTINGS_COMPRESS_DATA")
        }
        val padding =
            if (flags and Http2.FLAG_PADDED != 0) (buffer!!.get() and 0xff) as Short else 0
        length = lengthWithoutPadding(length, flags, padding)
        val endStream = flags and Http2.FLAG_END_STREAM != 0
        if (length > 0) {
            callback.onResult(
                ByteBuffer.wrap(
                    Arrays.copyOfRange(
                        buffer!!.array(), buffer.position(),
                        buffer.position() + length
                    )
                ), endStream
            )
        } else {
            // Notify stream is end
            callback.onResult(ByteBuffer.allocate(0), endStream)
            if (endStream) {
                callback.onSkip(endStream(FrameType.DATA, streamId))
            }
        }
    }

    @Throws(IOException::class)
    private fun decodeGoAway(buffer: ByteBuffer?, length: Int, flags: Byte, streamId: Int) {
        if (length < 8) {
            throw IOException("Http2 TYPE_GOAWAY length < 8: $length")
        }
        if (streamId != 0) {
            throw IOException("Http2 TYPE_GOAWAY streamId != 0")
        }
        val initPosition = buffer!!.position()
        val lastStreamId = buffer.int
        val errorCodeInt = buffer.int
        val opaqueDataLength = length - 8
        val errorCode: ErrorCode = ErrorCode.Companion.fromHttp2(errorCodeInt)
            ?: throw IOException("Http2 TYPE_GOAWAY unexpected error code: $errorCodeInt")
        mLog!!.e("Http2 TYPE_GOAWAY error code: $errorCode last stream: $lastStreamId")
        if (opaqueDataLength > 0) { // Must read debug data in order to not corrupt the connection.
            val debugData = ByteArray(opaqueDataLength)
            buffer[debugData]
            mLog!!.e("Http2 TYPE_GOAWAY debug data: " + String(debugData))
        }
        buffer.position(initPosition)
    }

    @Throws(IOException::class)
    private fun lengthWithoutPadding(length: Int, flags: Byte, padding: Short): Int {
        var length = length
        if (flags and Http2.FLAG_PADDED != 0) {
            length-- // Account for reading the padding length.
        }
        if (padding > length) {
            throw IOException("Http2 PROTOCOL_ERROR padding $padding > remaining length $length")
        }
        return (length - padding).toShort()
    }

    private fun endStream(frameType: FrameType, streamId: Int): ByteBuffer {
        val endBuffer = ByteBuffer.allocate(Http2.FRAME_HEADER_LENGTH)
        endBuffer.put(0.toByte())
        endBuffer.put(0.toByte())
        endBuffer.put(0.toByte())
        endBuffer.put((frameType.get() and 0xff) as Byte)
        endBuffer.put((Http2.FLAG_END_STREAM and 0xff) as Byte)
        endBuffer.putInt(streamId and 0x7fffffff)
        endBuffer.flip()
        return endBuffer
    }

    init {
        mHttpIds = ConcurrentHashMap()
        mRequestStream = Http2Stream()
        mResponseStream = Http2Stream()
    }
}