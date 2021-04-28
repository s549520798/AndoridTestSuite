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

import com.github.megatronking.netbare.http2.Http2Settings
import java.util.*

/**
 * Provides HTTP protocol session information.
 *
 * @author Megatron King
 * @since 2018-11-10 11:56
 */
/* package */
class HttpSession {
    var isHttps = false
    var protocol: HttpProtocol? = null
    var method: HttpMethod? = null
    var path: String? = null
    var requestHeaders: MutableMap<String?, MutableList<String>> = LinkedHashMap()
    var responseHeaders: MutableMap<String?, MutableList<String>> = LinkedHashMap()
    var code = 0
    var message: String? = null
    var reqBodyOffset = 0
    var resBodyOffset = 0

    // Belows is for HTTP2
    var clientHttp2Settings: Http2Settings? = null
    var peerHttp2Settings: Http2Settings? = null
    var requestStreamEnd = false
    var responseStreamEnd = false
}