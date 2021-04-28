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
import com.github.megatronking.netbare.gateway.InterceptorChain
import com.github.megatronking.netbare.http.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Encodes HTTP2 request and response packets.
 *
 * @author Megatron King
 * @since 2019/1/5 14:24
 */
class Http2EncodeInterceptor : HttpInterceptor {
    private val mStreamRequestIndexes: MutableMap<Int, Int>
    private val mStreamResponseIndexes: MutableMap<Int, Int>
    private var mLog: NetBareXLog? = null
    private var mHpackRequestWriter: Hpack.Writer? = null
    private var mHpackResponseWriter: Hpack.Writer? = null
    @Throws(IOException::class)
    override fun intercept(chain: HttpRequestChain, buffer: ByteBuffer) {
        if (chain.request().httpProtocol() == HttpProtocol.HTTP_2) {
            if (mLog == null) {
                val request = chain.request()
                mLog = NetBareXLog(request!!.protocol(), request.ip(), request.port())
            }
            if (mHpackRequestWriter == null) {
                mHpackRequestWriter = Hpack.Writer()
            }
            val index: Int
            val streamId = chain.request().streamId()
            val requestIndex = mStreamRequestIndexes[streamId]
            index = if (requestIndex != null) {
                requestIndex + 1
            } else {
                0
            }
            mStreamRequestIndexes[streamId] = index
            if (index == 0) {
                encodeRequestHeader(chain)
            } else {
                encodeRequestData(chain, buffer)
            }
        } else {
            chain.process(buffer)
        }
    }

    @Throws(IOException::class)
    override fun intercept(@NonNull chain: HttpResponseChain, @NonNull buffer: ByteBuffer) {
        if (chain.response().httpProtocol() == HttpProtocol.HTTP_2) {
            if (mLog == null) {
                val response = chain.response()
                mLog = NetBareXLog(response!!.protocol(), response.ip(), response.port())
            }
            if (mHpackResponseWriter == null) {
                mHpackResponseWriter = Hpack.Writer()
            }
            val index: Int
            val streamId = chain.response().streamId()
            val responseIndex = mStreamResponseIndexes[streamId]
            index = if (responseIndex != null) {
                responseIndex + 1
            } else {
                0
            }
            mStreamResponseIndexes[streamId] = index
            if (index == 0) {
                encodeResponseHeader(chain)
            } else {
                encodeResponseData(chain, buffer)
            }
        } else {
            chain.process(buffer)
        }
    }

    override fun onRequestFinished(@NonNull request: HttpRequest) {}
    override fun onResponseFinished(@NonNull response: HttpResponse) {}
    @Throws(IOException::class)
    private fun encodeRequestHeader(chain: HttpRequestChain) {
        val request = chain.request()
        val peerHttp2Settings = request!!.peerHttp2Settings()
        if (peerHttp2Settings != null) {
            val headerTableSize = peerHttp2Settings.headerTableSize
            if (headerTableSize != -1) {
                mHpackRequestWriter!!.setHeaderTableSizeSetting(headerTableSize)
            }
        }
        val headerBlock = mHpackRequestWriter!!.writeRequestHeaders(
            request.method(),
            request.path(), request.host(), request.requestHeaders()
        )
        sendHeaderBlockFrame(
            chain, headerBlock, peerHttp2Settings, request.streamId(),
            request.requestStreamEnd()
        )
    }

    @Throws(IOException::class)
    private fun encodeResponseHeader(chain: HttpResponseChain) {
        val response = chain.response()
        val clientHttp2Settings = response!!.clientHttp2Settings()
        if (clientHttp2Settings != null) {
            val headerTableSize = clientHttp2Settings.headerTableSize
            if (headerTableSize != -1) {
                mHpackResponseWriter!!.setHeaderTableSizeSetting(headerTableSize)
            }
        }
        val headerBlock = mHpackResponseWriter!!.writeResponseHeaders(
            response.code(),
            response.message(), response.responseHeaders()
        )
        sendHeaderBlockFrame(
            chain, headerBlock, clientHttp2Settings, response.streamId(),
            response.responseStreamEnd()
        )
    }

    @Throws(IOException::class)
    private fun encodeRequestData(chain: HttpRequestChain, buffer: ByteBuffer) {
        val data = Arrays.copyOfRange(buffer.array(), buffer.position(), buffer.limit())
        val request = chain.request()
        sendDataFrame(
            chain, data, request!!.peerHttp2Settings(), request.streamId(),
            request.requestStreamEnd()
        )
    }

