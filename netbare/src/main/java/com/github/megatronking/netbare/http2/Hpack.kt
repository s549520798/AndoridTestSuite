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

import android.text.TextUtils
import com.github.megatronking.netbare.NetBareUtils
import com.github.megatronking.netbare.http.HttpMethod
import com.github.megatronking.netbare.http.HttpProtocol
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import kotlin.experimental.and

/**
 * Read and write HPACK v10.
 *
 * https://httpwg.org/specs/rfc7540.html#HeaderBlock
 *
 * This implementation uses an array for the dynamic table and a list for indexed entries.  Dynamic
 * entries are added to the array, starting in the last position moving forward.  When the array
 * fills, it is doubled.
 *
 * @author Megatron King
 * @since 2019/1/5 20:13
 */
/* package */
internal object Hpack {
    private const val PREFIX_4_BITS = 0x0f
    private const val PREFIX_5_BITS = 0x1f
    private const val PREFIX_6_BITS = 0x3f
    private const val PREFIX_7_BITS = 0x7f
    private val STATIC_HEADER_TABLE = arrayOf(
        Header(Header.TARGET_AUTHORITY, ""),
        Header(Header.TARGET_METHOD, "GET"),
        Header(Header.TARGET_METHOD, "POST"),
        Header(Header.TARGET_PATH, "/"),
        Header(Header.TARGET_PATH, "/index.html"),
        Header(Header.TARGET_SCHEME, "http"),
        Header(Header.TARGET_SCHEME, "https"),
        Header(Header.RESPONSE_STATUS, "200"),
        Header(Header.RESPONSE_STATUS, "204"),
        Header(Header.RESPONSE_STATUS, "206"),
        Header(Header.RESPONSE_STATUS, "304"),
        Header(Header.RESPONSE_STATUS, "400"),
        Header(Header.RESPONSE_STATUS, "404"),
        Header(Header.RESPONSE_STATUS, "500"),
        Header("accept-charset", ""),
        Header("accept-encoding", "gzip, deflate"),
        Header("accept-language", ""),
        Header("accept-ranges", ""),
        Header("accept", ""),
        Header("access-control-allow-origin", ""),
        Header("age", ""),
        Header("allow", ""),
        Header("authorization", ""),
        Header("cache-control", ""),
        Header("content-disposition", ""),
        Header("content-encoding", ""),
        Header("content-language", ""),
        Header("content-length", ""),
        Header("content-location", ""),
        Header("content-range", ""),
        Header("content-type", ""),
        Header("cookie", ""),
        Header("date", ""),
        Header("etag", ""),
        Header("expect", ""),
        Header("expires", ""),
        Header("from", ""),
        Header("host", ""),
        Header("if-match", ""),
        Header("if-modified-since", ""),
        Header("if-none-match", ""),
        Header("if-range", ""),
        Header("if-unmodified-since", ""),
        Header("last-modified", ""),
        Header("link", ""),
        Header("location", ""),
        Header("max-forwards", ""),
        Header("proxy-authenticate", ""),
        Header("proxy-authorization", ""),
        Header("range", ""),
        Header("referer", ""),
        Header("refresh", ""),
        Header("retry-after", ""),
        Header("server", ""),
        Header("set-cookie", ""),
        Header("strict-transport-security", ""),
        Header("transfer-encoding", ""),
        Header("user-agent", ""),
        Header("vary", ""),
        Header("via", ""),
        Header("www-authenticate", "")
    )
    private val HTTP_2_SKIPPED_REQUEST_HEADERS = Arrays.asList(
        "connection",
        "host",
        "keep_alive",
        "proxy_connection",
        "te",
        "transfer_encoding",
        "encoding",
        "upgrade"
    )
    private val HTTP_2_SKIPPED_RESPONSE_HEADERS = Arrays.asList(
        "connection",
        "host",
        "keep_alive",
        "proxy_connection",
        "te",
        "transfer_encoding",
        "encoding",
        "upgrade"
    )
    private val NAME_TO_FIRST_INDEX = nameToFirstIndex()
    private fun nameToFirstIndex(): Map<String?, Int> {
        val result: MutableMap<String?, Int> = LinkedHashMap(STATIC_HEADER_TABLE.size)
        for (i in STATIC_HEADER_TABLE.indices) {
            if (!result.containsKey(STATIC_HEADER_TABLE[i].name)) {
                result[STATIC_HEADER_TABLE[i].name] = i
            }
        }
        return Collections.unmodifiableMap(result)
    }

