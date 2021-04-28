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
package com.github.megatronking.netbare.gateway

import java.io.IOException
import java.nio.ByteBuffer

/**
 * A response chain, responsible for intercepting response packets.
 *
 * @author Megatron King
 * @since 2018-11-14 23:19
 */
class ResponseChain :
    AbstractResponseChain<Response, Interceptor<Request?, RequestChain?, Response?, ResponseChain?>?> {
    private var mResponse: Response

    constructor(
        response: Response,
        interceptors: List<Interceptor<Request?, RequestChain?, Response?, ResponseChain?>?>?
    ) : super(response, interceptors) {
        mResponse = response
    }

    private constructor(
        response: Response,
        interceptors: List<Interceptor<Request?, RequestChain?, Response?, ResponseChain?>?>?,
        index: Int,
        tag: Any?
    ) : super(response, interceptors, index, tag) {
        mResponse = response
    }

    @Throws(IOException::class)
    override fun processNext(
        buffer: ByteBuffer?,
        response: Response,
        interceptors: List<Interceptor<Request?, RequestChain?, Response?, ResponseChain?>?>?,
        index: Int,
        tag: Any?
    ) {
        var index = index
        val interceptor = interceptors!![index]
        interceptor?.intercept(ResponseChain(response, interceptors, ++index, tag), buffer!!)
    }

    @NonNull
    override fun response(): Response {
        return mResponse
    }
}