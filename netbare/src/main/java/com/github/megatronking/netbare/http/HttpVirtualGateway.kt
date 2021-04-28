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

import com.github.megatronking.netbare.gateway.*
import com.github.megatronking.netbare.http2.Http2DecodeInterceptor
import com.github.megatronking.netbare.http2.Http2EncodeInterceptor
import com.github.megatronking.netbare.net.Session
import com.github.megatronking.netbare.ssl.*
import com.github.megatronking.netbare.tcp.TcpVirtualGateway
import java.io.IOException
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.util.*

/**
 * A [VirtualGateway] that is responsible for HTTP(S) packets interception. It integrates
 * several internal [HttpInterceptor]s to decode and parse HTTP(S) packets. And also it
 * supports extensional [HttpInterceptor]s. Use [HttpVirtualGatewayFactory] to
 * create an instance.
 *
 * @author Megatron King
 * @since 2018-11-20 23:43
 */
/* package */
internal class HttpVirtualGateway(
    session: Session, request: Request, response: Response, jks: JKS,
    factories: List<HttpInterceptorFactory>
) : TcpVirtualGateway(session, request, response) {
    private val mHttpZygoteRequest: HttpZygoteRequest
    private val mHttpZygoteResponse: HttpZygoteResponse
    private val mInterceptors: MutableList<HttpInterceptor?>
    @Throws(IOException::class)
    public override fun onSpecRequest(buffer: ByteBuffer?) {
        HttpRequestChain(mHttpZygoteRequest, mInterceptors).process(buffer)
    }

    @Throws(IOException::class)
    public override fun onSpecResponse(buffer: ByteBuffer?) {
        HttpResponseChain(mHttpZygoteResponse, mInterceptors).process(buffer)
    }

    public override fun onSpecRequestFinished() {
        for (interceptor in mInterceptors) {
            interceptor!!.onRequestFinished(mHttpZygoteRequest)
        }
    }

    public override fun onSpecResponseFinished() {
        for (interceptor in mInterceptors) {
            interceptor!!.onResponseFinished(mHttpZygoteResponse)
        }
    }

    /* package */
    init {
        val sessionFactory = HttpSessionFactory()
        mHttpZygoteRequest = HttpZygoteRequest(request, sessionFactory)
        mHttpZygoteResponse = HttpZygoteResponse(response, sessionFactory)
        val sslEngineFactory: SSLEngineFactory?
        sslEngineFactory = try {
            SSLEngineFactory.Companion.get(jks)
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: IOException) {
            null
        }

        // Add default interceptors.
        val codecInterceptor = HttpSSLCodecInterceptor(sslEngineFactory, request, response)
        mInterceptors = ArrayList(8)
        mInterceptors.add(HttpSniffInterceptor(sessionFactory.create(session.id)))
        mInterceptors.add(codecInterceptor)
        mInterceptors.add(Http2SniffInterceptor(codecInterceptor))
        mInterceptors.add(
            Http2DecodeInterceptor(
                codecInterceptor,
                mHttpZygoteRequest,
                mHttpZygoteResponse
            )
        )
        mInterceptors.add(HttpMultiplexInterceptor(mHttpZygoteRequest, mHttpZygoteResponse))
        mInterceptors.add(HttpHeaderSniffInterceptor(codecInterceptor))
        mInterceptors.add(ContainerHttpInterceptor {
            val subs: MutableList<HttpInterceptor?> = ArrayList(factories.size + 2)
            subs.add(HttpHeaderSeparateInterceptor())
            subs.add(HttpHeaderParseInterceptor())
            // Add extension interceptors.
            for (factory in factories) {
                subs.add(factory.create())
            }
            subs
        })
        // Goalkeepers.
        mInterceptors.add(mInterceptors.size, Http2EncodeInterceptor())
        mInterceptors.add(mInterceptors.size, HttpSSLRefluxInterceptor(codecInterceptor))

        //
        // SSL Flow Model:
        //
        //        Request                                  Response
        //
        //     out        in                             in        out
        //      ⇈         ⇊                               ⇊         ⇈
        //       Encrypted                                 Encrypted
        //      ⇈         ⇊                               ⇊         ⇈
        //   -----------------------------------------------------------
        //  |                     Codec Interceptor                     |
        //   -----------------------------------------------------------
        //      ⇈  |      ⇊              ...              ⇊      |  ⇈
        //         |      ⇊              ...              ⇊      |
        //      ⇈  |  Decrypted  |   interceptors  |  Decrypted  |  ⇈
        //         |      ⇊              ...              ⇊      |
        //      ⇈  |      ⇊              ...              ⇊      |  ⇈
        //   -----------------------------------------------------------
        //  |                     Reflux Interceptor                    |
        //   -----------------------------------------------------------
        //      ⇈ ⇇  ⇇  ⇇ ⇊                               ⇊ ⇉  ⇉  ⇉ ⇈
        //
    }
}