    private const val DEFAULT_HEADER_TABLE_SIZE_SETTING = 4096
    private const val SETTINGS_HEADER_TABLE_SIZE_LIMIT = 16384

    internal class Reader {
        private val mHeaders: MutableList<Header>
        private var mHeaderTableSizeSetting: Int
        private var mDynamicTable: Array<Header?>
        private var mNextHeaderIndex: Int
        private var mHeaderCount = 0
        private var mDynamicTableByteCount = 0
        private var mMaxDynamicTableByteCount: Int
        fun setHeaderTableSizeSetting(headerTableSizeSetting: Int) {
            if (mHeaderTableSizeSetting == headerTableSizeSetting) {
                return
            }
            mHeaderTableSizeSetting = headerTableSizeSetting
            val effectiveHeaderTableSize = Math.min(
                headerTableSizeSetting,
                SETTINGS_HEADER_TABLE_SIZE_LIMIT
            )
            if (mMaxDynamicTableByteCount == effectiveHeaderTableSize) {
                return  // No change.
            }
            mMaxDynamicTableByteCount = effectiveHeaderTableSize
            adjustDynamicTableByteCount()
        }

        @Throws(IOException::class, IndexOutOfBoundsException::class)
        fun readHeaders(buffer: ByteBuffer?, flags: Byte, callback: DecodeCallback) {
            mHeaders.clear()
            while (buffer!!.hasRemaining()) {
                val b: Int = buffer.get().toInt() and 0xff
                if (b == 0x80) { // 10000000
                    throw IOException("Hpack read headers failed: index == 0")
                } else if (b and 0x80 == 0x80) { // 1NNNNNNN
                    val index = readInt(buffer, b, PREFIX_7_BITS)
                    readIndexedHeader(index - 1)
                } else if (b == 0x40) { // 01000000
                    readLiteralHeaderWithIncrementalIndexingNewName(buffer)
                } else if (b and 0x40 == 0x40) {  // 01NNNNNN
                    val index = readInt(buffer, b, PREFIX_6_BITS)
                    readLiteralHeaderWithIncrementalIndexingIndexedName(buffer, index - 1)
                } else if (b and 0x20 == 0x20) {  // 001NNNNN
                    mMaxDynamicTableByteCount = readInt(buffer, b, PREFIX_5_BITS)
                    if (mMaxDynamicTableByteCount < 0
                        || mMaxDynamicTableByteCount > mHeaderTableSizeSetting
                    ) {
                        throw IOException(
                            "Hpack read headers failed: Invalid dynamic table " +
                                    "size update " + mMaxDynamicTableByteCount
                        )
                    }
                    adjustDynamicTableByteCount()
                } else if (b == 0x10 || b == 0) { // 000?0000 - Ignore never indexed bit.
                    readLiteralHeaderWithoutIndexingNewName(buffer)
                } else { // 000?NNNN - Ignore never indexed bit.
                    val index = readInt(buffer, b, PREFIX_4_BITS)
                    readLiteralHeaderWithoutIndexingIndexedName(buffer, index - 1)
                }
            }
            // Build normal http header part
            var method: String? = null
            var path: String? = null
            var host: String? = null
            var status: String? = null
            val headers: MutableList<Header> = ArrayList()
            for (header in mHeaders) {
                if (header.name == Header.TARGET_METHOD) {
                    method = header.value
                } else if (header.name == Header.TARGET_PATH) {
                    path = header.value
                } else if (header.name == Header.TARGET_AUTHORITY) {
                    host = header.value
                } else if (header.name.equals("host", ignoreCase = true)) {
                    host = header.value
                } else if (header.name.equals(Header.RESPONSE_STATUS, ignoreCase = true)) {
                    status = header.value
                } else {
                    headers.add(header)
                }
            }
            val sb = StringBuilder()
            if (method != null && path != null) {
                sb.append(method).append(" ").append(path).append(" ")
                    .append(HttpProtocol.HTTP_2.toString())
                sb.append(NetBareUtils.LINE_END)
            }
            if (status != null) {
                sb.append(HttpProtocol.HTTP_2.toString()).append(" ").append(status)
                sb.append(NetBareUtils.LINE_END)
            }
            if (host != null) {
                headers.add(0, Header("Host", host))
            }
            for (header in headers) {
                if (header.name == Header.TARGET_SCHEME) {
                    continue
                }
                sb.append(header.name).append(": ")
                if (header.value != null) {
                    sb.append(header.value)
                }
                sb.append(NetBareUtils.LINE_END)
            }
            if ((flags and Http2.FLAG_END_HEADERS) != 0.toByte()) {
                sb.append(NetBareUtils.LINE_END)
            }
            callback.onResult(
                ByteBuffer.wrap(sb.toString().toByteArray()),
                flags and Http2.FLAG_END_STREAM != 0.toByte()
            )
        }

