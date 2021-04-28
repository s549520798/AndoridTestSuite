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

import com.github.megatronking.netbare.gateway.AbstractRequestChain
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Http request chain, responsible for intercepting http request packets.
 *
 * @author Megatron King
 * @since 2018-11-16 23:21
 */
open class HttpRequestChain /* package */ /* package */ @JvmOverloads internal constructor(
    private val mZygoteRequest: HttpZygoteRequest?, interceptors: List<HttpInterceptor?>?,
    index: Int = 0, tag: Any? = null
) : AbstractRequestChain<HttpRequest, HttpInterceptor?>(
    mZygoteRequest, interceptors, index, tag
) {
    fun zygoteRequest(): HttpZygoteRequest? {
        return mZygoteRequest
    }

    @Throws(IOException::class)
    override fun processNext(
        buffer: ByteBuffer?, request: HttpRequest,
        interceptors: List<HttpInterceptor?>?, index: Int, tag: Any?
    ) {
        var index = index
        val interceptor = interceptors!![index]
        interceptor?.intercept(
            HttpRequestChain(mZygoteRequest, interceptors, ++index, tag),
            buffer!!
        )
    }

    @NonNull
    override fun request(): HttpRequest {
        val active = mZygoteRequest.getActive()
        return active ?: mZygoteRequest!!
    }
}