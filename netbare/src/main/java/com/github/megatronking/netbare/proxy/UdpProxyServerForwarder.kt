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
import com.github.megatronking.netbare.ip.IpHeader
import com.github.megatronking.netbare.ip.Protocol
import com.github.megatronking.netbare.ip.UdpHeader
import com.github.megatronking.netbare.net.SessionProvider
import com.github.megatronking.netbare.net.UidDumper
import java.io.IOException
import java.io.OutputStream

/**
 * Unlike TCP proxy server, UDP doesn't need handshake, we can forward packets to it directly.
 *
 * @author Megatron King
 * @since 2018-10-09 01:30
 */
class UdpProxyServerForwarder(vpnService: VpnService, mtu: Int, dumper: UidDumper?) :
    ProxyServerForwarder {
    private val mSessionProvider: SessionProvider
    private val mProxyServer: UdpProxyServer
    override fun prepare() {
        mProxyServer.start()
    }

    override fun forward(packet: ByteArray, len: Int, output: OutputStream) {
        val ipHeader = IpHeader(packet, 0)
        val udpHeader = UdpHeader(ipHeader, packet, ipHeader.headerLength)

        // Src IP & Port
        val localIp = ipHeader.sourceIp
        val localPort = udpHeader.sourcePort

        // Dest IP & Port
        val remoteIp = ipHeader.destinationIp
        val remotePort = udpHeader.destinationPort

        // UDP data size
        val udpDataSize = ipHeader.dataLength - udpHeader.headerLength
        NetBareLog.v(
            "ip: %s:%d -> %s:%d", NetBareUtils.convertIp(localIp),
            NetBareUtils.convertPort(localPort), NetBareUtils.convertIp(remoteIp),
            NetBareUtils.convertPort(remotePort)
        )
        NetBareLog.v("udp: %s, size: %d", udpHeader.toString(), udpDataSize)
        val session = mSessionProvider.ensureQuery(Protocol.UDP, localPort, remotePort, remoteIp)
        session!!.packetIndex++
        try {
            mProxyServer.send(udpHeader, output)
            session.sendDataSize += udpDataSize
        } catch (e: IOException) {
            NetBareLog.e(e.message)
        }
    }

    override fun release() {
        mProxyServer.stop()
    }

    init {
        mSessionProvider = SessionProvider(dumper)
        mProxyServer = UdpProxyServer(vpnService, mtu)
        mProxyServer.setSessionProvider(mSessionProvider)
    }
}