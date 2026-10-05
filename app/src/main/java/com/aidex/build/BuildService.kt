package com.aidex.build

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Orchestrates the full on-device build:
 *   1. Clean intermediates
 *   2. Compile Kotlin  -> classes/
 *   3. Compile Java    -> classes/
 *   4. DEX classes     -> dex/
 *   5. aapt2 compile   -> flat/
 *   6. aapt2 link      -> skeleton.apk
 *   7. Merge dex       -> unsigned.apk
 *   8. Sign            -> final.apk
 */
object BuildService {

    data class ProjectSpec(
        val name: String,
        val root: File,
        val packageName: String
    )

    suspend fun build(
        context: Context,
        project: ProjectSpec,
        onStage: (BuildStage) -> Unit
    ): BuildResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        BuildLogger.clear()

        try {
            val root = project.root
            val srcDir      = File(root, "src")
            val resDir      = File(root, "res")
            val manifest    = File(root, "AndroidManifest.xml")

            require(manifest.exists()) {
                "AndroidManifest.xml missing at ${manifest.absolutePath}"
            }

            val buildDir    = File(root, ".aidex-build")
            val classesDir  = File(buildDir, "classes")
            val dexDir      = File(buildDir, "dex")
            val flatDir     = File(buildDir, "flat")
            val skeletonApk = File(buildDir, "skeleton.apk")
            val unsignedApk = File(buildDir, "unsigned.apk")
            val outputApk   = File(root, "${project.name}-debug.apk")

            // ── PREPARE ──────────────────────────────────────────────────
            onStage(BuildStage.PREPARE)
            BuildLogger.section("PREPARE")
            if (buildDir.exists()) buildDir.deleteRecursively()
            classesDir.mkdirs()
            dexDir.mkdirs()
            flatDir.mkdirs()
            BuildLogger.log("Clean build directory prepared")

            // Collect classpath JARs (android.jar + kotlin stdlib + others).
            val tc = ToolchainManager.toolchainDir(context)
            val classpath = listOf(
                "android.jar", "kotlin-stdlib.jar", "kotlin-reflect.jar", "annotations.jar"
            ).map { File(tc, it) }.filter { it.exists() }

            // ── KOTLIN ───────────────────────────────────────────────────
            onStage(BuildStage.KOTLIN)
            BuildLogger.section("KOTLIN")
            KotlinCompiler.compile(context, listOf(srcDir), classpath, classesDir)

            // ── JAVA ─────────────────────────────────────────────────────
            onStage(BuildStage.JAVA)
            BuildLogger.section("JAVA")
            JavaCompiler.compile(context, listOf(srcDir), classpath, classesDir)

            // ── DEX ──────────────────────────────────────────────────────
            onStage(BuildStage.DEX)
            BuildLogger.section("DEX")
            Dexer.dex(context, classesDir, classpath, dexDir)

            // ── RESOURCES ────────────────────────────────────────────────
            onStage(BuildStage.RESOURCES)
            BuildLogger.section("RESOURCES")
            Aapt2Runner.compileResources(context, resDir, flatDir)
            Aapt2Runner.link(
                context = context,
                flatDir = flatDir,
                manifest = manifest,
                androidJar = File(tc, "android.jar"),
                outApk = skeletonApk
            )

            // ── PACKAGE ──────────────────────────────────────────────────
            onStage(BuildStage.PACKAGE)
            BuildLogger.section("PACKAGE")
            ApkPackager.packageApk(skeletonApk, dexDir, unsignedApk)

            // ── SIGN ─────────────────────────────────────────────────────
            onStage(BuildStage.SIGN)
            BuildLogger.section("SIGN")
            ApkSigner.sign(context, unsignedApk, outputApk)

            // ── DONE ─────────────────────────────────────────────────────
            onStage(BuildStage.DONE)
            BuildLogger.section("DONE")
            val elapsed = System.currentTimeMillis() - start
            BuildLogger.log("Built ${outputApk.name} in ${elapsed / 1000}s")
            BuildLogger.log("Size: ${outputApk.length() / 1024} KB")
            BuildLogger.log("Path: ${outputApk.absolutePath}")

            BuildResult.Success(outputApk, elapsed)

        } catch (t: Throwable) {
            val elapsed = System.currentTimeMillis() - start
            BuildLogger.section("FAILED")
            BuildLogger.log("Error: ${t.message}")
            t.stackTrace.take(15).forEach { BuildLogger.logRaw("  at $it") }
            BuildResult.Failure(
                stage = "BUILD",
                message = t.message ?: t.javaClass.simpleName,
                cause = t,
                durationMs = elapsed
            )
        }
    }
}