        private fun readInt(buffer: ByteBuffer?, firstByte: Int, prefixMask: Int): Int {
            val prefix = firstByte and prefixMask
            if (prefix < prefixMask) {
                return prefix // This was a single byte value.
            }

            // This is a multibyte value. Read 7 bits at a time.
            var result = prefixMask
            var shift = 0
            while (buffer!!.hasRemaining()) {
                val b = buffer.get().toInt()
                if (b and 0x80 != 0) { // Equivalent to (b >= 128) since b is in [0..255].
                    result += b and 0x7f shl shift
                    shift += 7
                } else {
                    result += b shl shift // Last byte.
                    break
                }
            }
            return result
        }

        private fun readByte(buffer: ByteBuffer?): Int {
            return buffer!!.get().toInt() and 0xff
        }

        @Throws(IOException::class)
        private fun readIndexedHeader(index: Int) {
            if (isStaticHeader(index)) {
                val staticEntry = STATIC_HEADER_TABLE[index]
                mHeaders.add(staticEntry)
            } else {
                val dynamicTableIndex = dynamicTableIndex(index - STATIC_HEADER_TABLE.size)
                if (dynamicTableIndex < 0 || dynamicTableIndex >= mDynamicTable.size) {
                    throw IOException(
                        "Hpack read headers failed: Header index too large " +
                                (index + 1)
                    )
                }
                val header = mDynamicTable[dynamicTableIndex]
                    ?: throw IOException("Hpack read headers failed: read dynamic table failed!")
                mHeaders.add(header)
            }
        }

        private fun dynamicTableIndex(index: Int): Int {
            return mNextHeaderIndex + 1 + index
        }

        private fun isStaticHeader(index: Int): Boolean {
            return index >= 0 && index <= STATIC_HEADER_TABLE.size - 1
        }

        @Throws(IOException::class)
        private fun readLiteralHeaderWithoutIndexingIndexedName(buffer: ByteBuffer?, index: Int) {
            val name = getName(index)
            val value = readString(buffer)
            mHeaders.add(Header(name, value))
        }

        @Throws(IOException::class)
        private fun getName(index: Int): String? {
            return if (isStaticHeader(index)) {
                STATIC_HEADER_TABLE[index].name
            } else {
                val dynamicTableIndex = dynamicTableIndex(index - STATIC_HEADER_TABLE.size)
                if (dynamicTableIndex < 0 || dynamicTableIndex >= mDynamicTable.size) {
                    throw IOException(
                        "Hpack read headers failed: Header index too large " +
                                (index + 1)
                    )
                }
                mDynamicTable[dynamicTableIndex]!!.name
            }
        }

        @Throws(IOException::class)
        private fun readString(buffer: ByteBuffer?): String {
            if (!buffer!!.hasRemaining()) {
                throw IOException("Hpack read headers failed: data is exhaust")
            }
            val firstByte = readByte(buffer)
            val huffmanDecode = firstByte and 0x80 == 0x80 // 1NNNNNNN
            val length = readInt(buffer, firstByte, PREFIX_7_BITS)
            if (buffer.remaining() < length) {
                throw IOException(
                    "Hpack read headers failed: data not enough, expect: " +
                            length + " actual: " + buffer.remaining()
                )
            }
            val data = ByteArray(length)
            buffer[data]
            return String(if (huffmanDecode) Huffman.Companion.get().decode(data) else data)
        }

