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
class ResponseChain : Interceptor.IResponseChain<Response> {
    private var mResponse: Response

    constructor(
        response: Response,
        interceptors: List<Interceptor<Request, Response>>
    ) : super(response, interceptors) {
        mResponse = response
    }

    private constructor(
        response: Response,
        interceptors: List<Interceptor<Request, Response>>,
        index: Int,
        tag: String?
    ) : super(response, interceptors, index, tag) {
        mResponse = response
    }

    @Throws(IOException::class)
    override fun processNext(
        buffer: ByteBuffer?,
        response: Response,
        interceptors: List<Interceptor<Request, Response>>,
        index: Int,
    ) {
        var i = index
        val interceptor = interceptors[index]
        interceptor.intercept(ResponseChain(response, interceptors, ++i, mTag), buffer!!)
    }

    override fun response(): Response {
        return mResponse
    }

    override fun processFinal(buffer: ByteBuffer?) {
        mFlow.process(buffer)
    }

    override fun process(buffer: ByteBuffer?) {
        if (mIndex >= mInterceptors.size) {
            processFinal(buffer)
        } else {
            processNext(buffer, mFlow, mInterceptors, mIndex)
        }
    }
}