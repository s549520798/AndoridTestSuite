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

import com.github.megatronking.netbare.NetBareLog
import com.github.megatronking.netbare.ip.IcmpHeader
import com.github.megatronking.netbare.ip.IpHeader
import java.io.OutputStream

/**
 * Forward the Internet Control Message Protocol (ICMP) to proxy server.
 *
 * @author Megatron King
 * @since 2018-10-09 01:30
 */
class IcmpProxyServerForwarder : ProxyServerForwarder {
    override fun prepare() {
        // TODO
    }

    override fun forward(packet: ByteArray, len: Int, output: OutputStream) {
        val ipHeader = IpHeader(packet, 0)
        val icmpHeader = IcmpHeader(ipHeader, packet, ipHeader.headerLength)
        NetBareLog.v("ICMP type: " + icmpHeader.type)
        NetBareLog.v("ICMP code: " + icmpHeader.code)
        // TODO transfer to proxy server
    }

    override fun release() {
        // TODO
    }
}