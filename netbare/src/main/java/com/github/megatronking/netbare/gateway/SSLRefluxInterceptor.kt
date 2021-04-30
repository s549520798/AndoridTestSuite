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

import com.github.megatronking.netbare.ssl.SSLRefluxCallback
import java.io.IOException
import java.nio.ByteBuffer

/**
 * An interceptor locates at the last layer of the interceptors. It is responsible for send
 * plaintext packets to [SSLCodecInterceptor].
 *
 * @author Megatron King
 * @since 2018-11-15 15:39
 */
abstract class SSLRefluxInterceptor<Req : Request, ReqChain : Interceptor.IRequestChain<Req>, Res : Response, ResChain : Interceptor.IResponseChain<Res>>(
    private val mRefluxCallback: SSLRefluxCallback<Req, Res>
) : Interceptor<Req, Res> {
    /**
     * Should reflux the request buffer to SSL codec if the buffer is origin decrypted.
     *
     * @param chain The request chain.
     * @return True if needs to encrypt again.
     */
    protected abstract fun shouldReflux(chain: ReqChain): Boolean

    /**
     * Should reflux the response buffer to SSL codec if the buffer is origin decrypted.
     *
     * @param chain The response chain.
     * @return True if needs to encrypt again.
     */
    protected abstract fun shouldReflux(chain: ResChain): Boolean

    @Throws(IOException::class)
    override fun intercept(chain: ReqChain, buffer: ByteBuffer) {
        if (shouldReflux(chain)) {
            mRefluxCallback.onRequest(chain.request(), buffer)
        } else {
            chain.process(buffer)
        }
    }

    @Throws(IOException::class)
    override fun intercept(chain: ResChain, buffer: ByteBuffer) {
        if (shouldReflux(chain)) {
            mRefluxCallback.onResponse(chain.response(), buffer)
        } else {
            chain.process(buffer)
        }
    }

    override fun onRequestFinished(request: Req) {}
    override fun onResponseFinished(response: Res) {}
}