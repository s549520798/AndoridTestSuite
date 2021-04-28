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

import com.github.megatronking.netbare.NetBareLog
import com.github.megatronking.netbare.ssl.SSLEngineFactory
import com.github.megatronking.netbare.ssl.SSLRequestCodec
import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationTargetException
import java.nio.charset.Charset
import javax.net.ssl.SSLEngine

/**
 * Http request ssl codec enables Application-Layer Protocol Negotiation(ALPN).
 *
 * See http://tools.ietf.org/html/draft-agl-tls-nextprotoneg-04#page-4
 *
 * @author Megatron King
 * @since 2019/1/3 23:01
 */
/* package */
internal class HttpSSLRequestCodec  /* package */
    (factory: SSLEngineFactory?) : SSLRequestCodec(factory) {
    private var mSelectedAlpnProtocol: HttpProtocol? = null
    private var mAlpnEnabled = false
    private var mSelectedAlpnResolved = false
    override fun createEngine(factory: SSLEngineFactory?): SSLEngine? {
        return enableALPN(super.createEngine(factory))
    }

    fun setSelectedAlpnProtocol(protocol: HttpProtocol?) {
        mSelectedAlpnProtocol = protocol
    }

    fun setSelectedAlpnResolved() {
        mSelectedAlpnResolved = true
    }

    fun selectedAlpnResolved(): Boolean {
        return mSelectedAlpnResolved
    }

    private fun enableALPN(sslEngine: SSLEngine?): SSLEngine? {
        if (sslEngine == null) {
            return null
        }
        if (mAlpnEnabled) {
            return sslEngine
        }
        mAlpnEnabled = true
        // Nothing to enable.
        if (mSelectedAlpnProtocol == null) {
            return sslEngine
        }
        try {
            val sslEngineName = sslEngine.javaClass.simpleName
            if (sslEngineName == "Java8EngineWrapper") {
                enableJava8EngineWrapperAlpn(sslEngine)
            } else if (sslEngineName == "ConscryptEngine") {
                enableConscryptEngineAlpn(sslEngine)
            } else {
                enableOpenSSLEngineImplAlpn(sslEngine)
            }
        } catch (e: NoSuchFieldException) {
            NetBareLog.wtf(e)
        } catch (e: IllegalAccessException) {
            NetBareLog.wtf(e)
        } catch (e: NoSuchMethodException) {
            NetBareLog.wtf(e)
        } catch (e: InvocationTargetException) {
            NetBareLog.wtf(e)
        }
        return sslEngine
    }

    @Throws(
        NoSuchMethodException::class,
        IllegalAccessException::class,
        InvocationTargetException::class
    )
    private fun enableJava8EngineWrapperAlpn(sslEngine: SSLEngine) {
        val setApplicationProtocolsMethod = sslEngine.javaClass.getDeclaredMethod(
            "setApplicationProtocols", Array<String>::class.java
        )
        setApplicationProtocolsMethod.isAccessible = true
        val protocols = arrayOf(mSelectedAlpnProtocol.toString().toLowerCase())
        setApplicationProtocolsMethod.invoke(sslEngine, *arrayOf<Any>(protocols))
    }

    @Throws(
        NoSuchMethodException::class,
        IllegalAccessException::class,
        InvocationTargetException::class
    )
    private fun enableConscryptEngineAlpn(sslEngine: SSLEngine) {
        val setAlpnProtocolsMethod = sslEngine.javaClass.getDeclaredMethod(
            "setAlpnProtocols", Array<String>::class.java
        )
        setAlpnProtocolsMethod.isAccessible = true
        val protocols = arrayOf(mSelectedAlpnProtocol.toString().toLowerCase())
        setAlpnProtocolsMethod.invoke(sslEngine, *arrayOf<Any>(protocols))
    }

    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    private fun enableOpenSSLEngineImplAlpn(sslEngine: SSLEngine) {
        val sslParametersField = sslEngine.javaClass.getDeclaredField("sslParameters")
        sslParametersField.isAccessible = true
        val sslParameters = sslParametersField[sslEngine]
        if (sslParameters != null) {
            val alpnProtocolsField = sslParameters.javaClass.getDeclaredField("alpnProtocols")
            alpnProtocolsField.isAccessible = true
            alpnProtocolsField[sslParameters] = concatLengthPrefixed()
        }
    }

    private fun concatLengthPrefixed(): ByteArray {
        val os = ByteArrayOutputStream()
        val protocolStr = mSelectedAlpnProtocol.toString().toLowerCase()
        os.write(protocolStr.length)
        os.write(protocolStr.toByteArray(Charset.forName("UTF-8")), 0, protocolStr.length)
        return os.toByteArray()
    }
}