package com.aidex.build

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.net.URLClassLoader
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

object ApkSigner {

    private const val APKSIG_JAR = "apksig.jar"

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
        require(apksigJar.exists()) { "apksig.jar missing — run the toolchain download." }

        val parent = ApkSigner::class.java.classLoader
        val loader = URLClassLoader(
            arrayOf(apksigJar.toURI().toURL()),
            parent
        )

        val apkSignerBuilderCls = Class.forName(
            "com.android.apksig.ApkSigner\$Builder", true, loader
        )
        val signerConfigBuilderCls = Class.forName(
            "com.android.apksig.ApkSigner\$SignerConfig\$Builder", true, loader
        )

        val ks = KeyStore.getInstance(
            if (creds.keystoreFile.extension.lowercase() == "jks") "JKS" else "PKCS12"
        )
        FileInputStream(creds.keystoreFile).use { ks.load(it, creds.storePassword) }

        val privateKey = ks.getKey(creds.alias, creds.keyPassword) as PrivateKey
        val certChain = ks.getCertificateChain(creds.alias)
            ?.map { it as X509Certificate }
            ?: throw RuntimeException(
                "Signer: no certificate chain for alias '${creds.alias}'"
            )

        val certListCls = Class.forName("java.util.List")
        val signerConfigBuilder = signerConfigBuilderCls
            .getConstructor(String::class.java, PrivateKey::class.java, certListCls)
            .newInstance(creds.alias, privateKey, certChain)
        val signerConfig = signerConfigBuilderCls
            .getMethod("build")
            .invoke(signerConfigBuilder)

        val builder = apkSignerBuilderCls
            .getConstructor(certListCls)
            .newInstance(listOf(signerConfig))

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

        apkSigner.javaClass.getMethod("sign").invoke(apkSigner)

        require(signedApk.exists()) { "Signer: apksig did not produce output APK." }
        BuildLogger.log(
            "Signer: done — ${signedApk.name} (${signedApk.length() / 1024} KB)"
        )
    }
}
