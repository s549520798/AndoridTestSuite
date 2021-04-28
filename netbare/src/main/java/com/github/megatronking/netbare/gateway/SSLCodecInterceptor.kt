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
package com.github.megatronking.netbare.gateway

import com.github.megatronking.netbare.NetBareXLog
import com.github.megatronking.netbare.ssl.SSLCodec.CodecCallback
import com.github.megatronking.netbare.ssl.SSLEngineFactory
import com.github.megatronking.netbare.ssl.SSLRefluxCallback
import com.github.megatronking.netbare.ssl.SSLRequestCodec
import com.github.megatronking.netbare.ssl.SSLResponseCodec
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Decodes SSL/TLS packets to plaintext.
 *
 * @author Megatron King
 * @since 2019/4/9 21:39
 */
abstract class SSLCodecInterceptor<Req : Request?, ReqChain : AbstractRequestChain<Req, out Interceptor<*, *, *, *>?>?, Res : Response?, ResChain : AbstractResponseChain<Res, out Interceptor<*, *, *, *>?>?>(
    private val mEngineFactory: SSLEngineFactory?,
    private val mRequest: Req,
    private val mResponse: Res
) : PendingIndexedInterceptor<Req, ReqChain, Res, ResChain>(), SSLRefluxCallback<Req, Res> {
    private val mRequestCodec: SSLRequestCodec
    private val mResponseCodec: SSLResponseCodec
    private val mLog: NetBareXLog

    /**
     * Should decrypt the request buffer with SSL codec.
     *
     * @param chain The request chain.
     * @return True if needs to decrypt.
     */
    protected abstract fun shouldDecrypt(chain: ReqChain): Boolean

    /**
     * Should decrypt the response buffer with SSL codec.
     *
     * @param chain The response chain.
     * @return True if needs to decrypt.
     */
    protected abstract fun shouldDecrypt(chain: ResChain): Boolean
    @Throws(IOException::class)
    override fun intercept(@NonNull chain: ReqChain, @NonNull buffer: ByteBuffer, index: Int) {
        if (mEngineFactory == null) {
            // Skip all interceptors
            chain!!.processFinal(buffer)
            mLog.w("JSK not installed, skip all interceptors!")
        } else if (shouldDecrypt(chain)) {
            decodeRequest(chain, buffer)
            mResponseCodec.prepareHandshake()
        } else {
            chain!!.process(buffer)
        }
    }

    @Throws(IOException::class)
    override fun intercept(@NonNull chain: ResChain, @NonNull buffer: ByteBuffer, index: Int) {
        if (mEngineFactory == null) {
            // Skip all interceptors
            chain!!.processFinal(buffer)
            mLog.w("JSK not installed, skip all interceptors!")
        } else if (shouldDecrypt(chain)) {
            decodeResponse(chain, buffer)
        } else {
            chain!!.process(buffer)
        }
    }

    @Throws(IOException::class)
    override fun onRequest(request: Req, buffer: ByteBuffer?) {
        mResponseCodec.encode(buffer!!, object : CodecCallback {
            override fun onPending(buffer: ByteBuffer?) {}
            override fun onProcess(buffer: ByteBuffer?) {}

            @Throws(IOException::class)
            override fun onEncrypt(buffer: ByteBuffer?) {
                // The encrypt request data is sent to remote server
                mRequest!!.process(buffer)
            }

            override fun onDecrypt(buffer: ByteBuffer?) {}
        })
    }

    @Throws(IOException::class)
    override fun onResponse(response: Res, buffer: ByteBuffer?) {
        mRequestCodec.encode(buffer!!, object : CodecCallback {
            override fun onPending(buffer: ByteBuffer?) {}
            override fun onProcess(buffer: ByteBuffer?) {}

            @Throws(IOException::class)
            override fun onEncrypt(buffer: ByteBuffer?) {
                // The encrypt response data is sent to proxy server
                mResponse!!.process(buffer)
            }

            override fun onDecrypt(buffer: ByteBuffer?) {}
        })
    }

    @Throws(IOException::class)
    private fun decodeRequest(chain: ReqChain, buffer: ByteBuffer) {
        // Merge buffers
        mRequestCodec.decode(mergeRequestBuffer(buffer),
            object : CodecCallback {
                override fun onPending(buffer: ByteBuffer?) {
                    pendRequestBuffer(buffer!!)
                }

                @Throws(IOException::class)
                override fun onProcess(buffer: ByteBuffer?) {
                    chain!!.processFinal(buffer)
                }

                @Throws(IOException::class)
                override fun onEncrypt(buffer: ByteBuffer?) {
                    mResponse!!.process(buffer)
                }

                @Throws(IOException::class)
                override fun onDecrypt(buffer: ByteBuffer?) {
                    chain!!.process(buffer)
                }
            })
    }

    @Throws(IOException::class)
    private fun decodeResponse(chain: ResChain, buffer: ByteBuffer) {
        // Merge buffers
        mResponseCodec.decode(mergeResponseBuffer(buffer),
            object : CodecCallback {
                override fun onPending(buffer: ByteBuffer?) {
                    pendResponseBuffer(buffer!!)
                }

                @Throws(IOException::class)
                override fun onProcess(buffer: ByteBuffer?) {
                    chain!!.processFinal(buffer)
                }

                @Throws(IOException::class)
                override fun onEncrypt(buffer: ByteBuffer?) {
                    mRequest!!.process(buffer)
                }

                @Throws(IOException::class)
                override fun onDecrypt(buffer: ByteBuffer?) {
                    chain!!.process(buffer)
                }
            })
    }

    init {
        mRequestCodec = SSLRequestCodec(mEngineFactory)
        mRequestCodec.setRequest(mRequest)
        mResponseCodec = SSLResponseCodec(mEngineFactory)
        mResponseCodec.setRequest(mRequest)
        mLog = NetBareXLog(mRequest!!.protocol(), mRequest.ip(), mRequest.port())
    }
}