    @Throws(IOException::class)
    private fun encodeResponseData(chain: HttpResponseChain, buffer: ByteBuffer) {
        val data = Arrays.copyOfRange(buffer.array(), buffer.position(), buffer.limit())
        val response = chain.response()
        sendDataFrame(
            chain, data, response!!.clientHttp2Settings(), response.streamId(),
            response.responseStreamEnd()
        )
    }

    @Throws(IOException::class)
    private fun sendHeaderBlockFrame(
        chain: InterceptorChain<*, *>, headerBlock: ByteArray?, http2Settings: Http2Settings?,
        streamId: Int, endStream: Boolean
    ) {
        val maxFrameSize = http2Settings?.getMaxFrameSize(Http2.INITIAL_MAX_FRAME_SIZE)
            ?: Http2.INITIAL_MAX_FRAME_SIZE
        val byteCount = headerBlock!!.size
        val length = Math.min(maxFrameSize, byteCount)
        val type = FrameType.HEADERS.get()
        var flags: Byte = 0
        if (byteCount == length) {
            flags = flags or Http2.FLAG_END_HEADERS
            if (endStream) {
                flags = flags or Http2.FLAG_END_STREAM
            }
        }
        val os = ByteArrayOutputStream()
        os.write(frameHeader(streamId, length, type, flags))
        os.write(headerBlock, 0, length)
        chain.process(ByteBuffer.wrap(os.toByteArray()))
        if (byteCount > length) {
            val left = Arrays.copyOfRange(headerBlock, length, byteCount)
            sendContinuationFrame(
                chain, left, streamId, maxFrameSize, (byteCount - length).toLong(),
                endStream
            )
        }
    }

    @Throws(IOException::class)
    private fun sendContinuationFrame(
        chain: InterceptorChain<*, *>, headerBlock: ByteArray, streamId: Int,
        maxFrameSize: Int, byteCount: Long, endStream: Boolean
    ) {
        var byteCount = byteCount
        var offset = 0
        while (byteCount > 0) {
            val length = Math.min(maxFrameSize.toLong(), byteCount).toInt()
            byteCount -= length.toLong()
            val os = ByteArrayOutputStream()
            var flags: Byte = 0
            if (byteCount == 0L) {
                flags = flags or Http2.FLAG_END_HEADERS
                if (endStream) {
                    mLog!!.i("Http2 stream end: $streamId")
                    flags = flags or Http2.FLAG_END_STREAM
                }
            }
            os.write(frameHeader(streamId, length, FrameType.CONTINUATION.get(), flags))
            os.write(headerBlock, offset, length)
            offset += length
            chain.process(ByteBuffer.wrap(os.toByteArray()))
        }
    }

    @Throws(IOException::class)
    private fun sendDataFrame(
        chain: InterceptorChain<*, *>, data: ByteArray, http2Settings: Http2Settings?,
        streamId: Int, endStream: Boolean
    ) {
        val maxFrameSize = http2Settings?.getMaxFrameSize(Http2.INITIAL_MAX_FRAME_SIZE)
            ?: Http2.INITIAL_MAX_FRAME_SIZE
        var byteCount = data.size
        val type = FrameType.DATA.get()
        var offset = 0
        while (byteCount > 0) {
            val length = Math.min(maxFrameSize, byteCount)
            byteCount -= length
            val os = ByteArrayOutputStream()
            var flags: Byte = 0
            if (byteCount == 0 && endStream) {
                mLog!!.i("Http2 stream end: $streamId")
                flags = flags or Http2.FLAG_END_STREAM
            }
            os.write(frameHeader(streamId, length, type, flags))
            os.write(data, offset, length)
            offset += length
            chain.process(ByteBuffer.wrap(os.toByteArray()))
        }
    }

    private fun frameHeader(streamId: Int, length: Int, type: Byte, flags: Byte): ByteArray {
        mLog!!.i(
            "Encode a http2 frame: " + FrameType.Companion.parse(type) + " stream(" + streamId +
                    ") length(" + length + ")"
        )
        val header = ByteBuffer.allocate(Http2.FRAME_HEADER_LENGTH)
        header.put((length ushr 16 and 0xff).toByte())
        header.put((length ushr 8 and 0xff).toByte())
        header.put((length and 0xff).toByte())
        header.put((type and 0xff) as Byte)
        header.put((flags and 0xff) as Byte)
        header.putInt(streamId and 0x7fffffff)
        return header.array()
    }

    init {
        mStreamRequestIndexes = ConcurrentHashMap()
        mStreamResponseIndexes = ConcurrentHashMap()
    }
}