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
import com.github.megatronking.netbare.NetBareVirtualGateway
import com.github.megatronking.netbare.gateway.Request
import com.github.megatronking.netbare.gateway.Response
import com.github.megatronking.netbare.gateway.VirtualGateway
import com.github.megatronking.netbare.net.Session
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer

/**
 * TCP protocol virtual gateway tunnel wraps [TcpProxyTunnel] and [TcpRemoteTunnel] as
 * client and server.
 *
 * @author Megatron King
 * @since 2018-11-18 00:19
 */
class TcpVATunnel(
    session: Session,
    private val mProxyTunnel: NioTunnel<*, *>,
    private val mRemoteTunnel: NioTunnel<*, *>,
    mtu: Int
) : VirtualGatewayTunnel() {
    override val gateway: VirtualGateway
    private val mMtu: Int
    @Throws(IOException::class)
    override fun connect(address: InetSocketAddress?) {
        mRemoteTunnel.connect(address!!)
    }

    private fun setCallbacks() {
        mProxyTunnel.setNioCallback(object : NioCallback {
            override fun onConnected() {
                // Nothing to do.
            }

            @Throws(IOException::class)
            override fun onRead() {
                if (mProxyTunnel.isClosed) {
                    gateway.onResponseFinished()
                    return
                }
                val buffer = ByteBuffer.allocate(mMtu)
                val len: Int
                len = try {
                    mProxyTunnel.read(buffer)
                } catch (e: IOException) {
                    throw ConnectionShutdownException(e.message)
                }
                if (len < 0 || mRemoteTunnel.isClosed) {
                    NetBareUtils.closeQuietly(mProxyTunnel)
                    gateway.onResponseFinished()
                    return
                }
                gateway.onRequest(buffer)
            }

            override fun onWrite() {
                // Do nothing
            }

            override fun onClosed() {
                close()
            }

            override val tunnel: NioTunnel<*, *>?
                get() = null
        })
        mRemoteTunnel.setNioCallback(object : NioCallback {
            @Throws(IOException::class)
            override fun onConnected() {
                // Prepare to read data.
                mProxyTunnel.prepareRead()
                mRemoteTunnel.prepareRead()
            }

            @Throws(IOException::class)
            override fun onRead() {
                if (mRemoteTunnel.isClosed) {
                    gateway.onRequestFinished()
                    return
                }
                val buffer = ByteBuffer.allocate(mMtu)
                val len: Int
                len = try {
                    mRemoteTunnel.read(buffer)
                } catch (e: IOException) {
                    throw ConnectionShutdownException(e.message)
                }
                if (len < 0 || mProxyTunnel.isClosed) {
                    NetBareUtils.closeQuietly(mRemoteTunnel)
                    gateway.onRequestFinished()
                    return
                }
                gateway.onResponse(buffer)
            }

            override fun onWrite() {
                // Do nothing
            }

            override fun onClosed() {
                close()
            }

            override val tunnel: NioTunnel<*, *>?
                get() = null
        })
    }

    override fun close() {
        NetBareUtils.closeQuietly(mProxyTunnel)
        NetBareUtils.closeQuietly(mRemoteTunnel)
        gateway.onRequestFinished()
        gateway.onResponseFinished()
    }

    init {
        gateway = NetBareVirtualGateway(
            session, Request(mRemoteTunnel),
            Response(mProxyTunnel)
        )
        mMtu = mtu
        setCallbacks()
    }
}