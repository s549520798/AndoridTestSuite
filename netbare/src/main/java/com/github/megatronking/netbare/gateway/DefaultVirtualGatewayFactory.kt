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
import java.util.*

/**
 * A [VirtualGatewayFactory] that produces the [DefaultVirtualGateway].
 *
 * @author Megatron King
 * @since 2018-11-01 23:29
 */
class DefaultVirtualGatewayFactory private constructor(private val mFactories: List<InterceptorFactory<Request, Response>>) :
    VirtualGatewayFactory {
    override fun create(session: Session, request: Request, response: Response): VirtualGateway {
        return DefaultVirtualGateway(session, request, response, ArrayList(mFactories))
    }

    companion object {
        /**
         * Create a [VirtualGatewayFactory] instance with a collection of
         * [InterceptorFactory].
         *
         * @param factories a collection of [InterceptorFactory].
         * @return A instance of [DefaultVirtualGatewayFactory].
         */
        /**
         * Create a [VirtualGatewayFactory] instance that not contains [Interceptor].
         *
         * @return A instance of [VirtualGatewayFactory].
         */
        @JvmOverloads
        fun create(factories: List<InterceptorFactory<Request, Response>> = ArrayList()): VirtualGatewayFactory {
            return DefaultVirtualGatewayFactory(factories)
        }
    }
}