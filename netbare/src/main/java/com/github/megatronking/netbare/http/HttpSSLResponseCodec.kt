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

import android.annotation.SuppressLint
import com.github.megatronking.netbare.NetBareLog
import com.github.megatronking.netbare.ssl.SSLEngineFactory
import com.github.megatronking.netbare.ssl.SSLResponseCodec
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import javax.net.ssl.SSLEngine

/**
 * Http SSL codec enables Application-Layer Protocol Negotiation(ALPN).
 *
 * See http://tools.ietf.org/html/draft-agl-tls-nextprotoneg-04#page-4
 *
 * @author Megatron King
 * @since 2019/1/3 23:31
 */
/* package */
internal class HttpSSLResponseCodec  /* package */
    (factory: SSLEngineFactory?) : SSLResponseCodec(factory) {
    private var mSSLEngine: SSLEngine? = null
    private var mAlpnEnabled = false
    private var mSelectedAlpnResolved = false
    private var mClientAlpns: Array<HttpProtocol?>?
    private var mAlpnCallback: AlpnResolvedCallback? = null
    override fun createEngine(factory: SSLEngineFactory?): SSLEngine? {
        if (mSSLEngine == null) {
            mSSLEngine = super.createEngine(factory)
            if (mSSLEngine != null && mClientAlpns != null) {
                enableAlpn()
            }
        }
        return mSSLEngine
    }

    @Throws(IOException::class)
    override fun decode(buffer: ByteBuffer?, @NonNull callback: CodecCallback) {
        super.decode(buffer, callback)
        // ALPN is put in ServerHello, once we receive the remote server packet, the ALPN must be
        // resolved.
        if (!mSelectedAlpnResolved) {
            mAlpnCallback!!.onResult(alpnSelectedProtocol)
        }
        mSelectedAlpnResolved = true
    }

    fun setSelectedAlpnResolved() {
        mSelectedAlpnResolved = true
    }

    @Throws(IOException::class)
    fun prepareHandshake(protocols: Array<HttpProtocol?>?, callback: AlpnResolvedCallback?) {
        mClientAlpns = protocols
        mAlpnCallback = callback
        super.prepareHandshake()
    }

    private fun enableAlpn() {
        try {
            val sslEngineName = mSSLEngine!!.javaClass.simpleName
            if (sslEngineName == "Java8EngineWrapper") {
                enableJava8EngineWrapperAlpn()
            } else if (sslEngineName == "ConscryptEngine") {
                enableConscryptEngineAlpn()
            } else {
                enableOpenSSLEngineImplAlpn()
            }
            mAlpnEnabled = true
        } catch (e: NoSuchFieldException) {
            NetBareLog.wtf(e)
        } catch (e: IllegalAccessException) {
            NetBareLog.wtf(e)
        } catch (e: NoSuchMethodException) {
            NetBareLog.wtf(e)
        } catch (e: InvocationTargetException) {
            NetBareLog.wtf(e)
        }
    }

    @Throws(
        NoSuchMethodException::class,
        IllegalAccessException::class,
        InvocationTargetException::class
    )
    private fun enableJava8EngineWrapperAlpn() {
        val setApplicationProtocolsMethod = mSSLEngine!!.javaClass.getDeclaredMethod(
            "setApplicationProtocols", Array<String>::class.java
        )
        setApplicationProtocolsMethod.isAccessible = true
        val protocols = arrayOfNulls<String>(mClientAlpns!!.size)
        for (i in protocols.indices) {
            protocols[i] = mClientAlpns!![i].toString().toLowerCase()
        }
        setApplicationProtocolsMethod.invoke(mSSLEngine, *arrayOf<Any>(protocols))
        val setUseSessionTicketsMethod = mSSLEngine!!.javaClass.getDeclaredMethod(
            "setUseSessionTickets", Boolean::class.javaPrimitiveType
        )
        setUseSessionTicketsMethod.isAccessible = true
        setUseSessionTicketsMethod.invoke(mSSLEngine, true)
    }

    @Throws(
        NoSuchMethodException::class,
        IllegalAccessException::class,
        InvocationTargetException::class
    )
    private fun enableConscryptEngineAlpn() {
        val setAlpnProtocolsMethod = mSSLEngine!!.javaClass.getDeclaredMethod(
            "setAlpnProtocols", Array<String>::class.java
        )
        setAlpnProtocolsMethod.isAccessible = true
        val protocols = arrayOfNulls<String>(mClientAlpns!!.size)
        for (i in protocols.indices) {
            protocols[i] = mClientAlpns!![i].toString().toLowerCase()
        }
        setAlpnProtocolsMethod.invoke(mSSLEngine, *arrayOf<Any>(protocols))
        val setUseSessionTicketsMethod = mSSLEngine!!.javaClass.getDeclaredMethod(
            "setUseSessionTickets", Boolean::class.javaPrimitiveType
        )
        setUseSessionTicketsMethod.isAccessible = true
        setUseSessionTicketsMethod.invoke(mSSLEngine, true)
    }

    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    private fun enableOpenSSLEngineImplAlpn() {
        val sslParametersField = mSSLEngine!!.javaClass.getDeclaredField("sslParameters")
        sslParametersField.isAccessible = true
        val sslParameters = sslParametersField[mSSLEngine]
            ?: throw IllegalAccessException("sslParameters value is null")
        val useSessionTicketsField = sslParameters.javaClass.getDeclaredField("useSessionTickets")
        useSessionTicketsField.isAccessible = true
        useSessionTicketsField[sslParameters] = true
        val useSniField = sslParameters.javaClass.getDeclaredField("useSni")
        useSniField.isAccessible = true
        useSniField[sslParameters] = true
        val alpnProtocolsField = sslParameters.javaClass.getDeclaredField("alpnProtocols")
        alpnProtocolsField.isAccessible = true
        alpnProtocolsField[sslParameters] = concatLengthPrefixed(*mClientAlpns)
    }

    private fun concatLengthPrefixed(vararg protocols: HttpProtocol): ByteArray {
        val os = ByteArrayOutputStream()
        for (protocol in protocols) {
            val protocolStr = protocol.toString().toLowerCase()
            os.write(protocolStr.length)
            os.write(protocolStr.toByteArray(Charset.forName("UTF-8")), 0, protocolStr.length)
        }
        return os.toByteArray()
    }

    @get:SuppressLint("PrivateApi")
    private val alpnSelectedProtocol: String?
        private get() {
            if (!mAlpnEnabled) {
                return null
            }
            var alpnResult: String? = null
            try {
                val sslEngineName = mSSLEngine!!.javaClass.simpleName
                alpnResult = if (sslEngineName == "Java8EngineWrapper") {
                    java8EngineWrapperAlpn
                } else if (sslEngineName == "ConscryptEngine") {
                    conscryptEngineAlpn
                } else {
                    openSSLEngineImplAlpn
                }
            } catch (e: ClassNotFoundException) {
                NetBareLog.e(e.message)
            } catch (e: NoSuchMethodException) {
                NetBareLog.e(e.message)
            } catch (e: NoSuchFieldException) {
                NetBareLog.e(e.message)
            } catch (e: IllegalAccessException) {
                NetBareLog.e(e.message)
            } catch (e: InvocationTargetException) {
                NetBareLog.e(e.message)
            }
            return alpnResult
        }

    @get:Throws(
        NoSuchMethodException::class,
        IllegalAccessException::class,
        InvocationTargetException::class
    )
    private val java8EngineWrapperAlpn: String
        private get() {
            val getApplicationProtocolMethod = mSSLEngine!!.javaClass.getDeclaredMethod(
                "getApplicationProtocol"
            )
            getApplicationProtocolMethod.isAccessible = true
            return getApplicationProtocolMethod.invoke(mSSLEngine) as String
        }

    @get:Throws(
        NoSuchMethodException::class,
        IllegalAccessException::class,
        InvocationTargetException::class
    )
    private val conscryptEngineAlpn: String?
        private get() {
            val getAlpnSelectedProtocolMethod = mSSLEngine!!.javaClass.getDeclaredMethod(
                "getAlpnSelectedProtocol"
            )
            getAlpnSelectedProtocolMethod.isAccessible = true
            val selectedProtocol = getAlpnSelectedProtocolMethod.invoke(mSSLEngine) as ByteArray
            return if (selectedProtocol != null) String(
                selectedProtocol,
                Charset.forName("UTF-8")
            ) else null
        }

    @get:Throws(
        ClassNotFoundException::class,
        NoSuchMethodException::class,
        NoSuchFieldException::class,
        IllegalAccessException::class,
        InvocationTargetException::class
    )
    @get:SuppressLint("PrivateApi")
    private val openSSLEngineImplAlpn: String?
        private get() {
            val nativeCryptoClass = Class.forName("com.android.org.conscrypt.NativeCrypto")
            val SSL_get0_alpn_selectedMethod = nativeCryptoClass.getDeclaredMethod(
                "SSL_get0_alpn_selected", Long::class.javaPrimitiveType
            )
            SSL_get0_alpn_selectedMethod.isAccessible = true
            val sslNativePointerField = mSSLEngine!!.javaClass.getDeclaredField("sslNativePointer")
            sslNativePointerField.isAccessible = true
            val sslNativePointer = sslNativePointerField[mSSLEngine] as Long
            val selectedProtocol =
                SSL_get0_alpn_selectedMethod.invoke(null, sslNativePointer) as ByteArray
            return if (selectedProtocol != null) String(
                selectedProtocol,
                Charset.forName("UTF-8")
            ) else null
        }

    internal interface AlpnResolvedCallback {
        @Throws(IOException::class)
        fun onResult(selectedAlpnProtocol: String?)
    }
}