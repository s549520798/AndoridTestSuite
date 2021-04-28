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
 * A virtual gateway interceptor, observes and modifies requests/responses. Interceptors are
 * organized by a virtual gateway, and process net packets one by one.
 *
 *
 * Methods are thread-safety due to interceptors are running in the local proxy server threads.
 *
 *
 *
 * Use [InterceptorFactory] to create an interceptor instance.
 *
 * @author Megatron King
 * @since 2018-11-13 23:46
 */
interface Interceptor<Req : Request, Res : Response> {
    /**
     * Intercept request packet, and delivery it to next interceptor or the terminal.
     *
     *
     * Remember do not block this method for a long time, because all the connections share the
     * same thread.
     *
     *
     * @param chain The request chain, call [RequestChain.process] to
     * delivery the packet.
     * @param buffer A nio buffer contains the packet data.
     * @throws IOException If an I/O error has occurred.
     */
    @Throws(IOException::class)
    fun intercept(chain: IRequestChain<Req>, buffer: ByteBuffer)

    /**
     * Intercept request packet, and delivery it to next interceptor or the terminal.
     *
     *
     * Remember do not block this method for a long time, because all the connections share the
     * same thread.
     *
     * @param chain The response chain, call [ResponseChain.process] to
     * delivery the packet.
     * @param buffer A nio buffer contains the packet data.
     * @throws IOException If an I/O error has occurred.
     */
    @Throws(IOException::class)
    fun intercept(chain: IResponseChain<Res>, buffer: ByteBuffer)

    /**
     * Invoked when a session's request has finished. It means the client has no more data sent to
     * server in this session, and it might invoked multi times if a connection is keep-alive.
     *
     * @param request The request.
     */
    fun onRequestFinished(request: Req)

    /**
     * Invoked when a session's response has finished. It means the server has no more data sent to
     * client in this session, and it might invoked multi times if a connection is keep-alive.
     *
     * @param response The response.
     */
    fun onResponseFinished(response: Res)

    abstract class Chain<T : TunnelFlow> protected constructor(
        flow: T,
        interceptors: List<Interceptor<out Request,out Response>>,
        index: Int = 0,
        tag: Any? = null
    ){

        private val mFlow: T = flow
        private val mInterceptors: List<Interceptor<out Request,out Response>> = interceptors
        private val mIndex: Int = index

        /**
         * Hand the net packets to the next [Interceptor].
         *
         * @param buffer A buffer contains net packet data.
         * @param flow A [TunnelFlow] implementation.
         * @param interceptors A collection of all interceptors in chain.
         * @param index The next interceptor index.
         * @param tag The chain's tag.
         * @throws IOException If an I/O error has occurred.
         */
        @Throws(IOException::class)
        fun processNext(buffer: ByteBuffer?, flow : T,interceptors : List<Interceptor<out Request, out Response>?>?, index: Int)

        /**
         * Finish the interception and send the packet to tunnel.
         *
         * @param buffer A buffer contains net packet data.
         * @throws IOException If an I/O error has occurred.
         */
        @Throws(IOException::class)
        fun processFinal(buffer: ByteBuffer?)

        /**
         * Hand the net packets to the next. If all interceptors have been processed, the packets will
         * be sent to tunnel, otherwise hand it to the next interceptor.
         *
         * @param buffer A buffer contains net packet data.
         * @throws IOException If an I/O error has occurred.
         */
        @Throws(IOException::class)
        fun process(buffer: ByteBuffer?)
    }

    abstract class IRequestChain<Req : Request> : Chain<Req> {
        /**
         * Get the current request instance in this chain.
         *
         * @return An instance of [Request].
         */
        abstract fun request(): Req
    }

    abstract class IResponseChain<Res : Response> : Chain<Res> {
        /**
         * Get the current response instance in this chain.
         *
         * @return An instance of [Response].
         */
        abstract fun response(): Res
    }
}