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

import com.github.megatronking.netbare.gateway.PendingIndexedInterceptor

/**
 * An abstract interceptor provides multi-apis for packet pending. The packet will be stored in a
 * queue, and you can merge them with another packet.
 *
 * @author Megatron King
 * @since 2018-12-09 12:07
 */
abstract class HttpPendingIndexedInterceptor :
    PendingIndexedInterceptor<HttpRequest?, HttpRequestChain?, HttpResponse?, HttpResponseChain?>(),
    HttpInterceptor