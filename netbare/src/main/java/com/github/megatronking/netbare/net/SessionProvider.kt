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
package com.github.megatronking.netbare.net

import com.github.megatronking.netbare.ip.*
import java.util.concurrent.ConcurrentHashMap

/**
 * A session provider that provides the session instance query services.
 *
 * @author Megatron King
 * @since 2018-10-15 21:46
 */
class SessionProvider(dumper: UidDumper?) {
    private val mSessions: MutableMap<Short, Session>
    private val mDumper: UidDumper?

    /**
     * Query a session by local VPN port.
     *
     * @param localPort The local VPN port.
     * @return The instance of [Session] if it exists, or null.
     */
    fun query(localPort: Short): Session? {
        val session = mSessions[localPort]
        if (mDumper != null && session != null && session.uid == 0) {
            // Query uid again.
            mDumper.request(session)
        }
        return session
    }

    /**
     * Query or create a session by protocol, ports and remote server IP.
     *
     * @param protocol IP protocol.
     * @param localPort Local VPN port.
     * @param remotePort Remote server port.
     * @param remoteIp Remote server IP.
     * @return An instance of [Session], if the instance not exists, will create a new one.
     */
    fun ensureQuery(
        protocol: Protocol,
        localPort: Short,
        remotePort: Short,
        remoteIp: Int
    ): Session {
        var session = mSessions[localPort]
        if (session != null) {
            if (session.protocol != protocol || session.localPort != localPort || session.remotePort != remotePort || session.remoteIp != remoteIp) {
                session = null
            }
        }
        if (session == null) {
            session = Session(protocol, localPort, remotePort, remoteIp)
            mSessions[localPort] = session
            // Dump uid from /proc/net/
            mDumper?.request(session)
        }
        return session
    }

    companion object {
        private const val MAX_SESSION = 100
    }

    /**
     * Constructs a session provider with a [UidDumper].
     *
     * @param dumper Use to dump uid, can be null.
     */
    init {
        mSessions = ConcurrentHashMap(MAX_SESSION)
        mDumper = dumper
    }
}