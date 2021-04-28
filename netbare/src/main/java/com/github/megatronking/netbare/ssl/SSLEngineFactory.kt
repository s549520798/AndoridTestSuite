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

import android.os.Build
import com.github.megatronking.netbare.NetBareLog
import com.github.megatronking.netbare.NetBareUtils
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import org.bouncycastle.operator.OperatorCreationException
import java.io.FileInputStream
import java.io.IOException
import java.security.*
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

/**
 * A factory produces client and server MITM [SSLEngine].
 *
 * @author Megatron King
 * @since 2018-11-10 23:43
 */
class SSLEngineFactory(private val mJKS: JKS) {
    private val mGenerator: CertificateGenerator
    private var mCaCert: Certificate? = null
    private var mCaPrivKey: PrivateKey? = null

    companion object {
        private const val ALIVE_MINUTES = 10
        private const val CONCURRENCY_LEVEL = 16

        /**
         * Enforce TLS 1.2 if available, since it's not default up to Java 8.
         *
         *
         * Java 7 disables TLS 1.1 and 1.2 for clients. From [Java Cryptography Architecture Oracle Providers Documentation:](http://docs.oracle.com/javase/7/docs/technotes/guides/security/SunProviders.html)
         * Although SunJSSE in the Java SE 7 release supports TLS 1.1 and TLS 1.2,
         * neither version is enabled by default for client connections. Some
         * servers do not implement forward compatibility correctly and refuse to
         * talk to TLS 1.1 or TLS 1.2 clients. For interoperability, SunJSSE does
         * not enable TLS 1.1 or TLS 1.2 by default for client connections.
         */
        private const val SSL_CONTEXT_PROTOCOL = "TLSv1.2"

        /**
         * [SSLContext]: Every implementation of the Java platform is required
         * to support the following standard SSLContext protocol: TLSv1
         */
        private const val SSL_CONTEXT_FALLBACK_PROTOCOL = "TLSv1"
        private var SERVER_SSL_CONTEXTS: Cache<String, SSLContext>? = null
        private var CLIENT_SSL_CONTEXTS: Cache<String?, SSLContext>? = null

        @Volatile
        private var sEngineFactory: SSLEngineFactory? = null
        private var sKeyManagerProvider: SSLKeyManagerProvider? = null
        private var sTrustManagerProvider: SSLTrustManagerProvider? = null
        fun updateProviders(
            keyManagerProvider: SSLKeyManagerProvider?,
            trustManagerProvider: SSLTrustManagerProvider?
        ) {
            sKeyManagerProvider = keyManagerProvider
            sTrustManagerProvider = trustManagerProvider
            // Clean all context caches.
            SERVER_SSL_CONTEXTS!!.invalidateAll()
            CLIENT_SSL_CONTEXTS!!.invalidateAll()
        }

        @Throws(GeneralSecurityException::class, IOException::class)
        operator fun get(jks: JKS): SSLEngineFactory? {
            if (sEngineFactory == null) {
                synchronized(SSLEngineFactory::class.java) {
                    if (sEngineFactory == null) {
                        sEngineFactory = SSLEngineFactory(jks)
                    }
                }
            }
            return sEngineFactory
        }

        init {
            SERVER_SSL_CONTEXTS = CacheBuilder.newBuilder()
                .expireAfterAccess(ALIVE_MINUTES.toLong(), TimeUnit.MINUTES)
                .concurrencyLevel(CONCURRENCY_LEVEL)
                .build()
            CLIENT_SSL_CONTEXTS = CacheBuilder.newBuilder()
                .expireAfterAccess(ALIVE_MINUTES.toLong(), TimeUnit.MINUTES)
                .concurrencyLevel(CONCURRENCY_LEVEL)
                .build()
        }
    }

