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
 * Add the index parameter in the [.intercept] and
 * [.intercept], it indicates the packet index in the session.
 *
 *
 * The index will be reset when the session finished.
 *
 *
 * @author Megatron King
 * @since 2018-12-03 21:00
 */
abstract class IndexedInterceptor<Req : Request, Res : Response> :
    Interceptor<Req, Res> {
    private var mRequestIndex = 0
    private var mResponseIndex = 0

    /**
     * The same like [.intercept].
     *
     * @param chain The request chain, call [ReqChain.process] to
     * delivery the packet.
     * @param buffer A nio buffer contains the packet data.
     * @param index The packet index, started from 0.
     * @throws IOException If an I/O error has occurred.
     */
    @Throws(IOException::class)
    protected abstract fun intercept(
        chain: Interceptor.IRequestChain<Req>,  buffer: ByteBuffer,
        index: Int
    )

    /**
     * The same like [.intercept].
     *
     * @param chain The response chain, call [ResChain.process] to
     * delivery the packet.
     * @param buffer A nio buffer contains the packet data.
     * @param index The packet index, started from 0.
     * @throws IOException If an I/O error has occurred.
     */
    @Throws(IOException::class)
    protected abstract fun intercept(
        chain: Interceptor.IResponseChain<Res>, buffer: ByteBuffer,
        index: Int
    )

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.IRequestChain<Req>, buffer: ByteBuffer) {
        intercept(chain, buffer, mRequestIndex++)
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.IResponseChain<Res>, buffer: ByteBuffer) {
        intercept(chain, buffer, mResponseIndex++)
    }

    override fun onRequestFinished(request: Req) {
        mRequestIndex = 0
    }

    override fun onResponseFinished(response: Res) {
        mResponseIndex = 0
    }
}