        @Throws(IOException::class)
        private fun readLiteralHeaderWithIncrementalIndexingNewName(buffer: ByteBuffer?) {
            val name = checkLowercase(readString(buffer))
            val value = readString(buffer)
            insertIntoDynamicTable(-1, Header(name, value))
        }

        @Throws(IOException::class)
        private fun readLiteralHeaderWithIncrementalIndexingIndexedName(
            buffer: ByteBuffer?,
            nameIndex: Int
        ) {
            val name = getName(nameIndex)
            val value = readString(buffer)
            insertIntoDynamicTable(-1, Header(name, value))
        }

        @Throws(IOException::class)
        private fun readLiteralHeaderWithoutIndexingNewName(buffer: ByteBuffer?) {
            val name = checkLowercase(readString(buffer))
            val value = readString(buffer)
            mHeaders.add(Header(name, value))
        }

        @Throws(IOException::class)
        private fun insertIntoDynamicTable(index: Int, entry: Header) {
            var index = index
            mHeaders.add(entry)
            var delta = entry.hpackSize()
            if (index != -1) { // Index -1 == new header.
                val header = mDynamicTable[dynamicTableIndex(index)]
                    ?: throw IOException("Hpack read headers failed: insert dynamic table failed!")
                delta -= header.hpackSize()
            }

            // if the new or replacement header is too big, drop all entries.
            if (delta > mMaxDynamicTableByteCount) {
                clearDynamicTable()
                return
            }

            // Evict headers to the required length.
            val bytesToRecover = mDynamicTableByteCount + delta - mMaxDynamicTableByteCount
            val entriesEvicted = evictToRecoverBytes(bytesToRecover)
            if (index == -1) { // Adding a value to the dynamic table.
                if (mHeaderCount + 1 > mDynamicTable.size) { // Need to grow the dynamic table.
                    val doubled = arrayOfNulls<Header>(mDynamicTable.size * 2)
                    System.arraycopy(
                        mDynamicTable, 0, doubled, mDynamicTable.size,
                        mDynamicTable.size
                    )
                    mNextHeaderIndex = mDynamicTable.size - 1
                    mDynamicTable = doubled
                }
                index = mNextHeaderIndex--
                mDynamicTable[index] = entry
                mHeaderCount++
            } else { // Replace value at same position.
                index += dynamicTableIndex(index) + entriesEvicted
                mDynamicTable[index] = entry
            }
            mDynamicTableByteCount += delta
        }

        private fun clearDynamicTable() {
            Arrays.fill(mDynamicTable, null)
            mNextHeaderIndex = mDynamicTable.size - 1
            mHeaderCount = 0
            mDynamicTableByteCount = 0
        }

        private fun evictToRecoverBytes(bytesToRecover: Int): Int {
            var bytesToRecover = bytesToRecover
            var entriesToEvict = 0
            if (bytesToRecover > 0) {
                // determine how many headers need to be evicted.
                var j = mDynamicTable.size - 1
                while (j >= mNextHeaderIndex && bytesToRecover > 0) {
                    bytesToRecover -= mDynamicTable[j]!!.hpackSize()
                    mDynamicTableByteCount -= mDynamicTable[j]!!.hpackSize()
                    mHeaderCount--
                    entriesToEvict++
                    j--
                }
                System.arraycopy(
                    mDynamicTable, mNextHeaderIndex + 1, mDynamicTable,
                    mNextHeaderIndex + 1 + entriesToEvict, mHeaderCount
                )
                mNextHeaderIndex += entriesToEvict
            }
            return entriesToEvict
        }

        private fun adjustDynamicTableByteCount() {
            if (mMaxDynamicTableByteCount < mDynamicTableByteCount) {
                if (mMaxDynamicTableByteCount == 0) {
                    clearDynamicTable()
                } else {
                    evictToRecoverBytes(mDynamicTableByteCount - mMaxDynamicTableByteCount)
                }
            }
        }

