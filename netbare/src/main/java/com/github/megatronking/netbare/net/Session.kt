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

import com.github.megatronking.netbare.ip.Protocol
import java.util.*

/**
 * This object represents a network session, it contains IPs, ports and IP packet details.
 *
 * @author Megatron King
 * @since 2018-10-14 23:39
 */
class Session internal constructor(
    /**
     * IP protocol.
     */
    val protocol: Protocol,
    /**
     * Local vpn port.
     */
    val localPort: Short,
    /**
     * Remote server port.
     */
    val remotePort: Short,
    /**
     * Remote server IP.
     */
    val remoteIp: Int
) {
    /**
     * An unique id uses to identify this session.
     */
    var id: String

    /**
     * Session started time.
     */
    var time: Long

    /**
     * Remote server host.
     */
    var host: String? = null

    /**
     * The process id that the session belongs to.
     */
    var uid = 0

    /**
     * Packet counts.
     */
    var packetIndex = 0

    /**
     * The total size of the packets that sends to remote server.
     */
    var sendDataSize = 0

    /**
     * The total size of the packets that received from remote server.
     */
    var receiveDataSize = 0

    /* package */
    init {
        id = UUID.randomUUID().toString()
        time = System.currentTimeMillis()
    }
}