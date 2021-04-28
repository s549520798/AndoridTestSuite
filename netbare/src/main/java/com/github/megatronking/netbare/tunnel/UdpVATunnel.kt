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
/*
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.megatronking.netbare.tunnel

import com.github.megatronking.netbare.NetBareLog
import com.github.megatronking.netbare.NetBareUtils
import com.github.megatronking.netbare.NetBareVirtualGateway
import com.github.megatronking.netbare.gateway.Request
import com.github.megatronking.netbare.gateway.Response
import com.github.megatronking.netbare.gateway.VirtualGateway
import com.github.megatronking.netbare.ip.IpHeader
import com.github.megatronking.netbare.ip.UdpHeader
import com.github.megatronking.netbare.net.Session
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer

/**
 * UDP protocol virtual gateway tunnel wraps [UdpRemoteTunnel] and itself as client and
 * server.
 *
 * @author Megatron King
 * @since 2018-11-25 20:16
 */
class UdpVATunnel(
    private val mSession: Session,
    val remoteChannel: NioTunnel<*, *>,
    private val mOutput: OutputStream,
    private val mMtu: Int
) : VirtualGatewayTunnel(), NioCallback, Tunnel {
    override val gateway: VirtualGateway
    private var mTemplateHeader: UdpHeader? = null
    @Throws(IOException::class)
    override fun connect(address: InetSocketAddress?) {
        remoteChannel.connect(address!!)
    }

    override fun onConnected() {}
    @Throws(IOException::class)
    override fun onRead() {
        if (remoteChannel.isClosed) {
            gateway.onRequestFinished()
            gateway.onResponseFinished()
            return
        }
        val buffer = ByteBuffer.allocate(mMtu)
        val len: Int
        len = try {
            remoteChannel.read(buffer)
        } catch (e: IOException) {
            throw ConnectionShutdownException(e.message)
        }
        if (len < 0) {
            close()
            return
        }
        gateway.onResponse(buffer)
    }

    override fun onWrite() {}
    override fun onClosed() {
        close()
    }

    override val tunnel: NioTunnel<*, *>?
        get() = null

    override fun close() {
        NetBareUtils.closeQuietly(remoteChannel)
        gateway.onRequestFinished()
        gateway.onResponseFinished()
    }

    fun send(header: UdpHeader) {
        if (remoteChannel.isClosed) {
            return
        }
        // Clone a template by the send data.
        if (mTemplateHeader == null) {
            mTemplateHeader = createTemplate(header)
        }
        try {
            gateway.onRequest(header.data())
        } catch (e: IOException) {
            NetBareLog.e(e.message)
            close()
        }
    }

    @Throws(IOException::class)
    override fun write(buffer: ByteBuffer?) {
        // Write to vpn.
        val header = mTemplateHeader!!.copy()
        val headerBuffer = header!!.buffer()
        val headLength = header.ipHeader.headerLength + header.headerLength
        val packet = ByteArray(headLength + buffer!!.remaining())
        headerBuffer!![packet, 0, headLength]
        buffer[packet, headLength, packet.size - headLength]
        val ipHeader = IpHeader(packet, 0)
        ipHeader.setTotalLength(packet.size.toShort())
        val udpHeader = UdpHeader(ipHeader, packet, ipHeader.headerLength)
        udpHeader.setTotalLength((packet.size - ipHeader.headerLength) as Short)
        ipHeader.updateChecksum()
        udpHeader.updateChecksum()
        mOutput.write(packet, 0, packet.size)
        mSession.receiveDataSize += packet.size
    }

    private fun createTemplate(header: UdpHeader): UdpHeader? {
        val templateUdp = header.copy()
        val templateIp = templateUdp.ipHeader
        // Swap ip
        val sourceIp = templateIp.sourceIp
        val destinationIp = templateIp.destinationIp
        templateIp.sourceIp = destinationIp
        templateIp.destinationIp = sourceIp
        // Swap port
        val sourcePort = templateUdp.sourcePort
        val destinationPort = templateUdp.destinationPort
        templateUdp.destinationPort = sourcePort
        templateUdp.sourcePort = destinationPort
        return templateUdp
    }

    init {
        gateway = NetBareVirtualGateway(
            mSession,
            Request(remoteChannel), Response(this)
        )
        remoteChannel.setNioCallback(this)
    }
}