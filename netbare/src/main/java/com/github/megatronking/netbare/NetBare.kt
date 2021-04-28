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

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.github.megatronking.netbare.gateway.DefaultVirtualGateway
import com.github.megatronking.netbare.gateway.DefaultVirtualGatewayFactory
import com.github.megatronking.netbare.gateway.VirtualGatewayFactory
import java.util.*

/**
 * NetBare is a single instance, we can use this class to config and manage the NetBare service.
 * The NetBare service is an implement class of [VpnService], before starting this service,
 * should call [.prepare] to check the vpn state.
 *
 * Start and stop the NetBare service:
 * <pre>
 * `
 * NetBare.get().start(config);
 * NetBare.get().stop();
` *
</pre> *
 *
 * @author Megatron King
 * @since 2018-10-07 09:28
 */
class NetBare private constructor() {
    private object Holder {
        val INSTANCE = NetBare()
    }

    private val mListeners: MutableSet<NetBareListener>
    private lateinit var mApp: Application
    /* package */lateinit var config: NetBareConfig
        private set

    /**
     * Whether the NetBare service is alive or not.
     *
     * @return True if the service is alive, false otherwise.
     */
    var isActive = false
        private set

    /**
     * Attach an application instance to NetBare. We recommend you to call this method in your
     * [Application] class.
     *
     * @param application The application instance.
     * @param debug Should print logs in console.
     * @return The single instance of NetBare.
     */
    fun attachApplication(application: Application, debug: Boolean): NetBare {
        mApp = application
        NetBareLog.setDebug(debug)
        return this
    }

    /**
     * Prepare to establish a VPN connection. This method returns `null` if the VPN
     * application is already prepared or if the user has previously consented to the VPN
     * application. Otherwise, it returns an [Intent] to a system activity. The application
     * should launch the activity using [Activity.startActivityForResult] to get itself
     * prepared.
     *
     * @return The intent to call using [Activity.startActivityForResult].
     */
    fun prepare(): Intent {
        return VpnService.prepare(mApp)
    }

    /**
     * Start the NetBare service with your specific configuration. If the service is started,
     * [NetBareListener.onServiceStarted] will be invoked.
     *
     * @param config The configuration for NetBare service.
     */
    fun start(config: NetBareConfig) {
        if (config.mtu <= 0) {
            throw RuntimeException("Must set mtu in NetBareConfig")
        }
        if (config.address == null) {
            throw RuntimeException("Must set address in NetBareConfig")
        }
        this.config = config
        val intent = Intent(NetBareService.ACTION_START)
        intent.setPackage(mApp.packageName)
        ContextCompat.startForegroundService(mApp, intent)
    }

    /**
     * Stop the NetBare service. If the service is started,
     * [NetBareListener.onServiceStopped] will be invoked.
     */
    fun stop() {
        val intent = Intent(NetBareService.ACTION_STOP)
        intent.setPackage(mApp.packageName)
        mApp.startService(intent)
    }

    /**
     * Register a callback to be invoked when the service state changes.
     *
     * @param listener The callback to register.
     */
    fun registerNetBareListener(listener: NetBareListener) {
        mListeners.add(listener)
    }

    /**
     * Remove a previously installed service state callback.
     *
     * @param listener The callback to remove.
     */
    fun unregisterNetBareListener(listener: NetBareListener) {
        mListeners.remove(listener)
    }// Make sure the virtual gateway not be null.

    /* package */
    val gatewayFactory: VirtualGatewayFactory
        get() =// Make sure the virtual gateway not be null.
            config.gatewayFactory ?: DefaultVirtualGatewayFactory.create()

    /* package */
    fun notifyServiceStarted() {
        isActive = true
        for (listener in LinkedHashSet(mListeners)) {
            listener.onServiceStarted()
        }
    }

    /* package */
    fun notifyServiceStopped() {
        isActive = false
        for (listener in LinkedHashSet(mListeners)) {
            listener.onServiceStopped()
        }
    }

    companion object {
        fun get(): NetBare {
            return Holder.INSTANCE
        }
    }

    init {
        mListeners = LinkedHashSet()
    }
}