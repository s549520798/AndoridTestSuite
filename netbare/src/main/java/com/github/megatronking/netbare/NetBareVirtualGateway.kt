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

import android.os.Process
import com.github.megatronking.netbare.gateway.*
import com.github.megatronking.netbare.ip.*
import com.github.megatronking.netbare.net.Session
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*

/**
 * The main virtual gateway used in proxy servers, it wraps the actual virtual gateway. We use this
 * class to do some internal verifications.
 *
 * @author Megatron King
 * @since 2018-11-17 23:10
 */
class NetBareVirtualGateway(session: Session, request: Request, response: Response) :
    VirtualGateway(session, request, response) {
    private val mGateway: VirtualGateway
    private val mSession: Session
    private val mLog: NetBareXLog
    private var mPolicy = 0
    private var mRequestFinished = false
    private var mResponseFinished = false
    @Throws(IOException::class)
    override fun onRequest(buffer: ByteBuffer?) {
        if (mRequestFinished) {
            mLog.w("Drop a buffer due to request has finished.")
            return
        }
        resolvePolicyIfNecessary(buffer)
        if (mPolicy == POLICY_ALLOWED) {
            mGateway.onRequest(buffer)
        } else if (mPolicy == POLICY_DISALLOWED) {
            super.onRequest(buffer)
        }
    }

    @Throws(IOException::class)
    override fun onResponse(buffer: ByteBuffer?) {
        if (mResponseFinished) {
            mLog.w("Drop a buffer due to response has finished.")
            return
        }
        resolvePolicyIfNecessary(buffer)
        if (mPolicy == POLICY_ALLOWED) {
            mGateway.onResponse(buffer)
        } else if (mPolicy == POLICY_DISALLOWED) {
            super.onResponse(buffer)
        }
    }

    override fun onRequestFinished() {
        if (mRequestFinished) {
            return
        }
        mLog.i("Gateway request finished!")
        mRequestFinished = true
        if (mPolicy == POLICY_ALLOWED) {
            mGateway.onRequestFinished()
        } else if (mPolicy == POLICY_DISALLOWED) {
            super.onRequestFinished()
        }
    }

    override fun onResponseFinished() {
        if (mResponseFinished) {
            return
        }
        mLog.i("Gateway response finished!")
        mResponseFinished = true
        if (mPolicy == POLICY_ALLOWED) {
            mGateway.onResponseFinished()
        } else if (mPolicy == POLICY_DISALLOWED) {
            super.onResponseFinished()
        }
    }

    private fun resolvePolicyIfNecessary(buffer: ByteBuffer?) {
        if (mPolicy != POLICY_INDETERMINATE) {
            // Resolved.
            return
        }
        if (!buffer!!.hasRemaining()) {
            // Invalid buffer remaining, do nothing.
            return
        }
        if (mSession.protocol != Protocol.TCP) {
            mPolicy = POLICY_ALLOWED
            return
        }

        // Now we verify the TCP protocol host
        val domain: String?
        domain = if (isHttp(buffer)) {
            parseHttpHost(buffer.array(), buffer.position(), buffer.remaining())
        } else {
            parseHttpsHost(buffer.array(), buffer.position(), buffer.remaining())
        }
        if (domain == null) {
            // Maybe not http protocol.
            mPolicy = POLICY_ALLOWED
            return
        } else {
            mSession.host = domain
        }
        val config: NetBareConfig = NetBare.get().config
        val allowedHost: Set<String?> = HashSet(
            config.allowedHosts
        )
        val disallowedHost: Set<String?> = HashSet(
            config.disallowedHosts
        )
        val isAllowedHostEmpty = allowedHost.isEmpty()
        val isDisallowedHostEmpty = disallowedHost.isEmpty()
        if (isAllowedHostEmpty && isDisallowedHostEmpty) {
            // No white and black list, it means allow everything.
            mPolicy = POLICY_ALLOWED
            return
        }
        if (!isDisallowedHostEmpty) {
            // Check domain hosts.
            for (host in disallowedHost) {
                if (host == domain) {
                    // Denied host.
                    mPolicy = POLICY_DISALLOWED
                    return
                }
            }
            // Check ip hosts.
            for (host in disallowedHost) {
                if (host == NetBareUtils.convertIp(mSession.remoteIp)) {
                    // Denied host.
                    mPolicy = POLICY_DISALLOWED
                    return
                }
            }
        }
        if (!isAllowedHostEmpty) {
            for (host in allowedHost) {
                if (host == domain) {
                    mPolicy = POLICY_ALLOWED
                    return
                }
            }
            for (host in allowedHost) {
                if (host == NetBareUtils.convertIp(mSession.remoteIp)) {
                    mPolicy = POLICY_ALLOWED
                    return
                }
            }
            mPolicy = POLICY_DISALLOWED
        } else {
            mPolicy = POLICY_ALLOWED
        }
    }

    private fun isHttp(buffer: ByteBuffer?): Boolean {
        when (buffer!![buffer.position()]) {
            'G'.toByte(), 'H'.toByte(), 'P'.toByte(), 'D'.toByte(), 'O'.toByte(), 'T'.toByte(), 'C'.toByte() -> return true
            else -> {
            }
        }
        return false
    }

    private fun parseHttpHost(buffer: ByteArray, offset: Int, size: Int): String? {
        val header = String(buffer, offset, size)
        val headers = header.split(NetBareUtils.LINE_END_REGEX).toTypedArray()
        if (headers.size <= 1) {
            return null
        }
        for (i in 1 until headers.size) {
            val requestHeader = headers[i]
            // Reach the header end
            if (requestHeader.isEmpty()) {
                return null
            }
            val nameValue = requestHeader.split(":").toTypedArray()
            if (nameValue.size < 2) {
                return null
            }
            val name = nameValue[0].trim { it <= ' ' }
            val value =
                requestHeader.replaceFirst(nameValue[0] + ": ".toRegex(), "").trim { it <= ' ' }
            if (name.toLowerCase() == "host") {
                return value
            }
        }
        return null
    }

    private fun parseHttpsHost(buffer: ByteArray, offset: Int, size: Int): String? {
        var offset = offset
        val limit = offset + size
        // Client Hello
        if (size <= 43 || buffer[offset] .toInt()!= 0x16) {
            mLog.w("Failed to get host from SNI: Bad ssl packet.")
            return null
        }
        // Skip 43 byte header
        offset += 43

        // Read sessionID
        if (offset + 1 > limit) {
            mLog.w("Failed to get host from SNI: No session id.")
            return null
        }
        val sessionIDLength: Int = buffer[offset++].toInt() and 0xFF
        offset += sessionIDLength

        // Read cipher suites
        if (offset + 2 > limit) {
            mLog.w("Failed to get host from SNI: No cipher suites.")
            return null
        }
        val cipherSuitesLength: Int = readShort(buffer, offset).toInt() and 0xFFFF
        offset += 2
        offset += cipherSuitesLength

        // Read Compression method.
        if (offset + 1 > limit) {
            mLog.w("Failed to get host from SNI: No compression method.")
            return null
        }
        val compressionMethodLength: Int = buffer[offset++].toInt() and 0xFF
        offset += compressionMethodLength

        // Read Extensions
        if (offset + 2 > limit) {
            mLog.w("Failed to get host from SNI: no extensions.")
            return null
        }
        val extensionsLength: Int = readShort(buffer, offset).toInt() and 0xFFFF
        offset += 2
        if (offset + extensionsLength > limit) {
            mLog.w("Failed to get host from SNI: no sni.")
            return null
        }
        while (offset + 4 <= limit) {
            val type0: Int = buffer[offset++].toInt() and 0xFF
            val type1: Int = buffer[offset++].toInt() and 0xFF
            var length: Int = readShort(buffer, offset).toInt() and 0xFFFF
            offset += 2
            // Got the SNI info
            if (type0 == 0x00 && type1 == 0x00 && length > 5) {
                offset += 5
                length -= 5
                return if (offset + length > limit) {
                    null
                } else String(buffer, offset, length)
            } else {
                offset += length
            }
        }
        mLog.w("Failed to get host from SNI: no host.")
        return null
    }

    private fun readShort(data: ByteArray, offset: Int): Short {
        val r: Int = data[offset].toInt() and 0xFF shl 8 or (data[offset + 1].toInt() and 0xFF)
        return r.toShort()
    }

    companion object {
        /**
         * Policy is indeterminate, we should resolve the policy before process data.
         */
        private const val POLICY_INDETERMINATE = 0

        /**
         * This policy allows data flow to configured virtual gateway.
         */
        private const val POLICY_ALLOWED = 1

        /**
         * This policy doesn't allow data flow to configured virtual gateway.
         */
        private const val POLICY_DISALLOWED = 2
    }

    init {
        mGateway = NetBare.get().gatewayFactory.create(session, request, response)
        mSession = session
        mLog = NetBareXLog(session)
        val config: NetBareConfig = NetBare.get().config
        mPolicy =
            if (config.excludeSelf && session.uid == Process.myUid()) {
                // Exclude the app itself.
                mLog.w("Exclude an app-self connection!")
                POLICY_DISALLOWED
            } else {
                POLICY_INDETERMINATE
            }
    }
}