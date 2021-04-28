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

import com.github.megatronking.netbare.NetBareLog
import com.github.megatronking.netbare.ssl.SSLRefluxCallback
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Detect the plaintext packet header to determine is it the HTTP protocol.
 *
 * @author Megatron King
 * @since 2019/1/31 16:13
 */
/* package */
internal class HttpHeaderSniffInterceptor     /* package */(private val mCallback: SSLRefluxCallback<HttpRequest, HttpResponse>) :
    HttpIndexedInterceptor() {
    private var mRealHttpProtocol = false
    @Throws(IOException::class)
    override fun intercept(
        @NonNull chain: HttpRequestChain, @NonNull buffer: ByteBuffer,
        index: Int
    ) {
        if (!buffer.hasRemaining()) {
            return
        }
        if (chain.request().httpProtocol() != null) {
            chain.process(buffer)
            return
        }
        if (index == 0) {
            if (requestHeaderFirstByteNotPassed(buffer[buffer.position()])) {
                mCallback.onRequest(chain.request(), buffer)
                return
            }
            // Sniff request header method
            if (buffer.remaining() >= 7 && requestHeaderMethodNotPassed(buffer)) {
                mCallback.onRequest(chain.request(), buffer)
                return
            }
            mRealHttpProtocol = true
            chain.process(buffer)
        } else {
            if (mRealHttpProtocol) {
                chain.process(buffer)
            } else {
                mCallback.onRequest(chain.request(), buffer)
            }
        }
    }

    @Throws(IOException::class)
    override fun intercept(
        @NonNull chain: HttpResponseChain, @NonNull buffer: ByteBuffer,
        index: Int
    ) {
        if (!buffer.hasRemaining()) {
            return
        }
        if (chain.response().httpProtocol() != null) {
            chain.process(buffer)
            return
        }
        if (index == 0) {
            if (responseHeaderFirstByteNotPassed(buffer[buffer.position()])) {
                mCallback.onResponse(chain.response(), buffer)
                return
            }
            // Sniff response header protocol
            if (buffer.remaining() >= 8 && responseHeaderProtocolNotPassed(buffer)) {
                mCallback.onResponse(chain.response(), buffer)
                return
            }
            mRealHttpProtocol = true
            chain.process(buffer)
        } else {
            if (mRealHttpProtocol) {
                chain.process(buffer)
            } else {
                mCallback.onResponse(chain.response(), buffer)
            }
        }
    }

    private fun requestHeaderFirstByteNotPassed(first: Byte): Boolean {
        when (first) {
            'G', 'H', 'P', 'D', 'O', 'T', 'C' -> return false
            else ->                 // Unknown first byte data.
                NetBareLog.w("Unknown first request header byte : $first")
        }
        return true
    }

    private fun requestHeaderMethodNotPassed(buffer: ByteBuffer): Boolean {
        val headerMethod = String(
            buffer.array(), buffer.position(),
            buffer.position() + 7
        )
        for (method in HttpMethod.values()) {
            if (method == HttpMethod.UNKNOWN) {
                continue
            }
            if (headerMethod.startsWith(method.name)) {
                return false
            }
        }
        NetBareLog.w("Unknown request header method : $headerMethod")
        return true
    }

    private fun responseHeaderFirstByteNotPassed(first: Byte): Boolean {
        when (first) {
            'h', 'H' -> return false
            else ->                 // Unknown first byte data.
                NetBareLog.w("Unknown first response header byte : $first")
        }
        return true
    }

    private fun responseHeaderProtocolNotPassed(buffer: ByteBuffer): Boolean {
        val headerProtocol = String(
            buffer.array(), buffer.position(),
            buffer.position() + 8
        )
        for (protocol in HttpProtocol.values()) {
            if (protocol == HttpProtocol.UNKNOWN || protocol == HttpProtocol.H2_PRIOR_KNOWLEDGE || protocol == HttpProtocol.SPDY_3 || protocol == HttpProtocol.QUIC) {
                continue
            }
            if (headerProtocol.startsWith(protocol.toString())) {
                return false
            }
        }
        NetBareLog.w("Unknown response header protocol : $headerProtocol")
        return true
    }
}