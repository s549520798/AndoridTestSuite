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

import android.text.TextUtils
import android.util.ArrayMap
import com.github.megatronking.netbare.NetBareConfig
import com.github.megatronking.netbare.NetBareUtils
import com.github.megatronking.netbare.ip.*
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import java.io.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * A dumper analyzes /proc/net/ files to dump uid of the network session. This class may be a
 * battery-killer, but can set [NetBareConfig.Builder.dumpUid] to false to close the dumper.
 *
 * @author Megatron King
 * @since 2018-12-03 16:54
 */
class UidDumper(localIp: String?, private val mUidProvider: UidProvider?) {
    private val mNetCaches: Cache<Int, Net>
    private val mDumpers: ArrayMap<Protocol?, Array<NetDumper>>
    fun request(session: Session) {
        if (mUidProvider != null) {
            val uid = mUidProvider.uid(session)
            if (uid != UidProvider.Companion.UID_UNKNOWN) {
                session.uid = uid
                return
            }
        }
        // Android Q abandons the access permission.
        if (NetBareUtils.isAndroidQ) {
            return
        }
        val port = NetBareUtils.convertPort(session.localPort)
        try {
            val net = mNetCaches[session.remoteIp, Callable {
                val dumpers = mDumpers[session.protocol] ?: throw Exception()
                for (dumper in dumpers) {
                    val net = dumper.dump(port)
                    if (net != null) {
                        return@Callable net
                    }
                }
                throw Exception()
            }]
            if (net != null) {
                session.uid = net.uid
            }
        } catch (e: ExecutionException) {
            // Not find the uid
        }
    }

    private class NetDumper(
        private val mArgs: String,
        private val mLocalIp: String?,
        private val mPattern: Pattern
    ) {
        fun dump(port: Int): Net? {
            var `is`: InputStream? = null
            var reader: BufferedReader? = null
            try {
                `is` = FileInputStream(mArgs)
                reader = BufferedReader(InputStreamReader(`is`))
                val now = System.currentTimeMillis()
                while (System.currentTimeMillis() - now < MAX_DUMP_DURATION) {
                    var line: String?
                    line = try {
                        reader.readLine()
                    } catch (e: IOException) {
                        continue
                    }
                    if (line == null || TextUtils.isEmpty(line.trim { it <= ' ' })) {
                        continue
                    }
                    val matcher = mPattern.matcher(line)
                    while (matcher.find()) {
                        val uid = NetBareUtils.parseInt(matcher.group(6), -1)
                        if (uid <= 0) {
                            continue
                        }
                        val localPort = parsePort(matcher.group(2))
                        if (localPort != port) {
                            continue
                        }
                        val localIp = parseIp(matcher.group(1))
                        if (localIp == null || localIp != mLocalIp) {
                            continue
                        }
                        val remoteIp = parseIp(matcher.group(3))
                        val remotePort = parsePort(matcher.group(4))
                        return Net(uid, localIp, localPort, remoteIp, remotePort)
                    }
                }
            } catch (e: IOException) {
                // Ignore
            } finally {
                NetBareUtils.closeQuietly(`is`)
                NetBareUtils.closeQuietly(reader)
            }
            return null
        }

        private fun parseIp(ip: String): String? {
            var ip = ip
            ip = ip.substring(ip.length - 8)
            val ip1 = NetBareUtils.parseInt(ip.substring(6, 8), 16, -1)
            val ip2 = NetBareUtils.parseInt(ip.substring(4, 6), 16, -1)
            val ip3 = NetBareUtils.parseInt(ip.substring(2, 4), 16, -1)
            val ip4 = NetBareUtils.parseInt(ip.substring(0, 2), 16, -1)
            return if (ip1 < 0 || ip2 < 0 || ip3 < 0 || ip4 < 0) {
                null
            } else "$ip1.$ip2.$ip3.$ip4"
        }

        private fun parsePort(port: String): Int {
            return NetBareUtils.parseInt(port, 16, -1)
        }

        companion object {
            private const val MAX_DUMP_DURATION: Long = 100
        }
    }

    companion object {
        private const val NET_ALIVE_SECONDS = 15
        private const val NET_CONCURRENCY_LEVEL = 6
        private const val NET_MAX_SIZE = 100
        private val IPV4_PATTERN = Pattern.compile(
            "\\s+\\d+:\\s([0-9A-F]{8}):" +
                    "([0-9A-F]{4})\\s([0-9A-F]{8}):([0-9A-F]{4})\\s([0-9A-F]{2})\\s[0-9A-F]{8}:[0-9A-F]{8}" +
                    "\\s[0-9A-F]{2}:[0-9A-F]{8}\\s[0-9A-F]{8}\\s+([0-9A-F]+)",
            Pattern.CASE_INSENSITIVE
                    or Pattern.UNIX_LINES
        )
        private val IPV6_PATTERN = Pattern.compile(
            "\\s+\\d+:\\s([0-9A-F]{32}):" +
                    "([0-9A-F]{4})\\s([0-9A-F]{32}):([0-9A-F]{4})\\s([0-9A-F]{2})\\s[0-9A-F]{8}:[0-9A-F]{8}" +
                    "\\s[0-9A-F]{2}:[0-9A-F]{8}\\s[0-9A-F]{8}\\s+([0-9A-F]+)",
            Pattern.CASE_INSENSITIVE
                    or Pattern.UNIX_LINES
        )
    }

    init {
        mNetCaches = CacheBuilder.newBuilder()
            .expireAfterAccess(NET_ALIVE_SECONDS.toLong(), TimeUnit.SECONDS)
            .concurrencyLevel(NET_CONCURRENCY_LEVEL)
            .maximumSize(NET_MAX_SIZE.toLong())
            .build()
        mDumpers = ArrayMap(2)
        mDumpers[Protocol.TCP] = arrayOf(
            NetDumper("/proc/net/tcp6", localIp, IPV6_PATTERN),
            NetDumper("/proc/net/tcp", localIp, IPV4_PATTERN)
        )
        mDumpers[Protocol.UDP] = arrayOf(
            NetDumper("/proc/net/udp6", localIp, IPV6_PATTERN),
            NetDumper("/proc/net/udp", localIp, IPV4_PATTERN)
        )
    }
}