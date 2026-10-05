package com.aidex.build

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Manages debug and custom keystores for APK signing.
 *
 * Debug mode: auto-generates filesDir/toolchain/aidex-debug.keystore on first
 * use. Alias "aidex", password "android". Same behaviour as Android Studio.
 *
 * Custom mode: user supplies a .jks / .keystore file via SAF plus alias and
 * password. The chosen path + credentials are persisted in SharedPreferences.
 */
object KeystoreManager {

    const val DEBUG_ALIAS = "aidex"
    const val DEBUG_PASSWORD = "android"
    private const val DEBUG_STORE = "aidex-debug.keystore"
    private const val PREFS = "aidex_signing"
    private const val KEY_MODE = "mode"
    private const val KEY_CUSTOM_PATH = "custom_path"
    private const val KEY_CUSTOM_ALIAS = "custom_alias"
    private const val KEY_CUSTOM_STORE_PASS = "custom_store_pass"
    private const val KEY_CUSTOM_KEY_PASS = "custom_key_pass"

    enum class Mode { DEBUG, CUSTOM }

    data class Credentials(
        val mode: Mode,
        val keystoreFile: File,
        val storePassword: CharArray,
        val alias: String,
        val keyPassword: CharArray
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun currentMode(context: Context): Mode =
        runCatching { Mode.valueOf(prefs(context).getString(KEY_MODE, "DEBUG")!!) }
            .getOrDefault(Mode.DEBUG)

    fun setMode(context: Context, mode: Mode) {
        prefs(context).edit().putString(KEY_MODE, mode.name).apply()
    }

    /** Returns the File of the debug keystore, generating it if missing. */
    fun ensureDebugKeystore(context: Context): File {
        val dir = ToolchainManager.toolchainDir(context)
        val ksFile = File(dir, DEBUG_STORE)
        if (ksFile.exists() && ksFile.length() > 0) return ksFile

        BuildLogger.log("Signer: generating debug keystore at ${ksFile.name}")
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)

        val kpg = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val kp = kpg.generateKeyPair()
        val cert = SelfSignedCert.generate(kp, "CN=AIDEX Debug, O=AIDEX, C=US")

        ks.setKeyEntry(
            DEBUG_ALIAS, kp.private, DEBUG_PASSWORD.toCharArray(),
            arrayOf(cert)
        )
        FileOutputStream(ksFile).use { ks.store(it, DEBUG_PASSWORD.toCharArray()) }
        BuildLogger.log("Signer: debug keystore ready (${ksFile.length()} bytes)")
        return ksFile
    }

    fun debugCredentials(context: Context): Credentials = Credentials(
        mode = Mode.DEBUG,
        keystoreFile = ensureDebugKeystore(context),
        storePassword = DEBUG_PASSWORD.toCharArray(),
        alias = DEBUG_ALIAS,
        keyPassword = DEBUG_PASSWORD.toCharArray()
    )

    fun saveCustomCredentials(
        context: Context,
        keystorePath: String,
        alias: String,
        storePassword: String,
        keyPassword: String
    ) {
        prefs(context).edit()
            .putString(KEY_MODE, Mode.CUSTOM.name)
            .putString(KEY_CUSTOM_PATH, keystorePath)
            .putString(KEY_CUSTOM_ALIAS, alias)
            .putString(KEY_CUSTOM_STORE_PASS, storePassword)
            .putString(KEY_CUSTOM_KEY_PASS, keyPassword)
            .apply()
    }

    fun customCredentials(context: Context): Credentials? {
        val p = prefs(context)
        val path = p.getString(KEY_CUSTOM_PATH, null) ?: return null
        val alias = p.getString(KEY_CUSTOM_ALIAS, null) ?: return null
        val storePass = p.getString(KEY_CUSTOM_STORE_PASS, null) ?: return null
        val keyPass = p.getString(KEY_CUSTOM_KEY_PASS, null) ?: storePass
        val file = File(path)
        if (!file.exists()) return null
        return Credentials(
            mode = Mode.CUSTOM,
            keystoreFile = file,
            storePassword = storePass.toCharArray(),
            alias = alias,
            keyPassword = keyPass.toCharArray()
        )
    }

    fun loadCredentials(context: Context): Credentials {
        return when (currentMode(context)) {
            Mode.DEBUG -> debugCredentials(context)
            Mode.CUSTOM -> customCredentials(context) ?: debugCredentials(context)
        }
    }

    fun verify(creds: Credentials): Boolean {
        return runCatching {
            val ks = KeyStore.getInstance(guessStoreType(creds.keystoreFile))
            FileInputStream(creds.keystoreFile).use { ks.load(it, creds.storePassword) }
            val key = ks.getKey(creds.alias, creds.keyPassword) as? PrivateKey ?: return false
            val cert = ks.getCertificate(creds.alias) as? X509Certificate ?: return false
            key.algorithm.isNotEmpty() && cert.subjectX500Principal.name.isNotEmpty()
        }.getOrDefault(false)
    }

    private fun guessStoreType(f: File): String =
        if (f.extension.lowercase() == "jks") "JKS" else "PKCS12"
}

/** Minimal self-signed X.509 generator used only for the debug keystore. */
internal object SelfSignedCert {
    fun generate(kp: java.security.KeyPair, dn: String): X509Certificate {
        val now = System.currentTimeMillis()
        val from = Date(now - 24L * 60 * 60 * 1000)
        val to = Date(now + 30L * 365 * 24 * 60 * 60 * 1000)
        val info = sun.security.x509.X500Name(dn)
        val certInfo = sun.security.x509.X509CertInfo().apply {
            set(sun.security.x509.X509CertInfo.VALIDITY,
                sun.security.x509.CertificateValidity(from, to))
            set(sun.security.x509.X509CertInfo.SERIAL_NUMBER,
                sun.security.x509.CertificateSerialNumber(BigInteger.valueOf(now)))
            set(sun.security.x509.X509CertInfo.SUBJECT, info)
            set(sun.security.x509.X509CertInfo.ISSUER, info)
            set(sun.security.x509.X509CertInfo.KEY,
                sun.security.x509.CertificateX509Key(kp.public))
            set(sun.security.x509.X509CertInfo.ALGORITHM_ID,
                sun.security.x509.CertificateAlgorithmId(
                    sun.security.x509.AlgorithmId.get("SHA256withRSA")))
        }
        val cert = sun.security.x509.X509CertImpl(certInfo)
        cert.sign(kp.private, "SHA256withRSA")
        return cert
    }
}
