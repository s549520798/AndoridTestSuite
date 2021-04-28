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

import com.github.megatronking.netbare.ip.*
import com.github.megatronking.netbare.net.Session

/**
 * A log util using in NetBare, it uses the protocol, ip and port as the prefix.
 *
 * @author Megatron King
 * @since 2018-10-14 10:25
 */
class NetBareXLog(protocol: Protocol?, ip: String?, port: Int) {
    private val mPrefix: String

    /**
     * Constructs a NetBareXLog instance with the net information.
     *
     * @param protocol The IP protocol.
     * @param ip The ip address, a string value.
     * @param port The port, a short value.
     */
    constructor(protocol: Protocol?, ip: String?, port: Short) : this(
        protocol,
        ip,
        NetBareUtils.convertPort(port)
    ) {
    }

    /**
     * Constructs a NetBareXLog instance with the net information.
     *
     * @param protocol The IP protocol.
     * @param ip The ip address, a int value.
     * @param port The port, a short value.
     */
    constructor(protocol: Protocol?, ip: Int, port: Short) : this(
        protocol,
        NetBareUtils.convertIp(ip),
        port
    ) {
    }

    /**
     * Constructs a NetBareXLog instance with the net information.
     *
     * @param protocol The IP protocol.
     * @param ip The ip address, a int value.
     * @param port The port, a int value.
     */
    constructor(protocol: Protocol?, ip: Int, port: Int) : this(
        protocol,
        NetBareUtils.convertIp(ip),
        port
    ) {
    }

    /**
     * Constructs a NetBareXLog instance with the net information.
     *
     * @param session The session contains net information.
     */
    constructor(session: Session) : this(session.protocol, session.remoteIp, session.remotePort) {}

    /**
     * Print a verbose level log in console, format is '[protocol][ip:port]message'.
     *
     * @param msg The message you would like logged.
     */
    fun v(msg: String) {
        NetBareLog.v(mPrefix + msg)
    }

    fun v(msg: String, vararg args: Any?) {
        NetBareLog.v(mPrefix + msg, *args)
    }

    /**
     * Print a debug level log in console, format is '[protocol][ip:port]message'.
     *
     * @param msg The message you would like logged.
     */
    fun d(msg: String) {
        NetBareLog.d(mPrefix + msg)
    }

    /**
     * Print a verbose level log in console, format is '[protocol][ip:port]message'.
     *
     * @param msg The message you would like logged.
     * @param args Arguments referenced by the format specifiers in the format string.
     */
    fun d(msg: String, vararg args: Any?) {
        NetBareLog.d(mPrefix + msg, *args)
    }

    /**
     * Print a info level log in console, format is '[protocol][ip:port]message'.
     *
     * @param msg The message you would like logged.
     */
    fun i(msg: String) {
        NetBareLog.i(mPrefix + msg)
    }

    /**
     * Print a info level log in console, format is '[protocol][ip:port]message'.
     *
     * @param msg The message you would like logged.
     * @param args Arguments referenced by the format specifiers in the format string.
     */
    fun i(msg: String, vararg args: Any?) {
        NetBareLog.i(mPrefix + msg, *args)
    }

    /**
     * Print a error level log in console, format is '[protocol][ip:port]message'.
     *
     * @param msg The message you would like logged.
     */
    fun e(msg: String) {
        NetBareLog.e(mPrefix + msg)
    }

    /**
     * Print a error level log in console, format is '[protocol][ip:port]message'.
     *
     * @param msg The message you would like logged.
     * @param args Arguments referenced by the format specifiers in the format string.
     */
    fun e(msg: String, vararg args: Any?) {
        NetBareLog.e(mPrefix + msg, *args)
    }

    /**
     * Print a warning level log in console, format is '[protocol][ip:port]message'.
     *
     * @param msg The message you would like logged.
     */
    fun w(msg: String) {
        NetBareLog.w(mPrefix + msg)
    }

    /**
     * Print a warning level log in console, format is '[protocol][ip:port]message'.
     *
     * @param msg The message you would like logged.
     * @param args Arguments referenced by the format specifiers in the format string.
     */
    fun w(msg: String, vararg args: Any?) {
        NetBareLog.w(mPrefix + msg, *args)
    }

    /**
     * Constructs a NetBareXLog instance with the net information.
     *
     * @param protocol The IP protocol.
     * @param ip The ip address, a string value.
     * @param port The port, a int value.
     */
    init {
        mPrefix = "[" + protocol!!.name + "][" + ip + ":" + port + "]"
    }
}