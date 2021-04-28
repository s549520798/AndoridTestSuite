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

import android.app.PendingIntent
import com.github.megatronking.netbare.NetBareConfig.Builder
import com.github.megatronking.netbare.gateway.VirtualGatewayFactory
import com.github.megatronking.netbare.http.HttpInterceptorFactory
import com.github.megatronking.netbare.http.HttpVirtualGatewayFactory
import com.github.megatronking.netbare.ip.IpAddress
import com.github.megatronking.netbare.net.UidProvider
import com.github.megatronking.netbare.ssl.*
import java.util.*

/**
 * The configuration class for NetBare. Use [Builder] to construct an instance.
 *
 * @author Megatron King
 * @since 2018-10-07 11:20
 */
class NetBareConfig private constructor() {
    var session: String? = null
    var configureIntent: PendingIntent? = null
    var mtu = 0
    var address: IpAddress? = null
    var routes: MutableSet<IpAddress>? = null
    var dnsServers: MutableSet<String>? = null
    var allowedApplications: MutableSet<String>? = null
    var disallowedApplications: MutableSet<String>? = null
    var allowedHosts: MutableSet<String>? = null
    var disallowedHosts: MutableSet<String>? = null
    var gatewayFactory: VirtualGatewayFactory? = null
    var uidProvider: UidProvider? = null
    var dumpUid = false
    var excludeSelf = false
    var keyManagerProvider: SSLKeyManagerProvider? = null
    var trustManagerProvider: SSLTrustManagerProvider? = null

    /**
     * Create a new builder based on the current.
     *
     * @return A new builder instance.
     */
    fun newBuilder(): Builder {
        val builder = Builder()
        builder.mConfig = this
        return builder
    }

    /**
     * Helper class to createServerEngine a VPN Service.
     */
    class Builder {
        var mConfig: NetBareConfig

        /**
         * Set the name of this session. It will be displayed in system-managed dialogs and
         * notifications. This is recommended not required.
         *
         * @param session Session name.
         * @return this [Builder] object to facilitate chaining method calls.
         */
        fun setSession(session: String?): Builder {
            mConfig.session = session
            return this
        }

        /**
         * Set the [PendingIntent] to an activity for users to configure the VPN connection.
         * If it is not set, the button to configure will not be shown in system-managed dialogs.
         *
         * @param intent An Activity intent.
         * @return this [Builder] object to facilitate chaining method calls.
         */
        fun setConfigureIntent(intent: PendingIntent?): Builder {
            mConfig.configureIntent = intent
            return this
        }

        /**
         * Set the maximum transmission unit (MTU) of the VPN interface. If it is not set, the
         * default value in the operating system will be used.
         *
         * @param mtu Maximum transmission unit (MTU).
         * @return this [Builder] object to facilitate chaining method calls.
         */
        fun setMtu(mtu: Int): Builder {
            mConfig.mtu = mtu
            return this
        }

        /**
         * Convenience method to add a network address to the VPN interface using a numeric address
         * string. See [InetAddress] for the definitions of numeric address formats.
         *
         * Adding an address implicitly allows traffic from that address family (i.e., IPv4 or IPv6)
         * to be routed over the VPN.
         *
         * @param address IPv4 or IPv6 address.
         * @return this [Builder] object to facilitate chaining method calls.
         */
        fun setAddress(address: IpAddress?): Builder {
            mConfig.address = address
            return this
        }

        /**
         * Add a network route to the VPN interface. Both IPv4 and IPv6 routes are supported.
         *
         * Adding a route implicitly allows traffic from that address family (i.e., IPv4 or IPv6)
         * to be routed over the VPN.
         *
         * @param address IPv4 or IPv6 address.
         * @return this [Builder] object to facilitate chaining method calls.
         */
        fun addRoute(address: IpAddress): Builder {
            mConfig.routes!!.add(address)
            return this
        }

        /**
         * Add a DNS server to the VPN connection. Both IPv4 and IPv6 addresses are supported.
         * If none is set, the DNS servers of the default network will be used.
         *
         * Adding a server implicitly allows traffic from that address family (i.e., IPv4 or IPv6)
         * to be routed over the VPN.
         *
         * @param address IPv4 or IPv6 address.
         * @return this [Builder] object to facilitate chaining method calls.
         */
        fun addDnsServer(address: String): Builder {
            mConfig.dnsServers!!.add(address)
            return this
        }

        /**
         * Adds an application that's allowed to access the VPN connection.
         *
         * If this method is called at least once, only applications added through this method (and
         * no others) are allowed access. Else (if this method is never called), all applications
         * are allowed by default.  If some applications are added, other, un-added applications
         * will use networking as if the VPN wasn't running.
         *
         * @param packageName The full name (e.g.: "com.google.apps.contacts") of an application.
         * @return this [Builder] object to facilitate chaining method calls.
         */
        fun addAllowedApplication(packageName: String): Builder {
            mConfig.allowedApplications!!.add(packageName)
            return this
        }

