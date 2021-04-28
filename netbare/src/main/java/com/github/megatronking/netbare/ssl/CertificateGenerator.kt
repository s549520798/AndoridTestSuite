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
import com.github.megatronking.netbare.NetBareUtils
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.bc.BcX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.OperatorCreationException
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.math.BigInteger
import java.security.*
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.*

/**
 * Generates self-signed certificate by [JKS].
 *
 * @author Megatron King
 * @since 2018-11-08 02:23
 */
class CertificateGenerator {
    /**
     * Generate a root keystore by a given [JKS].
     *
     * @param jks A java keystore object.
     * @return A root [KeyStore].
     */
    @Throws(
        KeyStoreException::class,
        CertificateException::class,
        NoSuchAlgorithmException::class,
        IOException::class,
        OperatorCreationException::class
    )
    fun generateRoot(jks: JKS): KeyStore {
        val keyPair = generateKeyPair(ROOT_KEY_SIZE)
        val nameBuilder = X500NameBuilder(BCStyle.INSTANCE)
        nameBuilder.addRDN(BCStyle.CN, jks.commonName())
        nameBuilder.addRDN(BCStyle.O, jks.organization())
        nameBuilder.addRDN(BCStyle.OU, jks.organizationalUnitName())
        val issuer = nameBuilder.build()
        val pubKey = keyPair.public
        val generator: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
            issuer, BigInteger.valueOf(randomSerial()), NOT_BEFORE, NOT_AFTER, issuer, pubKey
        )
        generator.addExtension(
            Extension.subjectKeyIdentifier, false,
            createSubjectKeyIdentifier(pubKey)
        )
        generator.addExtension(
            Extension.basicConstraints, true,
            BasicConstraints(true)
        )
        val usage = KeyUsage(
            KeyUsage.keyCertSign or KeyUsage.digitalSignature or
                    KeyUsage.keyEncipherment or KeyUsage.dataEncipherment or KeyUsage.cRLSign
        )
        generator.addExtension(Extension.keyUsage, false, usage)
        val purposes = ASN1EncodableVector()
        purposes.add(KeyPurposeId.id_kp_serverAuth)
        purposes.add(KeyPurposeId.id_kp_clientAuth)
        purposes.add(KeyPurposeId.anyExtendedKeyUsage)
        generator.addExtension(
            Extension.extendedKeyUsage, false,
            DERSequence(purposes)
        )
        val cert = signCertificate(generator, keyPair.private)
        val result = KeyStore.getInstance(KEY_STORE_TYPE)
        result.load(null, null)
        result.setKeyEntry(jks.alias(), keyPair.private, jks.password(), arrayOf<Certificate>(cert))
        return result
    }

    @Throws(
        NoSuchAlgorithmException::class,
        NoSuchProviderException::class,
        IOException::class,
        OperatorCreationException::class,
        CertificateException::class,
        InvalidKeyException::class,
        SignatureException::class,
        KeyStoreException::class
    )
    fun generateServer(
        commonName: String?,
        jks: JKS,
        caCert: Certificate?,
        caPrivKey: PrivateKey?
    ): KeyStore {
        val keyPair = generateKeyPair(SERVER_KEY_SIZE)
        val issuer = X509CertificateHolder(caCert!!.encoded).subject
        val serial = BigInteger.valueOf(randomSerial())
        val name = X500NameBuilder(BCStyle.INSTANCE)
        name.addRDN(BCStyle.CN, commonName)
        name.addRDN(BCStyle.O, jks.certOrganisation())
        name.addRDN(BCStyle.OU, jks.certOrganizationalUnitName())
        val subject = name.build()
        val builder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
            issuer, serial, NOT_BEFORE,
            Date(System.currentTimeMillis() + ONE_DAY), subject, keyPair.public
        )
        builder.addExtension(
            Extension.subjectKeyIdentifier, false,
            createSubjectKeyIdentifier(keyPair.public)
        )
        builder.addExtension(
            Extension.basicConstraints, false,
            BasicConstraints(false)
        )
        builder.addExtension(
            Extension.subjectAlternativeName, false,
            DERSequence(GeneralName(GeneralName.dNSName, commonName))
        )
        val cert = signCertificate(builder, caPrivKey)
        cert.checkValidity(Date())
        cert.verify(caCert.publicKey)
        val result = KeyStore.getInstance(KeyStore.getDefaultType())
        result.load(null, null)
        val chain = arrayOf<Certificate?>(cert, caCert)
        result.setKeyEntry(jks.alias(), keyPair.private, jks.password(), chain)
        return result
    }

    fun keyStoreType(): String {
        return KEY_STORE_TYPE
    }

    @Throws(NoSuchAlgorithmException::class)
    private fun generateKeyPair(keySize: Int): KeyPair {
        val generator = KeyPairGenerator.getInstance(KEYGEN_ALGORITHM)
        val secureRandom = SecureRandom.getInstance(SECURE_RANDOM_ALGORITHM)
        generator.initialize(keySize, secureRandom)
        return generator.generateKeyPair()
    }

    private fun randomSerial(): Long {
        val rnd = Random()
        rnd.setSeed(System.currentTimeMillis())
        // prevent browser certificate caches, cause of doubled serial numbers
        // using 48bit random number
        var sl = rnd.nextInt().toLong() shl 32 or (rnd.nextInt().toLong() and 0xFFFFFFFFL)
        // let reserve of 16 bit for increasing, serials have to be positive
        sl = sl and 0x0000FFFFFFFFFFFFL
        return sl
    }

    companion object {
        private const val KEY_STORE_TYPE = "PKCS12"
        private const val KEYGEN_ALGORITHM = "RSA"
        private const val SECURE_RANDOM_ALGORITHM = "SHA1PRNG"
        private const val PROVIDER_NAME = BouncyCastleProvider.PROVIDER_NAME
        private const val ROOT_KEY_SIZE = 2048
        private const val SERVER_KEY_SIZE = 1024

        /**
         * The signature algorithm starting with the message digest to use when signing certificates.
         * On 64-bit systems this should be set to SHA512, on 32-bit systems this is SHA256. On 64-bit
         * systems, SHA512 generally performs better than SHA256; see this question for details:
         * http://crypto.stackexchange.com/questions/26336/sha512-faster-than-sha256
         */
        private val SIGNATURE_ALGORITHM = (if (is32BitJvm()) "SHA256" else "SHA512") +
                "WithRSAEncryption"

        /**
         * The milliseconds of 1 day.
         */
        private const val ONE_DAY = 86400000L

        /**
         * Current time minus 1 year, just in case software clock goes back due to time synchronization.
         */
        private val NOT_BEFORE = Date(System.currentTimeMillis() - ONE_DAY * 365)

        /**
         * The maximum possible value in X.509 specification: 9999-12-31 23:59:59,
         * new Date(253402300799000L), but Apple iOS 8 fails with a certificate
         * expiration date grater than Mon, 24 Jan 6084 02:07:59 GMT (issue #6).
         *
         * Hundred years in the future from starting the proxy should be enough.
         */
        private val NOT_AFTER = Date(System.currentTimeMillis() + ONE_DAY * 365 * 10)
        @Throws(IOException::class)
        private fun createSubjectKeyIdentifier(key: Key): SubjectKeyIdentifier {
            val bIn = ByteArrayInputStream(key.encoded)
            var `is`: ASN1InputStream? = null
            return try {
                `is` = ASN1InputStream(bIn)
                val seq = `is`.readObject() as ASN1Sequence
                val info = SubjectPublicKeyInfo.getInstance(seq)
                BcX509ExtensionUtils().createSubjectKeyIdentifier(info)
            } finally {
                NetBareUtils.closeQuietly(`is`)
            }
        }

        @Throws(OperatorCreationException::class, CertificateException::class)
        private fun signCertificate(
            certificateBuilder: X509v3CertificateBuilder,
            signedWithPrivateKey: PrivateKey?
        ): X509Certificate {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signer = JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                    .build(signedWithPrivateKey)
                JcaX509CertificateConverter()
                    .getCertificate(certificateBuilder.build(signer))
            } else {
                val signer = JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                    .setProvider(PROVIDER_NAME)
                    .build(signedWithPrivateKey)
                JcaX509CertificateConverter()
                    .setProvider(PROVIDER_NAME)
                    .getCertificate(certificateBuilder.build(signer))
            }
        }

        /**
         * Uses the non-portable system property sun.arch.data.model to help
         * determine if we are running on a 32-bit JVM. Since the majority of modern
         * systems are 64 bits, this method "assumes" 64 bits and only returns true
         * if sun.arch.data.model explicitly indicates a 32-bit JVM.
         *
         * @return true if we can determine definitively that this is a 32-bit JVM,
         * otherwise false
         */
        private fun is32BitJvm(): Boolean {
            val bits = Integer.getInteger("sun.arch.data.model")
            return bits != null && bits == 32
        }
    }
}