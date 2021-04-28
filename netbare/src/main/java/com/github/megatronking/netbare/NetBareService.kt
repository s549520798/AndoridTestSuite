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

import android.app.Notification
import android.content.Intent
import android.net.VpnService
import com.github.megatronking.netbare.ssl.SSLEngineFactory

/**
 * Base class for NetBare services.
 *
 *
 * NetBare service is an implement of [VpnService], it establishes a vpn connection to
 * route incoming and outgoing net packets. The NetBare service are forced to display a notification
 * due to intercepting packets raises huge security concerns.
 *
 * <P>
 * The NetBare service is managed by [com.github.megatronking.netbare.NetBare], and you can use [NetBareListener] to
 * observe the state.
</P> *
 *
 * @author Megatron King
 * @since 2018-10-08 21:09
 */
abstract class NetBareService : VpnService() {
    /**
     * The identifier for this notification as per
     * [NotificationManager.notify]; must not be 0.
     *
     * @return The identifier
     */
    protected abstract fun notificationId(): Int

    /**
     * A [Notification] object describing what to show the user. Must not be null.
     *
     * @return The Notification to be displayed.
     */
    protected abstract fun createNotification(): Notification?
    private var mNetBareThread: NetBareThread? = null
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent == null) {
            return START_NOT_STICKY
        }
        val action = intent.action
        if (ACTION_START == action) {
            startNetBare()
            startForeground(notificationId(), createNotification())
        } else if (ACTION_STOP == action) {
            stopNetBare()
            stopForeground(true)
            stopSelf()
        } else {
            stopSelf()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopNetBare()
        stopForeground(true)
    }

    private fun startNetBare() {
        // Terminate previous service.
        stopNetBare()
        val config: NetBareConfig = NetBare.get().config
            ?: throw IllegalArgumentException(
                "Must start NetBareService with a " +
                        "NetBareConfig"
            )
        NetBareLog.i("Start NetBare service!")
        SSLEngineFactory.Companion.updateProviders(
            config.keyManagerProvider,
            config.trustManagerProvider
        )
        mNetBareThread = NetBareThread(this, config)
        mNetBareThread!!.start()
    }

    private fun stopNetBare() {
        if (mNetBareThread == null) {
            return
        }
        NetBareLog.i("Stop NetBare service!")
        mNetBareThread!!.interrupt()
        mNetBareThread = null
    }

    companion object {
        /**
         * Start capturing target app's net packets.
         */
        const val ACTION_START = "com.github.megatronking.netbare.action.Start"

        /**
         * Stop capturing target app's net packets.
         */
        const val ACTION_STOP = "com.github.megatronking.netbare.action.Stop"
    }
}