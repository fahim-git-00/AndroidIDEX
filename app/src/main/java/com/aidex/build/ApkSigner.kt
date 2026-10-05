package com.aidex.build

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * Signs the packaged APK using Google's apksig library, loaded via
 * DexClassLoader at runtime.
 *
 * apksig produces v1 + v2 + v3 signatures in one pass — the exact same
 * output you'd get from Gradle's `apksigner`, and fully compatible with
 * the Android package manager on every API level.
 *
 * The library (~200 KB) is fetched by ToolchainManager alongside the other
 * toolchain JARs on first launch.
 */
object ApkSigner {

    private const val APKSIG_JAR = "apksig.jar"

    /**
     * Signs [unsignedApk] into [signedApk] using the credentials currently
     * selected in [KeystoreManager] (debug or custom).
     */
    fun sign(context: Context, unsignedApk: File, signedApk: File) {
        require(unsignedApk.exists()) { "Unsigned APK missing: ${unsignedApk.absolutePath}" }

        val creds = KeystoreManager.loadCredentials(context)
        BuildLogger.log("Signer: mode=${creds.mode} alias=${creds.alias}")
        BuildLogger.log("Signer: keystore=${creds.keystoreFile.name}")

        if (!KeystoreManager.verify(creds)) {
            throw RuntimeException(
                "Signer: keystore verification failed. Check alias/passwords."
            )
        }

        val apksigJar = File(ToolchainManager.toolchainDir(context), APKSIG_JAR)
        require(apksigJar.exists()) {
            "apksig.jar missing — run the toolchain download."
        }

        // Build a URLClassLoader over apksig + its transitive deps (none at runtime).
        val loader = java.net.URLClassLoader(
            arrayOf(apksigJar.toURI().toURL()),
            ClassLoader.getSystemClassLoader().parent
        )

        // Load the top-level ApkSigner.Builder and its sub-types reflectively.
        val apkSignerBuilderCls = Class.forName(
            "com.android.apksig.ApkSigner\$Builder", true, loader)
        val signerConfigCls = Class.forName(
            "com.android.apksig.ApkSigner\$SignerConfig", true, loader)
        val signerConfigBuilderCls = Class.forName(
            "com.android.apksig.ApkSigner\$SignerConfig\$Builder", true, loader)

        // Load key + certificate chain from the keystore.
        val ks = KeyStore.getInstance(
            if (creds.keystoreFile.extension.lowercase() == "jks") "JKS" else "PKCS12"
        )
        FileInputStream(creds.keystoreFile).use { ks.load(it, creds.storePassword) }

        val privateKey = ks.getKey(creds.alias, creds.keyPassword) as PrivateKey
        val certChain = ks.getCertificateChain(creds.alias)
            ?.map { it as X509Certificate }
            ?: throw RuntimeException("Signer: no certificate chain for alias '${creds.alias}'")

        // SignerConfig.Builder(name, privateKey, certificates: List<X509Certificate>)
        val certListCls = Class.forName("java.util.List")
        val signerConfigBuilder = signerConfigBuilderCls
            .getConstructor(String::class.java, PrivateKey::class.java, certListCls)
            .newInstance(creds.alias, privateKey, certChain)
        val signerConfig = signerConfigBuilderCls
            .getMethod("build")
            .invoke(signerConfigBuilder)

        // ApkSigner.Builder(List<SignerConfig>)
        val builder = apkSignerBuilderCls
            .getConstructor(certListCls)
            .newInstance(listOf(signerConfig))

        // .setInputApk(File) / .setOutputApk(File) / .setV1SigningEnabled / setV2SigningEnabled / build()
        apkSignerBuilderCls.getMethod("setInputApk", File::class.java)
            .invoke(builder, unsignedApk)
        apkSignerBuilderCls.getMethod("setOutputApk", File::class.java)
            .invoke(builder, signedApk)
        apkSignerBuilderCls.getMethod("setV1SigningEnabled", Boolean::class.javaPrimitiveType)
            .invoke(builder, true)
        apkSignerBuilderCls.getMethod("setV2SigningEnabled", Boolean::class.javaPrimitiveType)
            .invoke(builder, true)
        apkSignerBuilderCls.getMethod("setV3SigningEnabled", Boolean::class.javaPrimitiveType)
            .invoke(builder, true)

        val apkSigner = apkSignerBuilderCls.getMethod("build").invoke(builder)

        BuildLogger.log("Signer: signing ${unsignedApk.name} -> ${signedApk.name}")
        if (signedApk.exists()) signedApk.delete()

        // .sign()
        val signMethod = apkSigner.javaClass.getMethod("sign")
        signMethod.invoke(apkSigner)

        require(signedApk.exists()) { "Signer: apksig did not produce output APK." }
        BuildLogger.log(
            "Signer: done — ${signedApk.name} (${signedApk.length() / 1024} KB)"
        )
    }
}
