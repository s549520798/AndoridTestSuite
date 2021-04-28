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

import android.net.VpnService
import com.github.megatronking.netbare.NetBareXLog
import com.github.megatronking.netbare.ip.Protocol
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Selector
import java.nio.channels.SocketChannel

/**
 * A TCP tunnel communicates with the remote server.
 *
 * @author Megatron King
 * @since 2018-11-21 01:41
 */
class TcpRemoteTunnel(
    private val mVpnService: VpnService, channel: SocketChannel, selector: Selector,
    remoteIp: String?, remotePort: Int
) : TcpTunnel(channel, selector) {
    private val mLog: NetBareXLog
    @Throws(IOException::class)
    override fun connect(address: InetSocketAddress) {
        if (mVpnService.protect(socket())) {
            super.connect(address)
            mLog.i("Connect to remote server %s", address)
        } else {
            throw IOException("[TCP]Can not protect remote tunnel socket.")
        }
    }

    @Throws(IOException::class)
    override fun onConnected() {
        mLog.i("Remote tunnel is connected.")
        super.onConnected()
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteBuffer): Int {
        val len = super.read(buffer)
        mLog.i("Read from remote: $len")
        return len
    }

    @Throws(IOException::class)
    override fun write(buffer: ByteBuffer?) {
        mLog.i("Write to remote: " + buffer!!.remaining())
        super.write(buffer)
    }

    override fun close() {
        mLog.i("Remote tunnel is closed.")
        super.close()
    }

    init {
        mLog = NetBareXLog(Protocol.TCP, remoteIp, remotePort)
    }
}