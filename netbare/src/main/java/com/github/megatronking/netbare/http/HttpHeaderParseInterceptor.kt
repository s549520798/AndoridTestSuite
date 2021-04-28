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
package com.github.megatronking.netbare.http

import com.github.megatronking.netbare.NetBareUtils
import com.github.megatronking.netbare.NetBareXLog
import com.github.megatronking.netbare.ip.*
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*

/**
 * Parse HTTP request header part and response header part from HTTP packets. The parse result will
 * be set to [HttpSession].
 *
 * @author Megatron King
 * @since 2018-12-09 12:19
 */
/* package */
internal class HttpHeaderParseInterceptor : HttpIndexedInterceptor() {
    private var mLog: NetBareXLog? = null
    @Throws(IOException::class)
    override fun intercept(
        @NonNull chain: HttpRequestChain, @NonNull buffer: ByteBuffer,
        index: Int
    ) {
        if (index > 0) {
            chain.process(buffer)
            return
        }
        if (mLog == null) {
            mLog = NetBareXLog(Protocol.TCP, chain.request().ip(), chain.request().port())
        }
        parseRequestHeader(chain.request().session(), buffer)
        chain.process(buffer)
    }

    @Throws(IOException::class)
    override fun intercept(
        @NonNull chain: HttpResponseChain, @NonNull buffer: ByteBuffer,
        index: Int
    ) {
        if (index > 0) {
            chain.process(buffer)
            return
        }
        if (mLog == null) {
            mLog = NetBareXLog(Protocol.TCP, chain.response().ip(), chain.response().port())
        }
        parseResponseHeader(chain.response().session(), buffer)
        chain.process(buffer)
    }

    private fun parseRequestHeader(session: HttpSession?, buffer: ByteBuffer) {
        session!!.reqBodyOffset = buffer.remaining()
        val headerString = String(buffer.array(), buffer.position(), buffer.remaining())
        val headers = headerString.split(NetBareUtils.LINE_END_REGEX).toTypedArray()
        val requestLine = headers[0].split(" ").toTypedArray()
        if (requestLine.size < 3) {
            mLog!!.w("Unexpected http request line: " + headers[0])
            return
        }
        // Method
        val method: HttpMethod = HttpMethod.Companion.parse(requestLine[0])
        if (method == HttpMethod.UNKNOWN) {
            mLog!!.w("Unknown http request method: " + requestLine[0])
            return
        }
        session.method = method
        // Http Protocol
        val protocol: HttpProtocol = HttpProtocol.Companion.parse(requestLine[requestLine.size - 1])
        if (protocol == HttpProtocol.UNKNOWN) {
            mLog!!.w("Unknown http request protocol: " + requestLine[requestLine.size - 1])
            return
        }
        session.protocol = protocol

        // Path
        session.path = headers[0].replace(requestLine[0], "")
            .replace(requestLine[requestLine.size - 1], "")
            .trim { it <= ' ' }

        // Http request headers
        if (headers.size <= 1) {
            mLog!!.w("Unexpected http request headers.")
            return
        }
        for (i in 1 until headers.size) {
            val requestHeader = headers[i]
            // Reach the header end
            if (requestHeader.isEmpty()) {
                continue
            }
            val nameValue = requestHeader.split(":").toTypedArray()
            if (nameValue.size < 2) {
                mLog!!.w("Unexpected http request header: $requestHeader")
                continue
            }
            val name = nameValue[0].trim { it <= ' ' }
            val value =
                requestHeader.replaceFirst(nameValue[0] + ": ".toRegex(), "").trim { it <= ' ' }
            var header = session.requestHeaders[name]
            if (header == null) {
                header = ArrayList(1)
                session.requestHeaders[name] = header
            }
            header.add(value)
        }
    }

    private fun parseResponseHeader(session: HttpSession?, buffer: ByteBuffer) {
        session!!.resBodyOffset = buffer.remaining()
        val headerString = String(buffer.array(), buffer.position(), buffer.remaining())
        // In some condition, no request but has response, we set the method to unknown.
        if (session.method == null) {
            session.method = HttpMethod.UNKNOWN
        }
        val headers = headerString.split(NetBareUtils.LINE_END_REGEX).toTypedArray()
        val responseLine = headers[0].split(" ").toTypedArray()
        if (responseLine.size < 2) {
            mLog!!.w("Unexpected http response line: " + headers[0])
            return
        }
        // Http Protocol
        val protocol: HttpProtocol = HttpProtocol.Companion.parse(responseLine[0])
        if (protocol == HttpProtocol.UNKNOWN) {
            mLog!!.w("Unknown http response protocol: " + responseLine[0])
            return
        }
        if (session.protocol != protocol) {
            // Protocol downgrade
            if (session.protocol != null) {
                mLog!!.w(
                    "Unmatched http protocol, request is " + session.protocol +
                            " but response is " + responseLine[0]
                )
            }
            session.protocol = protocol
        }
        // Code
        val code = NetBareUtils.parseInt(responseLine[1], -1)
        if (code == -1) {
            mLog!!.w("Unexpected http response code: " + responseLine[1])
            return
        }
        session.code = code
        // Message
        session.message = headers[0].replaceFirst(responseLine[0].toRegex(), "")
            .replaceFirst(responseLine[1].toRegex(), "").trim { it <= ' ' }

        // Http response headers
        if (headers.size <= 1) {
            mLog!!.w("Unexpected http response headers.")
            return
        }
        for (i in 1 until headers.size) {
            val responseHeader = headers[i]
            // Reach the header end
            if (responseHeader.isEmpty()) {
                continue
            }
            val nameValue = responseHeader.split(":").toTypedArray()
            if (nameValue.size < 2) {
                mLog!!.w("Unexpected http response header: $responseHeader")
                continue
            }
            val name = nameValue[0].trim { it <= ' ' }
            val value =
                responseHeader.replaceFirst(nameValue[0] + ": ".toRegex(), "").trim { it <= ' ' }
            var header = session.responseHeaders[name]
            if (header == null) {
                header = ArrayList(1)
                session.responseHeaders[name] = header
            }
            header.add(value)
        }
    }
}