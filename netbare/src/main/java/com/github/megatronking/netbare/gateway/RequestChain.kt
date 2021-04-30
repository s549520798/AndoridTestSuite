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
 * A request chain, responsible for intercepting request packets.
 *
 * @author Megatron King
 * @since 2018-11-14 23:18
 */
class RequestChain : Interceptor.IRequestChain<Request> {

    private var mRequest: Request

    constructor(
        request: Request,
        interceptors: List<Interceptor<out Request, out Response>>
    ) : super(request, interceptors) {
        mRequest = request
    }

    private constructor(
        request: Request,
        interceptors: List<Interceptor<out Request, out Response>>,
        index: Int,
        tag: String?
    ) : super(request, interceptors, index, tag) {
        mRequest = request
    }

    @Throws(IOException::class)
    override fun processNext(
        buffer: ByteBuffer?,
        request: Request,
        interceptors: List<Interceptor<out Request, out Response>>,
        index: Int
    ) {
        var i = index
        val interceptor = interceptors.get(i)
        interceptor.intercept(RequestChain(request, interceptors, ++i, mTag), buffer!!)
    }

    override fun request(): Request {
        return mRequest
    }

}