        /**
         * Adds an application that's denied access to the VPN connection.
         *
         * By default, all applications are allowed access, except for those denied through this
         * method.  Denied applications will use networking as if the VPN wasn't running.
         *
         * @param packageName The full name (e.g.: "com.google.apps.contacts") of an application.
         * @return this [Builder] object to facilitate chaining method calls.
         */
        fun addDisallowedApplication(packageName: String): Builder {
            mConfig.disallowedApplications!!.add(packageName)
            return this
        }

        /**
         * Adds an ip host or a domain host that's allowed to capture.
         *
         * @param host An ip host or a domain host, not support the domain host.
         * @return this [Builder] object to facilitate chaining method calls.
         */
        fun addAllowedHost(host: String): Builder {
            mConfig.allowedHosts!!.add(host)
            return this
        }

        /**
         * Adds an ip host or a domain host that's denied access to capture.
         *
         * @param host An ip host or a domain host, not support the domain host.
         * @return this [Builder] object to facilitate chaining method calls.
         */
        fun addDisallowedHost(host: String): Builder {
            mConfig.disallowedHosts!!.add(host)
            return this
        }

        /**
         * Set the factory of gateway, the gateway will handle some intercepted actions before the
         * server and client received the final data.
         *
         * @param gatewayFactory A factory of gateway.
         * @return this [Builder] object to facilitate chaining method calls.
         */
        fun setVirtualGatewayFactory(gatewayFactory: VirtualGatewayFactory?): Builder {
            mConfig.gatewayFactory = gatewayFactory
            return this
        }

        /**
         * Dump the uid of the session, you can get the value from [Session.uid]. This config
         * will cost much battery.
         *
         *
         * Android Q removes access to /proc/net, this config doesn't work on Android Q.
         *
         * @param dumpUid Should dump session's uid from /proc/net/
         * @return this [Builder] object to facilitate chaining method calls.
         */
        fun dumpUid(dumpUid: Boolean): Builder {
            mConfig.dumpUid = dumpUid
            return this
        }

        /**
         * Exclude all net packets of the app self, this config is associated with [.dumpUid].
         * If the config of dumpUid is false, the excludeSelf will be forced to false too.
         *
         * @param excludeSelf Should exclude all net packets of the app self.
         * @return this [Builder] object to facilitate chaining method calls.
         */
        fun excludeSelf(excludeSelf: Boolean): Builder {
            mConfig.excludeSelf = excludeSelf
            return this
        }

        /**
         * Sets an uid provider.
         *
         * @param provider This interface provides a known uid for a session.
         * @return this [Builder] object to facilitate chaining method calls.
         */
        fun setUidProvider(provider: UidProvider?): Builder {
            mConfig.uidProvider = provider
            return this
        }

        /**
         * Set a SSL KeyManager provider, NetBare will use it to initialize
         * [javax.net.ssl.SSLContext].
         *
         * If not set, the MITM server will use a self-signed root CA, the MITM client will set the
         * parameter to null when initializing [javax.net.ssl.SSLContext].
         *
         * @param provider This interface provides [javax.net.ssl.KeyManager[]]
         * @return this [Builder] object to facilitate chaining method calls.
         */
        fun setSSLKeyManagerProvider(provider: SSLKeyManagerProvider?): Builder {
            mConfig.keyManagerProvider = provider
            return this
        }

        /**
         * Set a SSL TrustManager provider, NetBare will use it to initialize
         * [javax.net.ssl.SSLContext].
         *
         * If not set, the MITM server and client will set the parameter to null when initializing
         * [javax.net.ssl.SSLContext].
         *
         * @param provider This interface provides [javax.net.ssl.TrustManager[]]
         * @return this [Builder] object to facilitate chaining method calls.
         */
        fun setSSLTrustManagerProvider(provider: SSLTrustManagerProvider?): Builder {
            mConfig.trustManagerProvider = provider
            return this
        }

        /**
         * Create the instance of [com.github.megatronking.netbare.NetBareConfig].
         *
         * @return The instance of [com.github.megatronking.netbare.NetBareConfig].
         */
        fun build(): NetBareConfig {
            return mConfig
        }

        init {
            mConfig = NetBareConfig()
            mConfig.routes = HashSet()
            mConfig.dnsServers = HashSet()
            mConfig.allowedApplications = HashSet()
            mConfig.disallowedApplications = HashSet()
            mConfig.allowedHosts = HashSet()
            mConfig.disallowedHosts = HashSet()
        }
    }

    companion object {
        /**
         * Create a default config using [HttpVirtualGatewayFactory] for HTTP protocol.
         *
         * @param jks JSK instance, not null.
         * @param interceptors A collection of [HttpInterceptorFactory].
         * @return A NetBare config instance.
         */
        fun defaultHttpConfig(
            jks: JKS,
            interceptors: List<HttpInterceptorFactory>?
        ): NetBareConfig {
            return defaultConfig().newBuilder()
                .setVirtualGatewayFactory(HttpVirtualGatewayFactory(jks, interceptors))
                .build()
        }

        /**
         * Create a default config.
         *
         * @return A NetBare config instance.
         */
        fun defaultConfig(): NetBareConfig {
            return Builder()
                .dumpUid(false)
                .setMtu(4096)
                .setAddress(IpAddress("10.1.10.1", 32))
                .setSession("NetBare")
                .addRoute(IpAddress("0.0.0.0", 0))
                .build()
        }
    }
}