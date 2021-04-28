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

import com.github.megatronking.netbare.gateway.Response
import com.github.megatronking.netbare.http2.Http2Settings
import com.github.megatronking.netbare.http2.Http2Updater
import java.util.*

/**
 * A zygote http response class, it creates the real http response instance.
 *
 * @author Megatron King
 * @since 2019/1/6 17:09
 */
class HttpZygoteResponse internal constructor(
    private val mResponse: Response,
    private val mSessionFactory: HttpSessionFactory
) : HttpResponse(
    mResponse, mSessionFactory.create(mResponse.id())
), Http2Updater {
    private val mCachedResponses: MutableMap<String?, HttpResponse>
    var active: HttpResponse? = null
        private set

    fun zygote(id: HttpId) {
        if (mCachedResponses.containsKey(id.id)) {
            active = mCachedResponses[id.id]
        } else {
            val originSession = super.session()
            val session = mSessionFactory.create(id.id)
            session!!.isHttps = originSession!!.isHttps
            session.protocol = originSession.protocol
            session.clientHttp2Settings = originSession.clientHttp2Settings
            session.peerHttp2Settings = originSession.peerHttp2Settings
            val response = HttpResponse(mResponse, id, session)
            mCachedResponses[id.id] = response
            active = response
        }
    }

    override fun onSettingsUpdate(http2Settings: Http2Settings) {
        session()!!.peerHttp2Settings = http2Settings
    }

    override fun onStreamFinished() {
        val response = active
        if (response != null) {
            response.session()!!.responseStreamEnd = true
        }
    }

    /* package */
    init {
        mCachedResponses = HashMap()
    }
}