        @Throws(IOException::class)
        private fun checkLowercase(name: String): String {
            for (i in 0 until name.length) {
                val c = name[i]
                if (c >= 'A' && c <= 'Z') {
                    throw IOException("Hpack read headers failed: mixed case name: $name")
                }
            }
            return name
        }

        /* package */
        init {
            mHeaders = ArrayList()
            mDynamicTable = arrayOfNulls(8)
            mNextHeaderIndex = mDynamicTable.size - 1
            mMaxDynamicTableByteCount = DEFAULT_HEADER_TABLE_SIZE_SETTING
            mHeaderTableSizeSetting = DEFAULT_HEADER_TABLE_SIZE_SETTING
        }
    }

    internal class Writer {
        private var mSmallestHeaderTableSizeSetting: Int
        private var mEmitDynamicTableSizeUpdate = false
        private var mHeaderTableSizeSetting: Int
        private var mMaxDynamicTableByteCount: Int

        // Visible for testing.
        private var mDynamicTable: Array<Header?>

        // Array is populated back to front, so new entries always have lowest index.
        private var mNextHeaderIndex: Int
        private var mHeaderCount = 0
        private var mDynamicTableByteCount = 0
        private var mOut: ByteArrayOutputStream? = null
        @Throws(IOException::class)
        fun writeRequestHeaders(
            method: HttpMethod?, path: String?, host: String?,
            headers: Map<String?, MutableList<String?>?>?
        ): ByteArray {
            mOut = ByteArrayOutputStream()
            val hpackHeaders: MutableList<Header> = ArrayList()
            hpackHeaders.add(Header(Header.TARGET_METHOD, method!!.name))
            hpackHeaders.add(Header(Header.TARGET_PATH, path))
            hpackHeaders.add(Header(Header.TARGET_AUTHORITY, host))
            hpackHeaders.add(Header(Header.TARGET_SCHEME, "https"))
            for ((key, value1) in headers!!) {
                if (HTTP_2_SKIPPED_REQUEST_HEADERS.contains(key!!.toLowerCase())) {
                    continue
                }
                for (value in value1!!) {
                    hpackHeaders.add(Header(key, value))
                }
            }
            return writeHeaders(hpackHeaders)
        }

        @Throws(IOException::class)
        fun writeResponseHeaders(
            code: Int,
            message: String?,
            headers: Map<String?, MutableList<String?>?>?
        ): ByteArray {
            mOut = ByteArrayOutputStream()
            val hpackHeaders: MutableList<Header> = ArrayList()
            hpackHeaders.add(
                Header(
                    Header.RESPONSE_STATUS,
                    if (TextUtils.isEmpty(message)) code.toString() else "$code $message"
                )
            )
            for ((key, value1) in headers!!) {
                if (HTTP_2_SKIPPED_RESPONSE_HEADERS.contains(key!!.toLowerCase())) {
                    continue
                }
                for (value in value1!!) {
                    hpackHeaders.add(Header(key, value))
                }
            }
            return writeHeaders(hpackHeaders)
        }

        private fun clearDynamicTable() {
            Arrays.fill(mDynamicTable, null)
            mNextHeaderIndex = mDynamicTable.size - 1
            mHeaderCount = 0
            mDynamicTableByteCount = 0
        }

        private fun evictToRecoverBytes(bytesToRecover: Int): Int {
            var bytesToRecover = bytesToRecover
            var entriesToEvict = 0
            if (bytesToRecover > 0) {
                // determine how many headers need to be evicted.
                var j = mDynamicTable.size - 1
                while (j >= mNextHeaderIndex && bytesToRecover > 0) {
                    bytesToRecover -= mDynamicTable[j]!!.hpackSize()
                    mDynamicTableByteCount -= mDynamicTable[j]!!.hpackSize()
                    mHeaderCount--
                    entriesToEvict++
                    j--
                }
                System.arraycopy(
                    mDynamicTable, mNextHeaderIndex + 1, mDynamicTable,
                    mNextHeaderIndex + 1 + entriesToEvict, mHeaderCount
                )
                Arrays.fill(
                    mDynamicTable,
                    mNextHeaderIndex + 1,
                    mNextHeaderIndex + 1 + entriesToEvict,
                    null
                )
                mNextHeaderIndex += entriesToEvict
            }
            return entriesToEvict
        }

