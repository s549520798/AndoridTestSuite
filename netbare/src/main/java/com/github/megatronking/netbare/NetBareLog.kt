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

import android.util.Log

/**
 * A static log util using in NetBare, and the tag is 'NetBare';
 *
 * @author Megatron King
 * @since 2018-10-08 23:12
 */
object NetBareLog {
    private const val TAG = "NetBare"
    private var sDebug = false

    /* package */
    fun setDebug(debug: Boolean) {
        sDebug = debug
    }

    /**
     * Print a verbose level log in console.
     *
     * @param msg The message you would like logged.
     */
    fun v(msg: String?) {
        if (!sDebug || msg == null) {
            return
        }
        Log.v(TAG, msg)
    }

    /**
     * Print a verbose level log in console.
     *
     * @param msg The message you would like logged.
     * @param args Arguments referenced by the format specifiers in the format string.
     */
    fun v(msg: String, vararg args: Any?) {
        v(format(msg, args))
    }

    /**
     * Print a debug level log in console.
     *
     * @param msg The message you would like logged.
     */
    fun d(msg: String?) {
        if (!sDebug || msg == null) {
            return
        }
        Log.d(TAG, msg)
    }

    /**
     * Print a debug level log in console.
     *
     * @param msg The message you would like logged.
     * @param args Arguments referenced by the format specifiers in the format string.
     */
    fun d(msg: String, vararg args: Any?) {
        d(format(msg, args))
    }

    /**
     * Print a info level log in console.
     *
     * @param msg The message you would like logged.
     */
    fun i(msg: String?) {
        if (!sDebug || msg == null) {
            return
        }
        Log.i(TAG, msg)
    }

    /**
     * Print a info level log in console.
     *
     * @param msg The message you would like logged.
     * @param args Arguments referenced by the format specifiers in the format string.
     */
    fun i(msg: String, vararg args: Any?) {
        i(format(msg, args))
    }

    /**
     * Print a error level log in console.
     *
     * @param msg The message you would like logged.
     */
    fun e(msg: String?) {
        if (!sDebug || msg == null) {
            return
        }
        Log.e(TAG, msg)
    }

    /**
     * Print a error level log in console.
     *
     * @param msg The message you would like logged.
     * @param args Arguments referenced by the format specifiers in the format string.
     */
    fun e(msg: String, vararg args: Any?) {
        e(format(msg, args))
    }

    /**
     * Print a warning level log in console.
     *
     * @param msg The message you would like logged.
     */
    fun w(msg: String?) {
        if (!sDebug || msg == null) {
            return
        }
        Log.w(TAG, msg)
    }

    /**
     * Print a warning level log in console.
     *
     * @param msg The message you would like logged.
     * @param args Arguments referenced by the format specifiers in the format string.
     */
    fun w(msg: String, vararg args: Any?) {
        w(format(msg, args))
    }

    /**
     * Print a fatal level log in console.
     *
     * @param throwable The error you would like logged.
     */
    fun wtf(throwable: Throwable?) {
        if (!sDebug || throwable == null) {
            return
        }
        Log.wtf(TAG, throwable)
    }

    private fun format(format: String, vararg objs: Any): String {
        return if (objs == null || objs.size == 0) {
            format
        } else {
            String.format(format, *objs)
        }
    }
}