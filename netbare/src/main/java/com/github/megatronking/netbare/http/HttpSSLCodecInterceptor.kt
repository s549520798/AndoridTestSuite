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
package com.github.megatronking.netbare.http

import com.github.megatronking.netbare.NetBareXLog
import com.github.megatronking.netbare.gateway.*
import com.github.megatronking.netbare.ip.*
import com.github.megatronking.netbare.ssl.SSLCodec.CodecCallback
import com.github.megatronking.netbare.ssl.SSLEngineFactory
import com.github.megatronking.netbare.ssl.SSLRefluxCallback
import com.github.megatronking.netbare.ssl.SSLUtils
import java.io.IOException
import java.nio.ByteBuffer

/**
 * An interceptor decodes SSL encrypt packets to plaintext packets.
 *
 * @author Megatron King
 * @since 2018-11-15 15:39
 */
/* package */
internal class HttpSSLCodecInterceptor(
    private val mEngineFactory: SSLEngineFactory?,
    private val mRequest: Request,
    private val mResponse: Response
) : HttpPendingIndexedInterceptor(), SSLRefluxCallback<HttpRequest?, HttpResponse?> {
    private val mRequestCodec: HttpSSLRequestCodec
    private val mResponseCodec: HttpSSLResponseCodec
    private val mLog: NetBareXLog
    private var mClientAlpnResolved = false
    @Throws(IOException::class)
    override fun intercept(
        @NonNull chain: HttpRequestChain, @NonNull buffer: ByteBuffer,
        index: Int
    ) {
        var buffer: ByteBuffer? = buffer
        if (!chain.request().isHttps) {
            chain.process(buffer)
        } else if (mEngineFactory == null) {
            // Skip all interceptors
            chain.processFinal(buffer)
            mLog.w("JSK not installed, skip all interceptors!")
        } else {
            if (!mClientAlpnResolved) {
                buffer = mergeRequestBuffer(buffer!!)
                val verifyResult = SSLUtils.verifyPacket(buffer)
                if (verifyResult == SSLUtils.PACKET_NOT_ENCRYPTED) {
                    throw IOException("SSL packet is not encrypt.")
                }
                if (verifyResult == SSLUtils.PACKET_NOT_ENOUGH) {
                    pendRequestBuffer(buffer)
                    return
                }
                mRequestCodec.setRequest(chain.request())
                // Start handshake with remote server
                mResponseCodec.setRequest(chain.request())

                // Parse the ALPN protocol of client.
                val protocols = SSLUtils.parseClientHelloAlpn(buffer)
                mClientAlpnResolved = true
                if (protocols == null || protocols.size == 0) {
                    mRequestCodec.setSelectedAlpnResolved()
                    mResponseCodec.setSelectedAlpnResolved()
                    mResponseCodec.prepareHandshake()
                } else {
                    // Detect remote server's ALPN and then continue request.
                    mResponseCodec.prepareHandshake(protocols) { selectedAlpnProtocol ->
                        if (selectedAlpnProtocol != null) {
                            val protocol: HttpProtocol =
                                HttpProtocol.Companion.parse(selectedAlpnProtocol)
                            // Only accept Http1.1 and Http2.0
                            if (protocol == HttpProtocol.HTTP_1_1 || protocol == HttpProtocol.HTTP_2) {
                                mRequestCodec.setSelectedAlpnProtocol(protocol)
                                chain.request().session()!!.protocol = protocol
                                mLog.i("Server selected ALPN protocol: $protocol")
                            } else {
                                mLog.w("Unexpected server ALPN protocol: $protocol")
                            }
                        }
                        mRequestCodec.setSelectedAlpnResolved()
                        // Continue request.
                        decodeRequest(chain, ByteBuffer.allocate(0))
                    }
                }
            }
            // Hold the request buffer until the server ALPN configuration resolved.
            if (!mRequestCodec.selectedAlpnResolved()) {
                pendRequestBuffer(buffer!!)
                return
            }
            decodeRequest(chain, buffer)
        }
    }

    @Throws(IOException::class)
    override fun intercept(
        @NonNull chain: HttpResponseChain, @NonNull buffer: ByteBuffer,
        index: Int
    ) {
        if (!chain.response().isHttps) {
            chain.process(buffer)
        } else if (mEngineFactory == null) {
            // Skip all interceptors
            chain.processFinal(buffer)
            mLog.w("JSK not installed, skip all interceptors!")
        } else {
            // Merge buffers
            decodeResponse(chain, buffer)
        }
    }

    @Throws(IOException::class)
    override fun onRequest(request: HttpRequest?, buffer: ByteBuffer?) {
        mResponseCodec.encode(buffer!!, object : CodecCallback {
            override fun onPending(buffer: ByteBuffer?) {}
            override fun onProcess(buffer: ByteBuffer?) {}

            @Throws(IOException::class)
            override fun onEncrypt(buffer: ByteBuffer?) {
                // The encrypt request data is sent to remote server
                mRequest.process(buffer)
            }

            override fun onDecrypt(buffer: ByteBuffer?) {}
        })
    }

    @Throws(IOException::class)
    override fun onResponse(response: HttpResponse?, buffer: ByteBuffer?) {
        mRequestCodec.encode(buffer!!, object : CodecCallback {
            override fun onPending(buffer: ByteBuffer?) {}
            override fun onProcess(buffer: ByteBuffer?) {}

            @Throws(IOException::class)
            override fun onEncrypt(buffer: ByteBuffer?) {
                // The encrypt response data is sent to proxy server
                mResponse.process(buffer)
            }

            override fun onDecrypt(buffer: ByteBuffer?) {}
        })
    }

    @Throws(IOException::class)
    private fun decodeRequest(chain: HttpRequestChain, buffer: ByteBuffer?) {
        // Merge buffers
        mRequestCodec.decode(mergeRequestBuffer(buffer!!),
            object : CodecCallback {
                override fun onPending(buffer: ByteBuffer?) {
                    pendRequestBuffer(buffer!!)
                }

                @Throws(IOException::class)
                override fun onProcess(buffer: ByteBuffer?) {
                    chain.processFinal(buffer)
                }

                @Throws(IOException::class)
                override fun onEncrypt(buffer: ByteBuffer?) {
                    mResponse.process(buffer)
                }

                @Throws(IOException::class)
                override fun onDecrypt(buffer: ByteBuffer?) {
                    chain.process(buffer)
                }
            })
    }

    @Throws(IOException::class)
    private fun decodeResponse(chain: HttpResponseChain, buffer: ByteBuffer) {
        // Merge buffers
        mResponseCodec.decode(mergeResponseBuffer(buffer),
            object : CodecCallback {
                override fun onPending(buffer: ByteBuffer?) {
                    pendResponseBuffer(buffer!!)
                }

                @Throws(IOException::class)
                override fun onProcess(buffer: ByteBuffer?) {
                    chain.processFinal(buffer)
                }

                @Throws(IOException::class)
                override fun onEncrypt(buffer: ByteBuffer?) {
                    mRequest.process(buffer)
                }

                @Throws(IOException::class)
                override fun onDecrypt(buffer: ByteBuffer?) {
                    chain.process(buffer)
                }
            })
    }

    /* package */
    init {
        mRequestCodec = HttpSSLRequestCodec(mEngineFactory)
        mResponseCodec = HttpSSLResponseCodec(mEngineFactory)
        mLog = NetBareXLog(Protocol.TCP, mRequest.ip(), mRequest.port())
    }
}