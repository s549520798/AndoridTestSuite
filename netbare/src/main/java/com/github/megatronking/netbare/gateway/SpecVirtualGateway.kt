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
package com.github.megatronking.netbare.gateway

import com.github.megatronking.netbare.ip.Protocol
import com.github.megatronking.netbare.net.Session
import java.io.IOException
import java.nio.ByteBuffer

/**
 * The spec VirtualGateway filter the net packets by [Protocol].
 *
 * @author Megatron King
 * @since 2018-11-03 10:34
 */
abstract class SpecVirtualGateway(
    protocol: Protocol, session: Session, request: Request,
    response: Response
) : VirtualGateway(session, request, response) {
    private val mIsSpec: Boolean

    /**
     * The specific protocol packets sent to server will flow through this method.
     *
     * @param buffer A byte buffer contains the net packet data.
     * @throws IOException If an I/O error has occurred.
     */
    @Throws(IOException::class)
    protected abstract fun onSpecRequest(buffer: ByteBuffer?)

    /**
     * The specific protocol packets sent to client will flow through this method.
     *
     * @param buffer A byte buffer contains the net packet data.
     * @throws IOException If an I/O error has occurred.
     */
    @Throws(IOException::class)
    protected abstract fun onSpecResponse(buffer: ByteBuffer?)

    /**
     * Notify virtual gateway that no longer has data sent to the server.
     */
    protected abstract fun onSpecRequestFinished()

    /**
     * Notify virtual gateway that no longer has data sent to the client.
     */
    protected abstract fun onSpecResponseFinished()
    @Throws(IOException::class)
    override fun onRequest(buffer: ByteBuffer?) {
        if (mIsSpec) {
            onSpecRequest(buffer)
        } else {
            super.onRequest(buffer)
        }
    }

    @Throws(IOException::class)
    override fun onResponse(buffer: ByteBuffer?) {
        if (mIsSpec) {
            onSpecResponse(buffer)
        } else {
            super.onResponse(buffer)
        }
    }

    override fun onRequestFinished() {
        if (mIsSpec) {
            onSpecRequestFinished()
        } else {
            super.onRequestFinished()
        }
    }

    override fun onResponseFinished() {
        if (mIsSpec) {
            onSpecResponseFinished()
        } else {
            super.onResponseFinished()
        }
    }

    init {
        mIsSpec = protocol == session.protocol
    }
}