    /**
     * Create a MITM server [SSLEngine] with the remote server host.
     *
     * @param host The remote server host.
     * @return A server [SSLEngine] instance.
     * @throws ExecutionException If an execution error has occurred.
     */
    @Throws(ExecutionException::class)
    fun createServerEngine(host: String): SSLEngine {
        val ctx = SERVER_SSL_CONTEXTS!![host, Callable { createServerContext(host) }]
        val engine: SSLEngine
        // On Android 8.1, createSSLEngine will be crashed due to 'Unable to create application data'.
        engine = if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) {
            try {
                ctx.createSSLEngine()
            } catch (e: Exception) {
                throw ExecutionException(e)
            }
        } else {
            ctx.createSSLEngine()
        }
        return engine
    }

    /**
     * Create a client [SSLEngine] with the remote server IP and port.
     *
     * @param host Remote server host.
     * @param port Remote server port.
     * @return A client [SSLEngine] instance.
     * @throws ExecutionException If an execution error has occurred.
     */
    @Throws(ExecutionException::class)
    fun createClientEngine(host: String?, port: Int): SSLEngine {
        val ctx = CLIENT_SSL_CONTEXTS!![host, Callable { createClientContext(host) }]
        val engine = ctx.createSSLEngine(host, port)
        val ciphers: MutableList<String> = LinkedList()
        for (each in engine.enabledCipherSuites) {
            if (each != "TLS_DHE_RSA_WITH_AES_128_CBC_SHA" &&
                each != "TLS_DHE_RSA_WITH_AES_256_CBC_SHA"
            ) {
                ciphers.add(each)
            }
        }
        engine.enabledCipherSuites = ciphers.toTypedArray()
        engine.useClientMode = true
        engine.needClientAuth = false
        return engine
    }

    @Throws(GeneralSecurityException::class, IOException::class)
    private fun initializeSSLContext() {
        val ks = loadKeyStore()
        mCaCert = ks.getCertificate(mJKS.alias())
        mCaPrivKey = ks.getKey(mJKS.alias(), mJKS.password()) as PrivateKey
    }

    @Throws(GeneralSecurityException::class, IOException::class)
    private fun loadKeyStore(): KeyStore {
        val ks = KeyStore.getInstance(mGenerator.keyStoreType())
        var `is`: FileInputStream? = null
        try {
            `is` = FileInputStream(mJKS.aliasFile(JKS.Companion.KEY_STORE_FILE_EXTENSION))
            ks.load(`is`, mJKS.password())
        } finally {
            NetBareUtils.closeQuietly(`is`)
        }
        return ks
    }

    @Throws(GeneralSecurityException::class, IOException::class, OperatorCreationException::class)
    private fun createServerContext(host: String): SSLContext {
        var kms =
            if (sKeyManagerProvider != null) sKeyManagerProvider!!.provide(host, false) else null
        if (kms == null) {
            kms = getServerKeyManagers(host)
        }
        val tms = if (sTrustManagerProvider != null) sTrustManagerProvider!!.provide(
            host,
            false
        ) else null
        return createContext(kms, tms)
    }

    @Throws(GeneralSecurityException::class)
    private fun createClientContext(host: String?): SSLContext {
        val kms =
            if (sKeyManagerProvider != null) sKeyManagerProvider!!.provide(host, true) else null
        var tms =
            if (sTrustManagerProvider != null) sTrustManagerProvider!!.provide(host, true) else null
        if (tms == null) {
            tms = clientTrustManager
        }
        return createContext(kms, tms)
    }

    @Throws(NoSuchAlgorithmException::class, KeyManagementException::class)
    private fun createContext(
        keyManagers: Array<KeyManager?>?,
        trustManagers: Array<TrustManager?>?
    ): SSLContext {
        val result = createSSLContext()
        val random = SecureRandom()
        random.setSeed(System.currentTimeMillis() + 1)
        result.init(keyManagers, trustManagers, random)
        return result
    }

    @Throws(NoSuchAlgorithmException::class)
    private fun createSSLContext(): SSLContext {
        return try {
            SSLContext.getInstance(SSL_CONTEXT_PROTOCOL)
        } catch (e: NoSuchAlgorithmException) {
            SSLContext.getInstance(SSL_CONTEXT_FALLBACK_PROTOCOL)
        }
    }

    @Throws(
        NoSuchAlgorithmException::class,
        UnrecoverableKeyException::class,
        KeyStoreException::class,
        OperatorCreationException::class,
        InvalidKeyException::class,
        IOException::class,
        SignatureException::class,
        NoSuchProviderException::class,
        CertificateException::class
    )
    private fun getServerKeyManagers(host: String): Array<KeyManager?> {
        val keyStore = mGenerator.generateServer(host, mJKS, mCaCert, mCaPrivKey)
        val keyManAlg = KeyManagerFactory.getDefaultAlgorithm()
        val kmf = KeyManagerFactory.getInstance(keyManAlg)
        kmf.init(keyStore, mJKS.password())
        return kmf.keyManagers
    }

    private val clientTrustManager: Array<TrustManager?>?
        private get() {
            try {
                val trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm()
                )
                trustManagerFactory.init(null as KeyStore?)
                val trustManagers = trustManagerFactory.trustManagers
                if (trustManagers.size != 1 || trustManagers[0] !is X509TrustManager) {
                    throw KeyManagementException(
                        "Unexpected default trust managers:"
                                + Arrays.toString(trustManagers)
                    )
                }
                return trustManagers
            } catch (e: NoSuchAlgorithmException) {
                NetBareLog.wtf(e)
            } catch (e: KeyStoreException) {
                NetBareLog.wtf(e)
            } catch (e: KeyManagementException) {
                NetBareLog.wtf(e)
            }
            return null
        }

    /**
     * Constructs the factory with a self-signed certificate.
     *
     * @param jks Java keystore of the self-signed certificate.
     * @throws GeneralSecurityException If a generic security exception has occurred.
     * @throws IOException If an I/O error has occurred.
     */
    init {
        mGenerator = CertificateGenerator()
        initializeSSLContext()
    }
}