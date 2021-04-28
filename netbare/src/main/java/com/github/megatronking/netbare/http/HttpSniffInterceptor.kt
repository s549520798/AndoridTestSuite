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
import com.github.megatronking.netbare.ssl.SSLCodec
import com.github.megatronking.netbare.ssl.SSLWhiteList
import java.io.IOException
import java.nio.ByteBuffer

/**
 * A fronted interceptor verifies the first net packet in order to determine whether it is a HTTP
 * protocol packet. If the packet is not a valid HTTP packet, it will be sent to tunnel directly,
 * otherwise sent to the next interceptor.
 *
 * @author Megatron King
 * @since 2018-12-04 11:58
 */
/* package */
internal class HttpSniffInterceptor     /* package */(private val mSession: HttpSession?) :
    HttpIndexedInterceptor() {
    private var mType = 0
    @Throws(IOException::class)
    override fun intercept(
        @NonNull chain: HttpRequestChain, @NonNull buffer: ByteBuffer,
        index: Int
    ) {
        if (index == 0) {
            if (SSLWhiteList.contains(chain.request().ip())) {
                mType = TYPE_WHITELIST
                NetBareLog.i("detect whitelist ip " + chain.request().ip())
            } else {
                mType = if (chain.request().host() == null) TYPE_INVALID else verifyHttpType(buffer)
            }
        }
        if (mType == TYPE_HTTPS) {
            mSession!!.isHttps = true
        }
        if (mType == TYPE_INVALID || mType == TYPE_WHITELIST) {
            chain.processFinal(buffer)
            return
        }
        chain.process(buffer)
    }

    @Throws(IOException::class)
    override fun intercept(
        @NonNull chain: HttpResponseChain, @NonNull buffer: ByteBuffer,
        index: Int
    ) {
        if (mType == TYPE_INVALID || mType == TYPE_WHITELIST) {
            chain.processFinal(buffer)
            return
        }
        chain.process(buffer)
    }

    private fun verifyHttpType(buffer: ByteBuffer): Int {
        if (!buffer.hasRemaining()) {
            return TYPE_INVALID
        }
        val first = buffer[buffer.position()]
        when (first) {
            'G', 'H', 'P', 'D', 'O', 'T', 'C' -> return TYPE_HTTP
            SSLCodec.Companion.SSL_CONTENT_TYPE_ALERT, SSLCodec.Companion.SSL_CONTENT_TYPE_APPLICATION_DATA, SSLCodec.Companion.SSL_CONTENT_TYPE_CHANGE_CIPHER_SPEC, SSLCodec.Companion.SSL_CONTENT_TYPE_EXTENSION_HEARTBEAT, SSLCodec.Companion.SSL_CONTENT_TYPE_HANDSHAKE -> return TYPE_HTTPS
            else ->                 // Unknown first byte data.
                NetBareLog.e("Unknown first request byte : $first")
        }
        return TYPE_INVALID
    }

    companion object {
        private const val TYPE_HTTP = 1
        private const val TYPE_HTTPS = 2
        private const val TYPE_INVALID = 3
        private const val TYPE_WHITELIST = 4
    }
}