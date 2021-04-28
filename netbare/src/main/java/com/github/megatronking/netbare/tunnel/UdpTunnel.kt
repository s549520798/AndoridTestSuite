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

import com.github.megatronking.netbare.NetBareLog
import java.io.IOException
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.Selector

/**
 * A UDP protocol implementation based with [NioTunnel].
 *
 * @author Megatron King
 * @since 2018-12-02 19:24
 */
abstract class UdpTunnel internal constructor(
    private val mDatagramChannel: DatagramChannel,
    selector: Selector
) : NioTunnel<DatagramChannel?, DatagramSocket>(
    mDatagramChannel, selector
) {
    @Throws(IOException::class)
    override fun connect(address: InetSocketAddress) {
        NetBareLog.i(
            "UDP connects to: %s:%s",
            address.address.hostAddress, address.port
        )
        if (mDatagramChannel.isBlocking) {
            mDatagramChannel.configureBlocking(false)
        }
        mDatagramChannel.connect(address)
        prepareRead()
    }

    override fun socket(): DatagramSocket {
        return mDatagramChannel.socket()
    }

    @Throws(IOException::class)
    override fun channelWrite(buffer: ByteBuffer?): Int {
        return mDatagramChannel.write(buffer)
    }

    @Throws(IOException::class)
    override fun channelRead(buffer: ByteBuffer?): Int {
        return mDatagramChannel.read(buffer)
    }
}