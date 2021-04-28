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
/*
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.megatronking.netbare.http2

import java.util.*

/**
 * Http/2 peer settings.
 *
 * @author Megatron King
 * @since 2019/1/6 23:14
 */
class Http2Settings {
    /**
     * Bitfield of which flags that values.
     */
    private var set = 0

    /**
     * Flag values.
     */
    private val values = IntArray(COUNT)
    fun clear() {
        set = 0
        Arrays.fill(values, 0)
    }

    operator fun set(id: Int, value: Int): Http2Settings {
        if (id < 0 || id >= values.size) {
            return this // Discard unknown settings.
        }
        val bit = 1 shl id
        set = set or bit
        values[id] = value
        return this
    }

    fun isSet(id: Int): Boolean {
        val bit = 1 shl id
        return set and bit != 0
    }

    operator fun get(id: Int): Int {
        return values[id]
    }

    fun size(): Int {
        return Integer.bitCount(set)
    }

    val headerTableSize: Int
        get() {
            val bit = 1 shl HEADER_TABLE_SIZE
            return if (bit and set != 0) values[HEADER_TABLE_SIZE] else -1
        }

    fun getEnablePush(defaultValue: Boolean): Boolean {
        val bit = 1 shl ENABLE_PUSH
        return (if (bit and set != 0) values[ENABLE_PUSH] else if (defaultValue) 1 else 0) == 1
    }

    fun getMaxConcurrentStreams(defaultValue: Int): Int {
        val bit = 1 shl MAX_CONCURRENT_STREAMS
        return if (bit and set != 0) values[MAX_CONCURRENT_STREAMS] else defaultValue
    }

    fun getMaxFrameSize(defaultValue: Int): Int {
        val bit = 1 shl MAX_FRAME_SIZE
        return if (bit and set != 0) values[MAX_FRAME_SIZE] else defaultValue
    }

    fun getMaxHeaderListSize(defaultValue: Int): Int {
        val bit = 1 shl MAX_HEADER_LIST_SIZE
        return if (bit and set != 0) values[MAX_HEADER_LIST_SIZE] else defaultValue
    }

    val initialWindowSize: Int
        get() {
            val bit = 1 shl INITIAL_WINDOW_SIZE
            return if (bit and set != 0) values[INITIAL_WINDOW_SIZE] else DEFAULT_INITIAL_WINDOW_SIZE
        }

    /**
     * Writes `other` into this. If any setting is populated by this and `other`, the
     * value and flags from `other` will be kept.
     */
    fun merge(other: Http2Settings) {
        for (i in 0 until COUNT) {
            if (!other.isSet(i)) {
                continue
            }
            set(i, other[i])
        }
    }

    companion object {
        /**
         * From the HTTP/2 specs, the default initial window size for all streams is 64 KiB. (Chrome 25
         * uses 10 MiB).
         */
        private const val DEFAULT_INITIAL_WINDOW_SIZE = 65535

        /**
         * HTTP/2: Size in bytes of the table used to decode the sender's header blocks.
         */
        private const val HEADER_TABLE_SIZE = 1

        /**
         * HTTP/2: The peer must not send a PUSH_PROMISE frame when this is 0.
         */
        private const val ENABLE_PUSH = 2

        /**
         * Sender's maximum number of concurrent streams.
         */
        private const val MAX_CONCURRENT_STREAMS = 4

        /**
         * HTTP/2: Size in bytes of the largest frame payload the sender will accept.
         */
        private const val MAX_FRAME_SIZE = 5

        /**
         * HTTP/2: Advisory only. Size in bytes of the largest header list the sender will accept.
         */
        private const val MAX_HEADER_LIST_SIZE = 6

        /**
         * Window size in bytes.
         */
        private const val INITIAL_WINDOW_SIZE = 7

        /**
         * Total number of settings.
         */
        private const val COUNT = 10
    }
}