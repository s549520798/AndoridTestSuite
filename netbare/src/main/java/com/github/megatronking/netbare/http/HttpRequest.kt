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

import com.github.megatronking.netbare.gateway.Request
import com.github.megatronking.netbare.http2.Http2Settings
import com.github.megatronking.netbare.ip.Protocol
import java.io.IOException
import java.nio.ByteBuffer

/**
 * It is an implementation of [Request] represents the HTTP protocol. Instances of this
 * class are not immutable.
 *
 * @author Megatron King
 * @since 2018-11-11 23:37
 */
open class HttpRequest  /* package */ internal constructor(
    private val mRequest: Request,
    private val mHttpId: HttpId?,
    private val mSession: HttpSession?
) : Request() {
    /* package */
    internal constructor(request: Request, session: HttpSession?) : this(request, null, session) {}

    /* package */
    fun session(): HttpSession? {
        return mSession
    }

    @Throws(IOException::class)
    override fun process(buffer: ByteBuffer?) {
        mRequest.process(buffer)
    }

    override fun id(): String? {
        return if (mHttpId != null) mHttpId.id else mRequest.id()
    }

    override fun time(): Long {
        return mHttpId?.time ?: mRequest.time()
    }

    override fun uid(): Int {
        return mRequest.uid()
    }

    override fun ip(): String? {
        return mRequest.ip()
    }

    override fun port(): Int {
        return mRequest.port()
    }

    override fun protocol(): Protocol? {
        return mRequest.protocol()
    }

    override fun host(): String? {
        return mRequest.host()
    }

    /**
     * Returns the request method for this request.
     *
     * @return The request method.
     */
    fun method(): HttpMethod? {
        return mSession!!.method
    }

    /**
     * Returns this request's http protocol, such as [HttpProtocol.HTTP_1_1] or
     * [HttpProtocol.HTTP_1_0].
     *
     * @return The request protocol.
     */
    fun httpProtocol(): HttpProtocol? {
        return mSession!!.protocol
    }

    /**
     * Returns this request's path.
     *
     * @return The request path.
     */
    fun path(): String? {
        return mSession!!.path
    }

    /**
     * Whether the request is a HTTPS request.
     *
     * @return HTTPS returns true.
     */
    val isHttps: Boolean
        get() = mSession!!.isHttps

    /**
     * Returns this request's URL.
     *
     * @return The request URL.
     */
    fun url(): String {
        val path = if (path() == null) "" else path()!!
        return (if (isHttps) "https://" else "http://") + host() + path
    }

    /**
     * Returns this request's headers.
     *
     * @return A map of headers.
     */
    fun requestHeaders(): Map<String?, MutableList<String?>?>? {
        return mSession!!.requestHeaders
    }

    /**
     * Returns this request's header values by name.
     *
     * @param name A header name.
     * @return A collection of header values.
     */
    fun requestHeader(name: String?): List<String?>? {
        return requestHeaders()!![name]
    }

    /**
     * Returns the offset of request body's starting index in request data.
     *
     * @return Offset of request body.
     */
    fun requestBodyOffset(): Int {
        return mSession!!.reqBodyOffset
    }

    /**
     * Returns the HTTP/2 stream id.
     *
     * @return A stream id.
     */
    fun streamId(): Int {
        return mHttpId?.streamId ?: -1
    }

    /**
     * Returns the HTTP/2 client settings.
     *
     * @return Client settings.
     */
    fun clientHttp2Settings(): Http2Settings? {
        return mSession!!.clientHttp2Settings
    }

    /**
     * Returns the HTTP/2 peer settings.
     *
     * @return Client settings.
     */
    fun peerHttp2Settings(): Http2Settings? {
        return mSession!!.peerHttp2Settings
    }

    /**
     * Whether the current HTTP2 request stream is end.
     *
     * @return End is true.
     */
    fun requestStreamEnd(): Boolean {
        return mSession!!.requestStreamEnd
    }
}