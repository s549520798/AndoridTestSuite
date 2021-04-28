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

import com.github.megatronking.netbare.gateway.*
import com.github.megatronking.netbare.net.Session
import com.github.megatronking.netbare.ssl.*
import java.util.*

/**
 * A [VirtualGatewayFactory] that produces the [HttpVirtualGateway].
 *
 * @author Megatron King
 * @since 2018-11-20 23:50
 */
class HttpVirtualGatewayFactory
/**
 * Constructs a [HttpVirtualGatewayFactory] instance with [JKS] and a collection of
 * [HttpInterceptorFactory].
 *
 * @param factories a collection of [HttpInterceptorFactory].
 * @return A instance of [HttpVirtualGatewayFactory].
 */(
    @param:NonNull private val mJKS: JKS,
    @param:NonNull private val mFactories: List<HttpInterceptorFactory>?
) : VirtualGatewayFactory {
    override fun create(session: Session, request: Request, response: Response): VirtualGateway {
        return HttpVirtualGateway(session, request, response, mJKS, ArrayList(mFactories))
    }

    companion object {
        /**
         * Create a [HttpVirtualGatewayFactory] instance with [JKS] and a collection of
         * [HttpInterceptorFactory].
         *
         * @param factories a collection of [HttpInterceptorFactory].
         * @return A instance of [HttpVirtualGatewayFactory].
         */
        fun create(
            @NonNull authority: JKS,
            @NonNull factories: List<HttpInterceptorFactory>?
        ): VirtualGatewayFactory {
            return HttpVirtualGatewayFactory(authority, factories)
        }
    }
}