        private fun insertIntoDynamicTable(entry: Header) {
            val delta = entry.hpackSize()

            // if the new or replacement header is too big, drop all entries.
            if (delta > mMaxDynamicTableByteCount) {
                clearDynamicTable()
                return
            }

            // Evict headers to the required length.
            val bytesToRecover = mDynamicTableByteCount + delta - mMaxDynamicTableByteCount
            evictToRecoverBytes(bytesToRecover)
            if (mHeaderCount + 1 > mDynamicTable.size) { // Need to grow the dynamic table.
                val doubled = arrayOfNulls<Header>(mDynamicTable.size * 2)
                System.arraycopy(mDynamicTable, 0, doubled, mDynamicTable.size, mDynamicTable.size)
                mNextHeaderIndex = mDynamicTable.size - 1
                mDynamicTable = doubled
            }
            val index = mNextHeaderIndex--
            mDynamicTable[index] = entry
            mHeaderCount++
            mDynamicTableByteCount += delta
        }

        @Throws(IOException::class)
        private fun writeHeaders(headerBlock: List<Header>): ByteArray {
            if (mEmitDynamicTableSizeUpdate) {
                if (mSmallestHeaderTableSizeSetting < mMaxDynamicTableByteCount) {
                    // Multiple dynamic table size updates!
                    writeInt(mSmallestHeaderTableSizeSetting, PREFIX_5_BITS, 0x20)
                }
                mEmitDynamicTableSizeUpdate = false
                mSmallestHeaderTableSizeSetting = Int.MAX_VALUE
                writeInt(mMaxDynamicTableByteCount, PREFIX_5_BITS, 0x20)
            }
            var i = 0
            val size = headerBlock.size
            while (i < size) {
                val header = headerBlock[i]
                val name = header.name!!.toLowerCase()
                val value = header.value
                var headerIndex = -1
                var headerNameIndex = -1
                val staticIndex = NAME_TO_FIRST_INDEX[name]
                if (staticIndex != null) {
                    headerNameIndex = staticIndex + 1
                    if (headerNameIndex > 1 && headerNameIndex < 8) {
                        // Only search a subset of the static header table. Most entries have an empty value, so
                        // it's unnecessary to waste cycles looking at them. This check is built on the
                        // observation that the header entries we care about are in adjacent pairs, and we
                        // always know the first index of the pair.
                        if (TextUtils.equals(
                                STATIC_HEADER_TABLE[headerNameIndex - 1].value,
                                value
                            )
                        ) {
                            headerIndex = headerNameIndex
                        } else if (TextUtils.equals(
                                STATIC_HEADER_TABLE[headerNameIndex].value,
                                value
                            )
                        ) {
                            headerIndex = headerNameIndex + 1
                        }
                    }
                }
                if (headerIndex == -1) {
                    var j = mNextHeaderIndex + 1
                    val length = mDynamicTable.size
                    while (j < length) {
                        if (TextUtils.equals(mDynamicTable[j]!!.name, name)) {
                            if (TextUtils.equals(mDynamicTable[j]!!.value, value)) {
                                headerIndex = j - mNextHeaderIndex + STATIC_HEADER_TABLE.size
                                break
                            } else if (headerNameIndex == -1) {
                                headerNameIndex = j - mNextHeaderIndex + STATIC_HEADER_TABLE.size
                            }
                        }
                        j++
                    }
                }
                if (headerIndex != -1) {
                    // Indexed Header Field.
                    writeInt(headerIndex, PREFIX_7_BITS, 0x80)
                } else if (headerNameIndex == -1) {
                    // Literal Header Field with Incremental Indexing - New Name.
                    mOut!!.write(0x40)
                    writeString(name)
                    writeString(value)
                    insertIntoDynamicTable(header)
                } else if (name.startsWith(Header.PSEUDO_PREFIX) && Header.TARGET_AUTHORITY != name) {
                    // Follow Chromes lead - only include the :authority pseudo header, but exclude all other
                    // pseudo headers. Literal Header Field without Indexing - Indexed Name.
                    writeInt(headerNameIndex, PREFIX_4_BITS, 0)
                    writeString(value)
                } else {
                    // Literal Header Field with Incremental Indexing - Indexed Name.
                    writeInt(headerNameIndex, PREFIX_6_BITS, 0x40)
                    writeString(value)
                    insertIntoDynamicTable(header)
                }
                i++
            }
            return mOut!!.toByteArray()
        }

