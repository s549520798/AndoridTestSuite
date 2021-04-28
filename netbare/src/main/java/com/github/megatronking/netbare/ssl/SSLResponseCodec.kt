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
package com.github.megatronking.netbare.ssl

import com.github.megatronking.netbare.NetBareLog
import com.github.megatronking.netbare.gateway.*
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ExecutionException
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLException

/**
 * An implementation of [SSLCodec] to codec response SSL packets. This codec creates a SSL
 * client engine using [SSLEngineFactory], it requires the remote server ip and host.
 * Before encrypt, should call [.prepareHandshake] to start SSL handshake.
 *
 * @author Megatron King
 * @since 2018-11-16 01:30
 */
open class SSLResponseCodec
/**
 * Constructs an instance of [SSLCodec] by a factory.
 *
 * @param factory A factory produces [SSLEngine].
 */(private val mSSLEngineFactory: SSLEngineFactory?) : SSLCodec(mSSLEngineFactory) {
    private var mRequest: Request? = null
    private var mEngine: SSLEngine? = null

    /**
     * Bind a [Request] to this codec.
     *
     * @param request A request has terminated remote server ip and port.
     */
    fun setRequest(request: Request?) {
        mRequest = request
    }

    override fun createEngine(factory: SSLEngineFactory?): SSLEngine? {
        if (mEngine == null) {
            try {
                val host = if (mRequest!!.host() != null) mRequest!!.host() else mRequest!!.ip()
                mEngine = factory!!.createClientEngine(host, mRequest!!.port())
                mEngine!!.useClientMode = true
            } catch (e: ExecutionException) {
                NetBareLog.e("Failed to create client SSLEngine: " + e.message)
            }
        }
        return mEngine
    }

    /**
     * Prepare and start SSL handshake with the remote server.
     *
     * @throws IOException If an I/O error has occurred.
     */
    @Throws(IOException::class)
    fun prepareHandshake() {
        if (mEngine != null) {
            // The handshake was started.
            return
        }
        val engine = createEngine(mSSLEngineFactory)
            ?: throw SSLException("Failed to create client SSLEngine.")
        val input = ByteBuffer.allocate(0)
        handshake(engine, input, object : CodecCallback {
            override fun onPending(buffer: ByteBuffer?) {}
            override fun onProcess(buffer: ByteBuffer?) {}

            @Throws(IOException::class)
            override fun onEncrypt(buffer: ByteBuffer?) {
                // Send to remote server
                mRequest!!.process(buffer)
            }

            override fun onDecrypt(buffer: ByteBuffer?) {}
        })
    }
}