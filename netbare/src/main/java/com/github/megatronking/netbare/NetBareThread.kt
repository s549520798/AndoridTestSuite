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
package com.github.megatronking.netbare

import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.github.megatronking.netbare.ip.*
import com.github.megatronking.netbare.net.UidDumper
import com.github.megatronking.netbare.proxy.IcmpProxyServerForwarder
import com.github.megatronking.netbare.proxy.ProxyServerForwarder
import com.github.megatronking.netbare.proxy.TcpProxyServerForwarder
import com.github.megatronking.netbare.proxy.UdpProxyServerForwarder
import java.io.*
import java.util.*

/**
 * A work thread running NetBare core logic. NetBase established the VPN connection is this thread
 * and read packets from the VPN file descriptor and transfer them to local proxy servers. Every
 * IP protocol runs an independent local proxy server to receive the packets.
 *
 * @author Megatron King
 * @since 2018-10-08 19:38
 */
/* package */
internal class NetBareThread     /* package */(
    private val mVpnService: VpnService,
    private val mConfig: NetBareConfig
) : Thread("NetBare") {
    private var vpnDescriptor: ParcelFileDescriptor? = null
    private var input: FileInputStream? = null
    private var output: FileOutputStream? = null
    private var packetsTransfer: PacketsTransfer? = null
    override fun interrupt() {
        super.interrupt()
        packetsTransfer!!.stop()
        NetBareUtils.closeQuietly(vpnDescriptor)
        NetBareUtils.closeQuietly(input)
        NetBareUtils.closeQuietly(output)
    }

    override fun run() {
        super.run()

        // Notify NetBareListener that the service is started now.
        NetBare.Companion.get().notifyServiceStarted()
        try {
            packetsTransfer = PacketsTransfer(mVpnService, mConfig)
        } catch (e: IOException) {
            NetBareLog.wtf(e)
        }
        if (packetsTransfer != null) {
            // Establish VPN, it runs a while loop unless failed.
            establishVpn(packetsTransfer!!)
        }

        // Notify NetBareListener that the service is stopped now.
        NetBare.Companion.get().notifyServiceStopped()
    }

    private fun establishVpn(packetsTransfer: PacketsTransfer) {
        val builder = mVpnService.Builder()
        builder.setMtu(mConfig.mtu)
        builder.addAddress(mConfig.address!!.address!!, mConfig.address!!.prefixLength)
        if (mConfig.session != null) {
            builder.setSession(mConfig.session!!)
        }
        if (mConfig.configureIntent != null) {
            builder.setConfigureIntent(mConfig.configureIntent!!)
        }
        for (ip in mConfig.routes!!) {
            builder.addRoute(ip!!.address!!, ip.prefixLength)
        }
        for (address in mConfig.dnsServers!!) {
            builder.addDnsServer(address!!)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder.setBlocking(true)
                for (packageName in mConfig.allowedApplications!!) {
                    builder.addAllowedApplication(packageName)
                }
                for (packageName in mConfig.disallowedApplications!!) {
                    builder.addDisallowedApplication(packageName)
                }
                // Add self to allowed list.
                if (!mConfig.allowedApplications!!.isEmpty()) {
                    builder.addAllowedApplication(mVpnService.packageName)
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            NetBareLog.wtf(e)
        }
        vpnDescriptor = builder.establish()
        if (vpnDescriptor == null) {
            return
        }

        // Open io with the VPN descriptor.
        val descriptor = vpnDescriptor!!.fileDescriptor ?: return
        input = FileInputStream(descriptor)
        output = FileOutputStream(descriptor)
        packetsTransfer.start()
        try {
            // Read packets from input io and forward them to proxy servers.
            while (!isInterrupted) {
                packetsTransfer.transfer(input!!, output!!)
            }
        } catch (e: IOException) {
            if (!isInterrupted) {
                NetBareLog.wtf(e)
            }
        }
    }

    private class PacketsTransfer(service: VpnService, config: NetBareConfig) {
        private val mForwarderRegistry: MutableMap<Protocol?, ProxyServerForwarder>
        private val buffer: ByteArray
        fun start() {
            for (forwarder in mForwarderRegistry.values) {
                forwarder.prepare()
            }
        }

        fun stop() {
            for (forwarder in mForwarderRegistry.values) {
                forwarder.release()
            }
            mForwarderRegistry.clear()
        }

        @Throws(IOException::class)
        fun transfer(input: InputStream, output: OutputStream) {
            // The thread would be blocked if there is no outgoing packets from input stream.
            transfer(buffer, input.read(buffer), output)
        }

        private fun transfer(packet: ByteArray, len: Int, output: OutputStream) {
            if (len < IpHeader.Companion.MIN_HEADER_LENGTH) {
                NetBareLog.w("Ip header length < " + IpHeader.Companion.MIN_HEADER_LENGTH)
                return
            }
            val ipHeader = IpHeader(packet, 0)
            val protocol: Protocol? = Protocol.parse(ipHeader.protocol.toInt())
            val forwarder = mForwarderRegistry[protocol]
            if (forwarder != null) {
                forwarder.forward(packet, len, output)
            } else {
                NetBareLog.w("Unknown ip protocol: " + ipHeader.protocol)
            }
        }

        init {
            val mtu = config.mtu
            val localIp = config.address!!.address
            val uidDumper = if (config.dumpUid) UidDumper(localIp, config.uidProvider) else null
            // Register all supported protocols here.
            mForwarderRegistry = LinkedHashMap(3)
            // TCP
            mForwarderRegistry[Protocol.TCP] = TcpProxyServerForwarder(
                service, localIp, mtu,
                uidDumper
            )
            // UDP
            mForwarderRegistry[Protocol.UDP] = UdpProxyServerForwarder(
                service, mtu,
                uidDumper
            )
            // ICMP
            mForwarderRegistry[Protocol.ICMP] = IcmpProxyServerForwarder()
            buffer = ByteArray(mtu)
        }
    }
}