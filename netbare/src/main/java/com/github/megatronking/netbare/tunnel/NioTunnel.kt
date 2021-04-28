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
package com.github.megatronking.netbare.tunnel

import com.github.megatronking.netbare.NetBareUtils
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.spi.AbstractSelectableChannel
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * An abstract base nio tunnel class uses nio operations, the sub class should provides IO
 * operations, such as connect, read and write.
 *
 * @param <T> An implementation class for selectable channels
 * @param <S> A socket protects by VPN service.
 *
 * @author Megatron King
 * @since 2018-11-18 18:34
</S></T> */
abstract class NioTunnel<T : AbstractSelectableChannel?, S> internal constructor(
    private val mChannel: T, private val mSelector: Selector
) : Closeable, NioCallback, Tunnel {
    /**
     * Let the remote tunnel connects to remote server.
     *
     * @param address The remote server IP socket address.
     * @throws IOException if an I/O error occurs.
     */
    @Throws(IOException::class)
    abstract fun connect(address: InetSocketAddress)

    /**
     * Returns the socket should be protected by VPN service.
     *
     * @return A socket.
     */
    abstract fun socket(): S

    /**
     * Write the packet buffer to remote server.
     *
     * @param buffer A packet buffer.
     * @return The wrote length.
     * @throws IOException if an I/O error occurs.
     */
    @Throws(IOException::class)
    protected abstract fun channelWrite(buffer: ByteBuffer?): Int

    /**
     * Read data from remote server and put it into the given buffer.
     *
     * @param buffer A buffer to store data.
     * @return The read length.
     * @throws IOException if an I/O error occurs.
     */
    @Throws(IOException::class)
    protected abstract fun channelRead(buffer: ByteBuffer?): Int
    private var mSelectionKey: SelectionKey? = null
    private val mPendingBuffers: Deque<ByteBuffer?>
    private var mCallback: NioCallback? = null
    var isClosed = false
        private set

    @Throws(IOException::class)
    override fun onConnected() {
        if (mCallback != null) {
            mCallback!!.onConnected()
        }
    }

    @Throws(IOException::class)
    override fun onRead() {
        if (mCallback != null) {
            mCallback!!.onRead()
        }
    }

    @Throws(IOException::class)
    override fun onWrite() {
        if (mCallback != null) {
            mCallback!!.onWrite()
        }
        // Write pending buffers.
        while (!mPendingBuffers.isEmpty()) {
            val buffer = mPendingBuffers.pollFirst()
            val remaining = buffer!!.remaining()
            val sent = channelWrite(buffer)
            if (sent < remaining) {
                // Should wait next onWrite.
                mPendingBuffers.offerFirst(buffer)
                return
            }
        }
        interestRead()
    }

    override fun onClosed() {
        if (mCallback != null) {
            mCallback!!.onClosed()
        }
    }

    override val tunnel: NioTunnel<*, *>?
        get() = this

    override fun close() {
        isClosed = true
        mPendingBuffers.clear()
        NetBareUtils.closeQuietly(mChannel)
    }

    @Throws(IOException::class)
    override fun write(buffer: ByteBuffer?) {
        if (isClosed) {
            return
        }
        if (!buffer!!.hasRemaining()) {
            return
        }
        mPendingBuffers.offerLast(buffer)
        interestWrite()
    }

    @Throws(IOException::class)
    open fun read(buffer: ByteBuffer): Int {
        buffer.clear()
        val len = channelRead(buffer)
        if (len > 0) {
            buffer.flip()
        }
        return len
    }

    /* package */
    fun setNioCallback(callback: NioCallback?) {
        mCallback = callback
    }

    /* package */
    @Throws(IOException::class)
    fun prepareRead() {
        if (mChannel!!.isBlocking) {
            mChannel.configureBlocking(false)
        }
        mSelector.wakeup()
        mSelectionKey = mChannel.register(mSelector, SelectionKey.OP_READ, this)
    }

    private fun interestWrite() {
        if (mSelectionKey != null) {
            mSelector.wakeup()
            mSelectionKey!!.interestOps(SelectionKey.OP_WRITE)
        }
    }

    private fun interestRead() {
        if (mSelectionKey != null) {
            mSelector.wakeup()
            mSelectionKey!!.interestOps(SelectionKey.OP_READ)
        }
    }

    init {
        mPendingBuffers = ConcurrentLinkedDeque()
    }
}