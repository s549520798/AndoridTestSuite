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
package com.github.megatronking.netbare.ssl

import com.github.megatronking.netbare.NetBareLog
import com.github.megatronking.netbare.http.HttpProtocol
import java.nio.ByteBuffer
import java.util.*

/**
 * A SSL utils class.
 *
 * @author Megatron King
 * @since 2018-11-14 11:38
 */
object SSLUtils {
    /**
     * Change cipher spec.
     */
    const val SSL_CONTENT_TYPE_CHANGE_CIPHER_SPEC = 20

    /**
     * Alert.
     */
    const val SSL_CONTENT_TYPE_ALERT = 21

    /**
     * Handshake.
     */
    const val SSL_CONTENT_TYPE_HANDSHAKE = 22

    /**
     * Application data.
     */
    const val SSL_CONTENT_TYPE_APPLICATION_DATA = 23

    /**
     * HeartBeat Extension.
     */
    const val SSL_CONTENT_TYPE_EXTENSION_HEARTBEAT = 24

    /**
     * The length of the ssl record header (in bytes).
     */
    private const val SSL_RECORD_HEADER_LENGTH = 5

    /**
     * Packet length is not enough to determine.
     */
    const val PACKET_NOT_ENOUGH = 1

    /**
     * It is a plaintext packet.
     */
    const val PACKET_NOT_ENCRYPTED = 2

    /**
     * It is a valid SSL packet.
     */
    const val PACKET_SSL = 3

    /**
     * Verify a packet to see whether it is a valid SSL packet.
     *
     * @param buffer Encrypted SSL packet.
     * @return Verification result, one of [.PACKET_NOT_ENOUGH], [.PACKET_NOT_ENCRYPTED],
     * and [.PACKET_SSL].
     */
    fun verifyPacket(buffer: ByteBuffer?): Int {
        val position = buffer!!.position()
        // Get the packet length and wait until we get a packets worth of data to unwrap.
        if (buffer.remaining() < SSL_RECORD_HEADER_LENGTH) {
            NetBareLog.w("No enough ssl/tls packet length: " + buffer.remaining())
            return PACKET_NOT_ENOUGH
        }
        var packetLength = 0
        // SSLv3 or TLS - Check ContentType
        var tls: Boolean
        tls = when (unsignedByte(buffer, position)) {
            SSL_CONTENT_TYPE_CHANGE_CIPHER_SPEC, SSL_CONTENT_TYPE_ALERT, SSL_CONTENT_TYPE_HANDSHAKE, SSL_CONTENT_TYPE_APPLICATION_DATA, SSL_CONTENT_TYPE_EXTENSION_HEARTBEAT -> true
            else ->                 // SSLv2 or bad data
                false
        }
        if (tls) {
            // SSLv3 or TLS - Check ProtocolVersion
            val majorVersion = unsignedByte(buffer, position + 1)
            if (majorVersion == 3) {
                // SSLv3 or TLS
                packetLength = unsignedShort(buffer, position + 3) + SSL_RECORD_HEADER_LENGTH
                if (packetLength <= SSL_RECORD_HEADER_LENGTH) {
                    // Neither SSLv3 or TLSv1 (i.e. SSLv2 or bad data)
                    tls = false
                }
            } else {
                // Neither SSLv3 or TLSv1 (i.e. SSLv2 or bad data)
                tls = false
            }
        }
        if (!tls) {
            // SSLv2 or bad data - Check the version
            val headerLength = if (unsignedByte(buffer, position) and 0x80 != 0) 2 else 3
            val majorVersion = unsignedByte(buffer, position + headerLength + 1)
            if (majorVersion == 2 || majorVersion == 3) {
                // SSLv2
                packetLength =
                    if (headerLength == 2) (buffer.getShort(position).toInt() and 0x7FFF) + 2 else (buffer.getShort(
                        position
                    ).toInt() and 0x3FFF) + 3
                if (packetLength <= headerLength) {
                    NetBareLog.w(
                        "No enough ssl/tls packet length, packet: " + packetLength +
                                " header: " + headerLength
                    )
                    // No enough data.
                    return PACKET_NOT_ENOUGH
                }
            } else {
                // Not encrypted
                return PACKET_NOT_ENCRYPTED
            }
        }
        // Decode SSL data.
        if (packetLength > buffer.remaining()) {
            NetBareLog.w(
                "No enough ssl/tls packet length, packet: " + packetLength +
                        " actual: " + buffer.remaining()
            )
            // Wait until the whole packet can be read.
            return PACKET_NOT_ENOUGH
        }
        return PACKET_SSL
    }

