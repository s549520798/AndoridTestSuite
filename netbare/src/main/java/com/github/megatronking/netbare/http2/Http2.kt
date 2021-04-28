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
package com.github.megatronking.netbare.http2

/**
 * HTTP2 protocol constants and common methods.
 *
 * See https://httpwg.org/specs/rfc7540.html
 *
 * @author Megatron King
 * @since 2019/1/5 14:14
 */
object Http2 {
    /**
     * In HTTP/2, each endpoint is required to send a connection preface as a final confirmation of
     * the protocol in use and to establish the initial settings for the HTTP/2 connection.
     */
    val CONNECTION_PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".toByteArray()
    const val FRAME_HEADER_LENGTH = 9

    /**
     * The initial max frame size, applied independently writing to, or reading from the peer.
     *
     * 0x4000 = 2^14 = 16384
     */
    const val INITIAL_MAX_FRAME_SIZE = 0x4000
    const val FLAG_NONE: Byte = 0x0

    /**
     * Used for settings and ping.
     */
    const val FLAG_ACK: Byte = 0x1

    /**
     * Used for headers and data.
     */
    const val FLAG_END_STREAM: Byte = 0x1

    /**
     * Used for headers and continuation.
     */
    const val FLAG_END_HEADERS: Byte = 0x4
    const val FLAG_END_PUSH_PROMISE: Byte = 0x4

    /**
     * Used for headers and data.
     */
    const val FLAG_PADDED: Byte = 0x8

    /**
     * Used for headers.
     */
    const val FLAG_PRIORITY: Byte = 0x20

    /**
     * Used for data.
     */
    const val FLAG_COMPRESSED: Byte = 0x20
}