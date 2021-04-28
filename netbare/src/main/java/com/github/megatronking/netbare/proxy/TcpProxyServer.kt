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
import com.github.megatronking.netbare.NetBareLog
import com.github.megatronking.netbare.NetBareUtils
import com.github.megatronking.netbare.gateway.VirtualGateway
import com.github.megatronking.netbare.net.SessionProvider
import com.github.megatronking.netbare.ssl.SSLWhiteList
import com.github.megatronking.netbare.tunnel.*
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

/**
 * The TCP proxy server is a nio [ServerSocketChannel], it listens the connections from
 * [VpnService] and forwards request packets to real remote server. This server uses
 * [TcpVATunnel] to bind [VirtualGateway] and [NioTunnel] together. Every TCP
 * connection has two channels: [TcpProxyTunnel] and [TcpRemoteTunnel].
 * The [TcpProxyTunnel] is responsible for sending remote server response packets to VPN
 * service, and the [TcpRemoteTunnel] is responsible for communicating with remote server.
 *
 * @author Megatron King
 * @since 2018-10-11 17:35
 */
/* package */
internal class TcpProxyServer(private val mVpnService: VpnService, ip: String?, mtu: Int) :
    BaseProxyServer("TcpProxyServer"), Runnable {
    private val mSelector: Selector
    private val mServerSocketChannel: ServerSocketChannel
    override val ip: Int
    override val port: Short
    private val mMtu: Int
    private var mSessionProvider: SessionProvider? = null
    fun setSessionProvider(sessionProvider: SessionProvider?) {
        mSessionProvider = sessionProvider
    }

    override fun run() {
        NetBareLog.i("[TCP]Server starts running.")
        super.run()
        NetBareUtils.closeQuietly(mSelector)
        NetBareUtils.closeQuietly(mServerSocketChannel)
        NetBareLog.i("[TCP]Server stops running.")
    }

    @Throws(IOException::class)
    override fun process() {
        val select = mSelector.select()
        if (select == 0) {
            return
        }
        val selectedKeys = mSelector.selectedKeys() ?: return
        val iterator = selectedKeys.iterator()
        while (iterator.hasNext()) {
            val key = iterator.next()
            try {
                if (key.isValid) {
                    if (key.isAcceptable) {
                        onAccept()
                    } else {
                        val attachment = key.attachment()
                        if (attachment is NioCallback) {
                            val callback = attachment
                            try {
                                if (key.isConnectable) {
                                    callback.onConnected()
                                } else if (key.isReadable) {
                                    callback.onRead()
                                } else if (key.isWritable) {
                                    callback.onWrite()
                                }
                            } catch (e: IOException) {
                                val tunnel = callback.tunnel
                                var ip: String? = null
                                val address = (tunnel!!.socket() as Socket).inetAddress
                                if (address != null) {
                                    ip = address.hostAddress
                                }
                                if (!tunnel.isClosed) {
                                    handleException(e, ip)
                                }
                                callback.onClosed()
                            }
                        }
                    }
                }
            } finally {
                iterator.remove()
            }
        }
    }

    @Throws(IOException::class)
    private fun onAccept() {
        val clientChannel = mServerSocketChannel.accept()
        val clientSocket = clientChannel.socket()

        // The client ip is the remote server ip
        // The client port is the local port(it is the vpn port not the proxy server port)
        val ip = clientSocket.inetAddress.hostAddress
        val port = clientSocket.port

        // The session should have be saved before the tcp packets be forwarded to proxy server. So
        // we can query it by client port.
        val session = mSessionProvider!!.query(port.toShort())
            ?: throw IOException("No session saved with key: $port")
        val remotePort = NetBareUtils.convertPort(session.remotePort)

        // Connect remote server and dispatch data.
        var proxyTunnel: TcpTunnel? = null
        var remoteTunnel: TcpTunnel? = null
        try {
            proxyTunnel = TcpProxyTunnel(clientChannel, mSelector, remotePort)
            remoteTunnel = TcpRemoteTunnel(
                mVpnService, SocketChannel.open(),
                mSelector, ip, remotePort
            )
            val gatewayTunnel = TcpVATunnel(
                session, proxyTunnel,
                remoteTunnel, mMtu
            )
            gatewayTunnel.connect(InetSocketAddress(ip, remotePort))
        } catch (e: IOException) {
            NetBareUtils.closeQuietly(proxyTunnel)
            NetBareUtils.closeQuietly(remoteTunnel)
            throw e
        }
    }

    private fun handleException(e: IOException?, ip: String?) {
        if (e == null || e.message == null) {
            return
        }
        if (e is SSLHandshakeException) {
            // Client doesn't accept the MITM CA certificate.
            NetBareLog.e(e.message)
            if (ip != null) {
                NetBareLog.i("add %s to whitelist", ip)
                SSLWhiteList.add(ip)
            }
        } else if (e is ConnectionShutdownException) {
            // Connection exception, do not mind this.
            NetBareLog.e(e.message)
        } else if (e is ConnectException) {
            // Connection timeout
            NetBareLog.e(e.message)
        } else if (e is SSLException && e.cause is EOFException) {
            // Connection shutdown manually
            NetBareLog.e(e.message)
        } else {
            NetBareLog.wtf(e)
            if (ip != null) {
                NetBareLog.i("add %s to whitelist", ip)
                SSLWhiteList.add(ip)
            }
        }
    }

    /* package */
    init {
        mSelector = Selector.open()
        mServerSocketChannel = ServerSocketChannel.open()
        mServerSocketChannel.configureBlocking(false)
        mServerSocketChannel.socket().bind(InetSocketAddress(0))
        mServerSocketChannel.register(mSelector, SelectionKey.OP_ACCEPT)
        this.ip = NetBareUtils.convertIp(ip)
        port = mServerSocketChannel.socket().localPort.toShort()
        mMtu = mtu
        NetBareLog.v("[TCP]proxy server: %s:%d", ip, NetBareUtils.convertPort(port))
    }
}