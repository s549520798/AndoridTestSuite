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
package com.github.megatronking.netbare.http

/**
 * Http protocols that NetBare defined.
 *
 * @author Megatron King
 * @since 2018-10-15 19:50
 */
enum class HttpProtocol(private val protocol: String) {
    /**
     * It means NetBare does not know the protocol.
     */
    UNKNOWN("unknown"),

    /**
     * An obsolete plaintext framing that does not use persistent sockets by default.
     */
    HTTP_1_0("HTTP/1.0"),

    /**
     * A plaintext framing that includes persistent connections.
     *
     *
     * This version of OkHttp implements [RFC
 * 7230](https://tools.ietf.org/html/rfc7230), and tracks revisions to that spec.
     */
    HTTP_1_1("HTTP/1.1"),

    /**
     * Chromium's binary-framed protocol that includes header compression, multiplexing multiple
     * requests on the same socket, and server-push. HTTP/1.1 semantics are layered on SPDY/3.
     */
    SPDY_3("spdy/3.1"),

    /**
     * The IETF's binary-framed protocol that includes header compression, multiplexing multiple
     * requests on the same socket, and server-push. HTTP/1.1 semantics are layered on HTTP/2.
     */
    HTTP_2("h2"),

    /**
     * Cleartext HTTP/2 with no "upgrade" round trip. This option requires the client to have prior
     * knowledge that the server supports cleartext HTTP/2.
     *
     * @see [Starting HTTP/2 with Prior
     * Knowledge](https://tools.ietf.org/html/rfc7540.section-3.4)
     */
    H2_PRIOR_KNOWLEDGE("h2_prior_knowledge"),

    /**
     * QUIC (Quick UDP Internet Connection) is a new multiplexed and secure transport atop UDP,
     * designed from the ground up and optimized for HTTP/2 semantics.
     * HTTP/1.1 semantics are layered on HTTP/2.
     */
    QUIC("quic");

    /**
     * Returns the protocol string value rather than it's name.
     *
     * @return Protocol value.
     */
    override fun toString(): String {
        return protocol
    }

    companion object {
        /**
         * Returns the protocol identified by `protocol`.
         *
         * @param protocol A string protocol presents in request line and status line.
         * @return A HttpProtocol enum.
         */
        @NonNull
        fun parse(@NonNull protocol: String): HttpProtocol {
            return if (protocol.equals(HTTP_1_0.protocol, ignoreCase = true)) {
                HTTP_1_0
            } else if (protocol.equals(HTTP_1_1.protocol, ignoreCase = true)) {
                HTTP_1_1
            } else if (protocol.equals(H2_PRIOR_KNOWLEDGE.protocol, ignoreCase = true)) {
                H2_PRIOR_KNOWLEDGE
            } else if (protocol.equals(HTTP_2.protocol, ignoreCase = true)) {
                HTTP_2
            } else if (protocol.equals(SPDY_3.protocol, ignoreCase = true)) {
                SPDY_3
            } else if (protocol.equals(QUIC.protocol, ignoreCase = true)) {
                QUIC
            } else {
                UNKNOWN
            }
        }
    }
}