    fun parseClientHelloAlpn(clienthelloMessage: ByteBuffer?): Array<HttpProtocol?>? {
        val buffer = clienthelloMessage!!.array()
        var offset = clienthelloMessage.position()
        val size = clienthelloMessage.remaining()
        val limit = offset + size
        // Client Hello
        if (size <= 43 || buffer[offset].toInt() != 0x16) {
            return null
        }
        // Skip 43 byte header
        offset += 43
        // Read sessionID
        if (offset + 1 > limit) {
            return null
        }
        val sessionIDLength: Int = buffer[offset++].toInt() and 0xFF
        offset += sessionIDLength

        // Read cipher suites
        if (offset + 2 > limit) {
            return null
        }
        val cipherSuitesLength: Int = readShort(buffer, offset).toInt() and 0xFFFF
        offset += 2
        offset += cipherSuitesLength

        // Read Compression method.
        if (offset + 1 > limit) {
            return null
        }
        val compressionMethodLength: Int = buffer[offset++].toInt() and 0xFF
        offset += compressionMethodLength

        // Read Extensions
        if (offset + 2 > limit) {
            return null
        }
        val extensionsLength: Int = readShort(buffer, offset).toInt() and 0xFFFF
        offset += 2
        if (offset + extensionsLength > limit) {
            return null
        }
        while (offset + 4 <= limit) {
            val type: Int = readShort(buffer, offset).toInt() and 0xFFFF
            offset += 2
            var length: Int = readShort(buffer, offset).toInt() and 0xFFFF
            offset += 2
            // TYPE_APPLICATION_LAYER_PROTOCOL_NEGOTIATION = 16
            if (type == 16) {
                val protocolCount: Int = readShort(buffer, offset).toInt() and 0xFFFF
                offset += 2
                length -= 2
                if (offset + length > limit) {
                    return null
                }
                val httpProtocols: MutableList<HttpProtocol> = ArrayList()
                var read = 0
                while (read <= protocolCount) {
                    val protocolLength: Int = buffer[offset + read].toInt()
                    read += 1
                    if (protocolLength < 0 || offset + read + protocolLength > buffer.size) {
                        return null
                    }
                    val protocol: HttpProtocol = HttpProtocol.Companion.parse(
                        String(
                            buffer, offset + read,
                            protocolLength
                        )
                    )
                    if (protocol == HttpProtocol.HTTP_1_1 || protocol == HttpProtocol.HTTP_2) {
                        httpProtocols.add(protocol)
                    }
                    read += protocolLength
                }
                return if (httpProtocols.isEmpty()) {
                    null
                } else {
                    val protocols = arrayOfNulls<HttpProtocol>(httpProtocols.size)
                    for (i in protocols.indices) {
                        protocols[i] = httpProtocols[i]
                    }
                    protocols
                }
            } else {
                offset += length
            }
        }
        return null
    }

    private fun unsignedByte(buffer: ByteBuffer?, index: Int): Int {
        return buffer!![index].toInt() and 0x0FF
    }

    private fun unsignedShort(buffer: ByteBuffer?, index: Int): Int {
        return buffer!!.getShort(index).toInt() and 0x0FFFF
    }

    private fun readShort(data: ByteArray, offset: Int): Short {
        val r: Int = data[offset].toInt() and 0xFF shl 8 or (data[offset + 1].toInt() and 0xFF)
        return r.toShort()
    }
}