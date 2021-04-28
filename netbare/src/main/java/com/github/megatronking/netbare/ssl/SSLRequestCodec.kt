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
package com.github.megatronking.netbare.ssl

import com.github.megatronking.netbare.NetBareLog
import com.github.megatronking.netbare.gateway.Request
import java.util.concurrent.ExecutionException
import javax.net.ssl.SSLEngine

/**
 * An implementation of [SSLCodec] to codec request SSL packets. This codec creates a MITM
 * SSL server engine using [SSLEngineFactory], it requires the remote server host.
 *
 * @author Megatron King
 * @since 2018-11-15 23:23
 */
open class SSLRequestCodec
/**
 * Constructs an instance of [SSLCodec] by a factory.
 *
 * @param factory A factory produces [SSLEngine].
 */
    (factory: SSLEngineFactory?) : SSLCodec(factory) {
    private var mRequest: Request? = null
    private var mEngine: SSLEngine? = null

    /**
     * Bind a [Request] to this codec.
     *
     * @param request A request has terminated remote server host.
     */
    fun setRequest(request: Request?) {
        mRequest = request
    }

    override fun createEngine(factory: SSLEngineFactory?): SSLEngine? {
        if (mEngine == null) {
            val host = mRequest!!.host()
            if (host == null) {
                // Unable to get host.
                NetBareLog.e("Failed to get SSL host.")
                return null
            }
            try {
                mEngine = factory!!.createServerEngine(host)
                mEngine!!.useClientMode = false
                mEngine!!.needClientAuth = false
            } catch (e: ExecutionException) {
                NetBareLog.e("Failed to create server SSLEngine: " + e.message)
            }
        }
        return mEngine
    }
}