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
 * A chain with multiple [Interceptor] in series. The interceptors process net packets one by
 * one, and send the modified packets to tunnel in the end.
 *
 * @param <T> An implementation of [TunnelFlow], responsible for sending data to tunnel.
 * @param <I> An implementation of [Interceptor].
 *
 * @author Megatron King
 * @since 2018-11-13 23:00
</I></T> */
abstract class InterceptorChain<T : TunnelFlow, I : Interceptor<*, *, *, *>> protected constructor(
    flow: T,
    interceptors: List<I>,
    index: Int = 0,
    tag: Any? = null
) {
    private val mFlow: T = flow
    private val mInterceptors: List<I> = interceptors
    private val mIndex: Int = index
    /**
     * Returns this chain's tag.
     *
     * @return The Object stored in this chain as a tag, or `null` if not set.
     */
    /**
     * Sets the tag associated with this chain. A tag can be used to mark the session.
     *
     * @param tag An Object to tag the chain with.
     */
    /**
     * The chain's tag.
     */
    var tag: Any?

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
    protected abstract fun processNext(
        buffer: ByteBuffer?, flow: T, interceptors: List<I>?, index: Int,
        tag: Any?
    )

    /**
     * Finish the interception and send the packet to tunnel.
     *
     * @param buffer A buffer contains net packet data.
     * @throws IOException If an I/O error has occurred.
     */
    @Throws(IOException::class)
    fun processFinal(buffer: ByteBuffer?) {
        mFlow.process(buffer)
    }

    /**
     * Hand the net packets to the next. If all interceptors have been processed, the packets will
     * be sent to tunnel, otherwise hand it to the next interceptor.
     *
     * @param buffer A buffer contains net packet data.
     * @throws IOException If an I/O error has occurred.
     */
    @Throws(IOException::class)
    open fun process(buffer: ByteBuffer?) {
        if (mIndex >= mInterceptors.size) {
            processFinal(buffer)
        } else {
            processNext(buffer, mFlow, mInterceptors, mIndex, tag)
        }
    }
    /**
     * Constructs a new intercept chain with the tunnel flow instance and a collection of
     * interceptors. The chain will start from the given index.
     *
     * @param flow A [TunnelFlow] implementation.
     * @param interceptors A collection of interceptors.
     * @param tag The chain's tag.
     * @param index The head index.
     */
    /**
     * Constructs an intercept chain with a tunnel flow instance and a collection of interceptors.
     *
     * @param flow A [TunnelFlow] implementation.
     * @param interceptors A collection of interceptors.
     */
    init {
        this.tag = tag
    }
}