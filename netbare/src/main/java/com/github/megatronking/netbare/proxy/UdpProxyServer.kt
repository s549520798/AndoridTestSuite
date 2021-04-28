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
package com.github.megatronking.netbare.proxy

import android.net.VpnService
import android.os.SystemClock
import com.github.megatronking.netbare.NetBareLog
import com.github.megatronking.netbare.NetBareUtils
import com.github.megatronking.netbare.gateway.VirtualGateway
import com.github.megatronking.netbare.ip.UdpHeader
import com.github.megatronking.netbare.net.SessionProvider
import com.github.megatronking.netbare.tunnel.*
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel
import java.nio.channels.Selector
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * The UDP proxy server is a virtual server, every packet from [UdpProxyServerForwarder] is
 * saw as a connection. It use [UdpVATunnel] to bind [VirtualGateway] and
 * [NioTunnel] together. Not like TCP, UDP only use [UdpRemoteTunnel] to communicate with
 * real remote server.
 *
 * @author Megatron King
 * @since 2018-10-11 17:35
 */
/* package */
internal class UdpProxyServer(private val mVpnService: VpnService, private val mMtu: Int) :
    BaseProxyServer("UdpProxyServer") {
    private val mSelector: Selector
    private val mTunnels: MutableMap<Short, UdpVATunnel>
    private var mSessionProvider: SessionProvider? = null
    override val ip: Int
        get() = 0
    override val port: Short
        get() = 0

    fun setSessionProvider(sessionProvider: SessionProvider?) {
        mSessionProvider = sessionProvider
    }

    @Throws(IOException::class)
    fun send(header: UdpHeader, output: OutputStream) {
        val localPort = header.sourcePort
        var tunnel = mTunnels[localPort]
        try {
            if (tunnel == null) {
                val session = mSessionProvider!!.query(localPort)
                    ?: throw IOException("No session saved with key: $localPort")
                val ipHeader = header.ipHeader
                val remoteTunnel: NioTunnel<*, *> = UdpRemoteTunnel(
                    mVpnService, DatagramChannel.open(),
                    mSelector, NetBareUtils.convertIp(session.remoteIp), session.remotePort
                )
                tunnel = UdpVATunnel(session, remoteTunnel, output, mMtu)
                tunnel.connect(
                    InetSocketAddress(
                        NetBareUtils.convertIp(ipHeader.destinationIp),
                        NetBareUtils.convertPort(header.destinationPort)
                    )
                )
                mTunnels[header.sourcePort] = tunnel
            }
            tunnel.send(header)
        } catch (e: IOException) {
            mTunnels.remove(localPort)
            NetBareUtils.closeQuietly(tunnel)
            throw e
        }
    }

    override fun run() {
        NetBareLog.i("[UDP]Server starts running.")
        super.run()
        NetBareUtils.closeQuietly(mSelector)
        NetBareLog.i("[UDP]Server stops running.")
    }

    @Throws(IOException::class)
    override fun process() {
        val select = mSelector.select()
        if (select == 0) {
            // Wait a short time to let the selector register or interest.
            SystemClock.sleep(SELECTOR_WAIT_TIME.toLong())
            return
        }
        val selectedKeys = mSelector.selectedKeys() ?: return
        val iterator = selectedKeys.iterator()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (key.isValid) {
                val attachment = key.attachment()
                if (attachment is NioCallback) {
                    val callback = attachment
                    try {
                        if (key.isReadable) {
                            callback.onRead()
                        } else if (key.isWritable) {
                            callback.onWrite()
                        } else if (key.isConnectable) {
                            callback.onConnected()
                        }
                    } catch (e: IOException) {
                        callback.onClosed()
                        removeTunnel(callback.tunnel)
                    }
                }
            }
            iterator.remove()
        }
    }

    public override fun stopServer() {
        super.stopServer()
        for (tunnel in mTunnels.values) {
            NetBareUtils.closeQuietly(tunnel)
        }
    }

    private fun removeTunnel(tunnel: Tunnel?) {
        val tunnels: Map<Short, UdpVATunnel> = HashMap(mTunnels)
        for (key in tunnels.keys) {
            if (tunnels[key]?.remoteChannel === tunnel) {
                mTunnels.remove(key)
            }
        }
    }

    companion object {
        private const val SELECTOR_WAIT_TIME = 50
    }

    /* package */
    init {
        mSelector = Selector.open()
        mTunnels = ConcurrentHashMap()
    }
}