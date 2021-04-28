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
package com.github.megatronking.netbare.tunnel

import com.github.megatronking.netbare.gateway.VirtualGateway
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress

/**
 * A tunnel uses [VirtualGateway] to intercept and filter net packets.
 *
 * @author Megatron King
 * @since 2018-11-21 09:00
 */
abstract class VirtualGatewayTunnel : Closeable {
    /**
     * Connects to the remote server by the given server address.
     *
     * @param address The server IP socket address.
     * @throws IOException If an I/O error has occurred.
     */
    @Throws(IOException::class)
    abstract fun connect(address: InetSocketAddress?)

    /**
     * Returns the [VirtualGateway] this tunnel created.
     *
     * @return The [VirtualGateway] instance.
     */
    abstract val gateway: VirtualGateway
}