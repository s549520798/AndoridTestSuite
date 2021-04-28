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
 * Copyright (C) 2014 Square, Inc.
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
package com.github.megatronking.netbare.ws

/**
 * Protocol see: http://tools.ietf.org/html/rfc6455
 *
 * @author Megatron King
 * @since 2019/1/18 23:27
 */
/* package */
internal object WebSocketProtocol {
    /**
     * Byte 0 flag for whether this is the final fragment in a message.
     */
    const val B0_FLAG_FIN = 128

    /**
     * Byte 0 reserved flag 1. Must be 0 unless negotiated otherwise.
     */
    const val B0_FLAG_RSV1 = 64

    /**
     * Byte 0 reserved flag 2. Must be 0 unless negotiated otherwise.
     */
    const val B0_FLAG_RSV2 = 32

    /**
     * Byte 0 reserved flag 3. Must be 0 unless negotiated otherwise.
     */
    const val B0_FLAG_RSV3 = 16

    /**
     * Byte 0 mask for the frame opcode.
     */
    const val B0_MASK_OPCODE = 15

    /**
     * Flag in the opcode which indicates a control frame.
     */
    const val OPCODE_FLAG_CONTROL = 8

    /**
     * Byte 1 flag for whether the payload data is masked.
     *
     * If this flag is set, the next four
     * bytes represent the mask key. These bytes appear after any additional bytes specified by [ ][.B1_MASK_LENGTH].
     */
    const val B1_FLAG_MASK = 128

    /**
     * Byte 1 mask for the payload length.
     *
     * If this value is [.PAYLOAD_SHORT], the next two
     * bytes represent the length. If this value is [.PAYLOAD_LONG], the next eight bytes
     * represent the length.
     */
    const val B1_MASK_LENGTH = 127
    const val OPCODE_CONTINUATION = 0x0
    const val OPCODE_TEXT = 0x1
    const val OPCODE_BINARY = 0x2
    const val OPCODE_CONTROL_CLOSE = 0x8
    const val OPCODE_CONTROL_PING = 0x9
    const val OPCODE_CONTROL_PONG = 0xa

    /**
     * Maximum length of frame payload. Larger payloads, if supported by the frame type, can use the
     * special values [.PAYLOAD_SHORT] or [.PAYLOAD_LONG].
     */
    const val PAYLOAD_BYTE_MAX = 125L

    /**
     * Maximum length of close message in bytes.
     */
    const val CLOSE_MESSAGE_MAX = PAYLOAD_BYTE_MAX - 2

    /**
     * Value for [.B1_MASK_LENGTH] which indicates the next two bytes are the unsigned length.
     */
    const val PAYLOAD_SHORT = 126

    /**
     * Maximum length of a frame payload to be denoted as [.PAYLOAD_SHORT].
     */
    const val PAYLOAD_SHORT_MAX = 0xffffL

    /**
     * Value for [.B1_MASK_LENGTH] which indicates the next eight bytes are the unsigned
     * length.
     */
    const val PAYLOAD_LONG = 127

    /**
     * Used when an unchecked exception was thrown in a listener.
     */
    const val CLOSE_CLIENT_GOING_AWAY = 1001

    /**
     * Used when an empty close frame was received (i.e., without a status code).
     */
    const val CLOSE_NO_STATUS_CODE = 1005
    fun closeCodeExceptionMessage(code: Int): String? {
        return if (code < 1000 || code >= 5000) {
            "Code must be in range [1000,5000): $code"
        } else if (code >= 1004 && code <= 1006 || code >= 1012 && code <= 2999) {
            "Code $code is reserved and may not be used."
        } else {
            null
        }
    }

    fun toggleMask(data: ByteArray, key: ByteArray?) {
        for (i in data.indices) {
            data[i] = (data[i] xor key!![i % key.size]) as Byte
        }
    }
}