        private fun writeInt(value: Int, prefixMask: Int, bits: Int) {
            // Write the raw value for a single byte value.
            var value = value
            if (value < prefixMask) {
                mOut!!.write(bits or value)
                return
            }

            // Write the mask to start a multibyte value.
            mOut!!.write(bits or prefixMask)
            value -= prefixMask

            // Write 7 bits at a time 'til we're done.
            while (value >= 0x80) {
                val b = value and 0x7f
                mOut!!.write(b or 0x80)
                value = value ushr 7
            }
            mOut!!.write(value)
        }

        @Throws(IOException::class)
        private fun writeString(data: String?) {
            val stringBytes = data!!.toByteArray()
            if (Huffman.Companion.get().encodedLength(data) < stringBytes.size) {
                val buffer = ByteBuffer.allocate(stringBytes.size)
                Huffman.Companion.get().encode(data, buffer)
                buffer.flip()
                writeInt(buffer.remaining(), PREFIX_7_BITS, 0x80)
                mOut!!.write(buffer.array(), buffer.position(), buffer.remaining())
            } else {
                writeInt(stringBytes.size, PREFIX_7_BITS, 0)
                mOut!!.write(data.toByteArray())
            }
        }

        fun setHeaderTableSizeSetting(headerTableSizeSetting: Int) {
            if (mHeaderTableSizeSetting == headerTableSizeSetting) {
                return
            }
            mHeaderTableSizeSetting = headerTableSizeSetting
            val effectiveHeaderTableSize = Math.min(
                headerTableSizeSetting,
                SETTINGS_HEADER_TABLE_SIZE_LIMIT
            )
            if (mMaxDynamicTableByteCount == effectiveHeaderTableSize) {
                return  // No change.
            }
            if (effectiveHeaderTableSize < mMaxDynamicTableByteCount) {
                mSmallestHeaderTableSizeSetting = Math.min(
                    mSmallestHeaderTableSizeSetting,
                    effectiveHeaderTableSize
                )
            }
            mEmitDynamicTableSizeUpdate = true
            mMaxDynamicTableByteCount = effectiveHeaderTableSize
            adjustDynamicTableByteCount()
        }

        private fun adjustDynamicTableByteCount() {
            if (mMaxDynamicTableByteCount < mDynamicTableByteCount) {
                if (mMaxDynamicTableByteCount == 0) {
                    clearDynamicTable()
                } else {
                    evictToRecoverBytes(mDynamicTableByteCount - mMaxDynamicTableByteCount)
                }
            }
        }

        init {
            mDynamicTable = arrayOfNulls(8)
            mNextHeaderIndex = mDynamicTable.size - 1
            mSmallestHeaderTableSizeSetting = Int.MAX_VALUE
            mHeaderTableSizeSetting = DEFAULT_HEADER_TABLE_SIZE_SETTING
            mMaxDynamicTableByteCount = DEFAULT_HEADER_TABLE_SIZE_SETTING
        }
    }

    private class Header(val name: String?, val value: String?) {
        fun hpackSize(): Int {
            return 32 + name!!.toByteArray().size + value!!.toByteArray().size
        }

        override fun toString(): String {
            return "$name: $value"
        }

        companion object {
            // Special header names defined in HTTP/2 spec.
            const val PSEUDO_PREFIX = ":"
            const val RESPONSE_STATUS = ":status"
            const val TARGET_METHOD = ":method"
            const val TARGET_PATH = ":path"
            const val TARGET_SCHEME = ":scheme"
            const val TARGET_AUTHORITY = ":authority"
        }
    }
}