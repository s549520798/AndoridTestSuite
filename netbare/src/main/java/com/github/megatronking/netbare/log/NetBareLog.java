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
package com.github.megatronking.netbare.log;

import android.util.Log;

import static android.util.Log.DEBUG;
import static android.util.Log.ERROR;
import static android.util.Log.VERBOSE;
import static android.util.Log.WARN;
import static android.util.Log.INFO;

/**
 * A static log util using in NetBare, and the tag is 'NetBare';
 *
 * @author Megatron King
 * @since 2018-10-08 23:12
 */
public final class NetBareLog {

    private static final String TAG = "[NetBare] ";

    private static boolean sDebug;

    private static NetBareLogListener mLogListener;

    private NetBareLog() {
    }

    public static void setDebug(boolean debug) {
        sDebug = debug;
    }

    public static void setLogListener(NetBareLogListener logListener) {
        if (mLogListener != null) {
            return;
        }
        mLogListener = logListener;
    }

    /**
     * Print a verbose level log in console.
     *
     * @param msg The message you would like logged.
     */
    public static void v(String tag, String msg) {
        if (msg == null) {
            return;
        }
        printTag(VERBOSE, TAG + tag, msg);
    }

    /**
     * Print a verbose level log in console.
     *
     * @param msg  The message you would like logged.
     * @param args Arguments referenced by the format specifiers in the format string.
     */
    public static void v(String tag, String msg, Object... args) {
        v(tag, format(msg, args));
    }

    /**
     * Print a debug level log in console.
     *
     * @param msg The message you would like logged.
     */
    public static void d(String tag, String msg) {
        if (msg == null) {
            return;
        }
        printTag(DEBUG, TAG + tag, msg);
    }

    /**
     * Print a debug level log in console.
     *
     * @param msg  The message you would like logged.
     * @param args Arguments referenced by the format specifiers in the format string.
     */
    public static void d(String tag, String msg, Object... args) {
        d(tag, format(msg, args));
    }

    /**
     * Print a info level log in console.
     *
     * @param msg The message you would like logged.
     */
    public static void i(String tag, String msg) {
        if (msg == null) {
            return;
        }
        printTag(INFO, TAG + tag, msg);
    }

    /**
     * Print a info level log in console.
     *
     * @param msg  The message you would like logged.
     * @param args Arguments referenced by the format specifiers in the format string.
     */
    public static void i(String tag, String msg, Object... args) {
        i(tag, format(msg, args));
    }

    /**
     * Print a error level log in console.
     *
     * @param msg The message you would like logged.
     */
    public static void e(String tag, String msg) {
        if (msg == null) {
            return;
        }
        printTag(ERROR, TAG + tag, msg);
    }

    /**
     * Print a error level log in console.
     *
     * @param msg  The message you would like logged.
     * @param args Arguments referenced by the format specifiers in the format string.
     */
    public static void e(String tag, String msg, Object... args) {
        e(tag, format(msg, args));
    }

    /**
     * Print a warning level log in console.
     *
     * @param msg The message you would like logged.
     */
    public static void w(String tag, String msg) {
        if (msg == null) {
            return;
        }
        printTag(WARN, TAG + tag, msg);
    }

    /**
     * Print a warning level log in console.
     *
     * @param msg  The message you would like logged.
     * @param args Arguments referenced by the format specifiers in the format string.
     */
    public static void w(String tag, String msg, Object... args) {
        w(tag, format(msg, args));
    }

    /**
     * Print a fatal level log in console.
     *
     * @param throwable The error you would like logged.
     */
    public static void wtf(String tag, Throwable throwable) {
        if (throwable == null) {
            return;
        }
        throwable.printStackTrace();
        printTag(ERROR, TAG + tag, throwable.getMessage());
    }

    private static void printTag(int level, String tag, String msg, Object... args) {
        if (level == VERBOSE) {
            return;
        }

        try {
            String message =
                    (args == null || args.length == 0) ? msg : String.format(msg, args);

            if (null != mLogListener) {
                switch (level) {
                    case INFO:
                        mLogListener.i(tag, message);
                        break;
                    case DEBUG:
                        mLogListener.d(tag, message);
                        break;
                    case WARN:
                        mLogListener.w(tag, message);
                        break;
                    case ERROR:
                        mLogListener.e(tag, message);
                        break;
                }
            } else {
                if (sDebug) {
                    android.util.Log.println(level, tag, message);
                }
            }
        } catch (java.util.MissingFormatArgumentException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } catch (OutOfMemoryError e) {
            //e.printStackTrace();
        }
    }

    private static String format(String format, Object... obis) {
        if (obis == null || obis.length == 0) {
            return format;
        } else {
            return String.format(format, obis);
        }
    }

}
