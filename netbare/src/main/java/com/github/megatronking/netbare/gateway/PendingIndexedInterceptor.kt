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

import java.nio.ByteBuffer
import java.util.*

/**
 * An abstract interceptor provides multi-apis for packet pending. The packet will be stored in a
 * queue, and you can merge them with another packet.
 *
 * @author Megatron King
 * @since 2018-12-09 12:07
 */
abstract class PendingIndexedInterceptor<Req : Request?, ReqChain : AbstractRequestChain<Req, out Interceptor<*, *, *, *>?>?, Res : Response?, ResChain : AbstractResponseChain<Res, out Interceptor<*, *, *, *>?>?> :
    IndexedInterceptor<Req, ReqChain, Res, ResChain>() {
    private val mRequestPendingBuffers: MutableList<ByteBuffer>
    private val mResponsePendingBuffers: MutableList<ByteBuffer>
    override fun onRequestFinished(@NonNull request: Req) {
        super.onRequestFinished(request)
        mRequestPendingBuffers.clear()
    }

    override fun onResponseFinished(@NonNull response: Res) {
        super.onResponseFinished(response)
        mResponsePendingBuffers.clear()
    }

    /**
     * Pend a request packet buffer to waiting queue.
     *
     * @param buffer A request packet.
     */
    protected fun pendRequestBuffer(buffer: ByteBuffer) {
        mRequestPendingBuffers.add(buffer)
    }

    /**
     * Pend a response packet buffer to waiting queue.
     *
     * @param buffer A response packet.
     */
    protected fun pendResponseBuffer(buffer: ByteBuffer) {
        mResponsePendingBuffers.add(buffer)
    }

    /**
     * Merge all the request pending buffers and a given buffer, and output a new buffer which
     * contains all data. The pending buffers will be clear after the merge action.
     *
     * @param buffer A fresh packet buffer.
     * @return A new buffer.
     */
    protected fun mergeRequestBuffer(buffer: ByteBuffer): ByteBuffer {
        return merge(mRequestPendingBuffers, buffer)
    }

    /**
     * Merge all the response pending buffers and a given buffer, and output a new buffer which
     * contains all data. The pending buffers will be clear after the merge action.
     *
     * @param buffer A fresh packet buffer.
     * @return A new buffer.
     */
    protected fun mergeResponseBuffer(buffer: ByteBuffer): ByteBuffer {
        return merge(mResponsePendingBuffers, buffer)
    }

    private fun merge(pendingBuffers: MutableList<ByteBuffer>, buffer: ByteBuffer): ByteBuffer {
        var buffer = buffer
        if (!pendingBuffers.isEmpty()) {
            var total = 0
            for (pendingBuffer in pendingBuffers) {
                total += pendingBuffer.remaining()
            }
            total += buffer.remaining()

            // Merge elder buffer first.
            var offset = 0
            val array = ByteArray(total)
            for (pendingBuffer in pendingBuffers) {
                System.arraycopy(
                    pendingBuffer.array(), pendingBuffer.position(), array, offset,
                    pendingBuffer.remaining()
                )
                offset += pendingBuffer.remaining()
            }

            // Merge the incoming buffer
            System.arraycopy(buffer.array(), buffer.position(), array, offset, buffer.remaining())
            buffer = ByteBuffer.wrap(array)
            // Clear all data.
            pendingBuffers.clear()
        }
        return buffer
    }

    /**
     * Constructs a [PendingIndexedInterceptor] instance.
     */
    init {
        mRequestPendingBuffers = ArrayList()
        mResponsePendingBuffers = ArrayList()
    }
}