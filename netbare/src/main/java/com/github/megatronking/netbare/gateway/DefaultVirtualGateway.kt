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

import com.github.megatronking.netbare.net.Session
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*

/**
 * A [VirtualGateway] provides the interception service. Interceptors are organized as a list
 * in chain, can observe and modify packets. Use [DefaultVirtualGatewayFactory] to create an
 * instance.
 *
 * @author Megatron King
 * @since 2018-11-01 23:35
 */
/* package */
internal class DefaultVirtualGateway(
    session: Session?, request: Request, response: Response,
    factories: List<InterceptorFactory<Request, Response>>
) : VirtualGateway(session, request, response) {
    private val mInterceptors: MutableList<Interceptor<Request, Response>>
    @Throws(IOException::class)
    override fun onRequest(buffer: ByteBuffer?) {
        RequestChain(mRequest, mInterceptors).process(buffer)
    }

    @Throws(IOException::class)
    override fun onResponse(buffer: ByteBuffer?) {
        ResponseChain(mResponse, mInterceptors).process(buffer)
    }

    override fun onRequestFinished() {
        for (interceptor in mInterceptors) {
            interceptor.onRequestFinished(mRequest)
        }
    }

    override fun onResponseFinished() {
        for (interceptor in mInterceptors) {
            interceptor.onResponseFinished(mResponse)
        }
    }

    /* package */
    init {
        mInterceptors = ArrayList(factories.size)
        for (factory in factories) {
            mInterceptors.add(factory.create())
        }
    }
}