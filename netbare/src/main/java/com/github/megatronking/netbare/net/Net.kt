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

/**
 * A dumped net info class contains IPs, ports and uid.
 *
 * @author Megatron King
 * @since 2018-12-01 22:33
 */
class Net  /* package */ internal constructor(
    /**
     * The identifier of a process's uid.
     */
    var uid: Int,
    /**
     * The local IP.
     */
    var localIp: String,
    /**
     * The local port.
     */
    var localPort: Int,
    /**
     * The remote server IP.
     */
    var remoteIp: String?,
    /**
     * The remote server port.
     */
    var remotePort: Int
) {
    override fun toString(): String {
        return "$uid $localIp:$localPort -> $remoteIp:$remotePort"
    }
}