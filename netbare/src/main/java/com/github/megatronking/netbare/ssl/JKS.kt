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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.security.KeyChain
import com.github.megatronking.netbare.NetBareLog
import com.github.megatronking.netbare.NetBareUtils
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import java.io.*
import java.security.KeyStore

/**
 * A java keystore to manage root certificate.
 *
 * @author Megatron King
 * @since 2018-11-10 20:06
 */
class JKS(
    context: Context, alias: String, password: CharArray,
    commonName: String, organization: String,
    organizationalUnitName: String, certOrganization: String,
    certOrganizationalUnitName: String
) {
    private val keystoreDir: File
    private val alias: String
    private val password: CharArray
    private val commonName: String
    private val organization: String
    private val organizationalUnitName: String
    private val certOrganization: String
    private val certOrganizationalUnitName: String
    fun alias(): String {
        return alias
    }

    fun password(): CharArray {
        return password
    }

    fun commonName(): String {
        return commonName
    }

    fun organization(): String {
        return organization
    }

    fun organizationalUnitName(): String {
        return organizationalUnitName
    }

    fun certOrganisation(): String {
        return certOrganization
    }

    fun certOrganizationalUnitName(): String {
        return certOrganizationalUnitName
    }

    val isInstalled: Boolean
        get() = aliasFile(KEY_STORE_FILE_EXTENSION).exists() &&
                aliasFile(KEY_PEM_FILE_EXTENSION).exists() &&
                aliasFile(KEY_JKS_FILE_EXTENSION).exists()

    fun aliasFile(fileExtension: String): File {
        return File(keystoreDir, alias + fileExtension)
    }

    private fun createKeystore() {
        if (aliasFile(KEY_STORE_FILE_EXTENSION).exists() &&
            aliasFile(KEY_PEM_FILE_EXTENSION).exists()
        ) {
            return
        }

        // Generate keystore in the async thread
        Thread {
            val generator = CertificateGenerator()
            val keystore: KeyStore?
            var os: OutputStream? = null
            var sw: Writer? = null
            var pw: JcaPEMWriter? = null
            try {
                keystore = generator.generateRoot(this@JKS)
                os = FileOutputStream(aliasFile(KEY_STORE_FILE_EXTENSION))
                keystore.store(os, password())
                val cert = keystore.getCertificate(alias())
                sw = FileWriter(aliasFile(KEY_PEM_FILE_EXTENSION))
                pw = JcaPEMWriter(sw)
                pw.writeObject(cert)
                pw.flush()
                NetBareLog.i("Generate keystore succeed.")
            } catch (e: Exception) {
                NetBareLog.e(e.message)
            } finally {
                NetBareUtils.closeQuietly(os)
                NetBareUtils.closeQuietly(sw)
                NetBareUtils.closeQuietly(pw)
            }
        }.start()
    }

    companion object {
        const val KEY_STORE_FILE_EXTENSION = ".p12"
        const val KEY_PEM_FILE_EXTENSION = ".pem"
        const val KEY_JKS_FILE_EXTENSION = ".jks"

        /**
         * Whether the certificate with given alias has been installed.
         *
         * @param context Any context.
         * @param alias Key store alias.
         * @return True if the certificate has been installed.
         */
        fun isInstalled(context: Context, alias: String): Boolean {
            return File(
                context.cacheDir,
                alias + KEY_JKS_FILE_EXTENSION
            ).exists()
        }

        /**
         * Install the self-signed root certificate.
         *
         * @param context Any context.
         * @param name Key chain name.
         * @param alias Key store alias.
         * @throws IOException If an IO error has occurred.
         */
        @Throws(IOException::class)
        fun install(context: Context, name: String?, alias: String) {
            val keychain: ByteArray
            var `is`: FileInputStream? = null
            try {
                `is` = FileInputStream(
                    File(
                        context.cacheDir,
                        alias + KEY_PEM_FILE_EXTENSION
                    )
                )
                keychain = ByteArray(`is`.available())
                val len = `is`.read(keychain)
                if (len != keychain.size) {
                    throw IOException("Install JKS failed, len: $len")
                }
            } finally {
                NetBareUtils.closeQuietly(`is`)
            }
            val intent = Intent(context, CertificateInstallActivity::class.java)
            intent.putExtra(KeyChain.EXTRA_CERTIFICATE, keychain)
            intent.putExtra(KeyChain.EXTRA_NAME, name)
            intent.putExtra(CertificateInstallActivity.Companion.EXTRA_ALIAS, alias)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    init {
        keystoreDir = context.cacheDir
        this.alias = alias
        this.password = password
        this.commonName = commonName
        this.organization = organization
        this.organizationalUnitName = organizationalUnitName
        this.certOrganization = certOrganization
        this.certOrganizationalUnitName = certOrganizationalUnitName
        createKeystore()
    }
}