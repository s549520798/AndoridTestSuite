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
import java.io.IOException

/**
 * An abstract base class defined for proxy servers. The local proxy server runs a separated thread
 * and loop to process packets. The sub class needs to impl [.process] to handle the packets.
 *
 * @author Megatron King
 * @since 2018-10-10 00:31
 */
/* package */
internal abstract class BaseProxyServer(serverName: String?) : ProxyServer(), Runnable {
    /**
     * Waiting the specific protocol packets and trying to sent to real remote server.
     *
     * @throws IOException If an I/O error has occurred.
     */
    @Throws(IOException::class)
    protected abstract fun process()
    private var mIsRunning = false
    private val mServerThread: Thread
    public override fun startServer() {
        mIsRunning = true
        mServerThread.start()
    }

    public override fun stopServer() {
        mIsRunning = false
        mServerThread.interrupt()
    }

    override fun run() {
        while (mIsRunning) {
            try {
                process()
            } catch (e: IOException) {
                NetBareLog.e(e.message)
            }
        }
    }

    /* package */
    val isStopped: Boolean
        get() = !mIsRunning

    /* package */
    init {
        mServerThread = Thread(this, serverName)
    }
}