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

import android.os.Build
import android.text.TextUtils
import java.io.Closeable
import java.io.IOException

/**
 * A collection of assorted utility classes.
 *
 * @author Megatron King
 * @since 2018-10-08 22:52
 */
object NetBareUtils {
    /**
     * Http line end (CRLF) symbol.
     */
    const val LINE_END = "\r\n"

    /**
     * Http line end (CRLF) regex.
     */
    const val LINE_END_REGEX = "\\r\\n"

    /**
     * A byte array of http line end (CRLF).
     */
    val LINE_END_BYTES = LINE_END.toByteArray()

    /**
     * Http double line end (CRLF), it separate the headers and body.
     */
    const val PART_END = "\r\n\r\n"

    /**
     * A byte array of double http line end (CRLF).
     */
    val PART_END_BYTES = "\r\n\r\n".toByteArray()

    /**
     * Convert a int ip value to ipv4 string.
     *
     * @param ip The ip address.
     * @return A ipv4 string value, format is N.N.N.N
     */
    fun convertIp(ip: Int): String {
        return String.format(
            "%s.%s.%s.%s", ip shr 24 and 0x00FF,
            ip shr 16 and 0x00FF, ip shr 8 and 0x00FF, ip and 0x00FF
        )
    }

    /**
     * Convert a string ip value to int.
     *
     * @param ip The ip address.
     * @return A int ip value.
     */
    fun convertIp(ip: String?): Int {
        val arrayStrings = ip!!.split("\\.").toTypedArray()
        return (arrayStrings[0].toInt() shl 24
                or (arrayStrings[1].toInt() shl 16)
                or (arrayStrings[2].toInt() shl 8)
                or arrayStrings[3].toInt())
    }

    /**
     * Convert a short ip value to int.
     *
     * @param port The port.
     * @return A int port value.
     */
    fun convertPort(port: Short): Int {
        return port.toInt() and 0xFFFF
    }

    /**
     * Closes a closeable object or release resource.
     *
     * @param closeable A closeable object like io stream.
     */
    fun closeQuietly(closeable: Closeable?) {
        if (closeable != null) {
            try {
                closeable.close()
            } catch (e: IOException) {
                NetBareLog.wtf(e)
            }
        }
    }

    /**
     * Parse a string to a integer value. If the string is not a integer value, this will return the
     * default value.
     *
     * @param string The string value.
     * @param defaultValue The default integer value.
     * @return The integer value.
     */
    fun parseInt(string: String, defaultValue: Int): Int {
        var result = defaultValue
        if (TextUtils.isEmpty(string)) {
            return result
        }
        try {
            result = string.toInt()
        } catch (e: Exception) {
            // parse error
        }
        return result
    }

    /**
     * Parse a string to a integer value with a radix. If the string is not a integer value, this
     * will return the default value.
     *
     * @param string The string value.
     * @param radix The radix to be used.
     * @param defaultValue The default integer value.
     * @return The integer value.
     */
    fun parseInt(string: String, radix: Int, defaultValue: Int): Int {
        var result = defaultValue
        if (TextUtils.isEmpty(string)) {
            return result
        }
        try {
            result = string.toInt(radix)
        } catch (e: Exception) {
            // parse error
        }
        return result
    }

    /**
     * Whether the OS build version is Android Q.
     *
     * @return True means the build android Q.
     */
    val isAndroidQ: Boolean
        get() = "Q" == Build.VERSION.RELEASE
}