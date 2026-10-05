package com.aidex.build

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Runs the native aapt2 binary. On first use, extracts it from the packaged
 * asset into filesDir/toolchain/aapt2 and marks it executable.
 *
 * NOTE: We ship aapt2 as an asset in the APK (see assets/aapt2). If the asset
 * is absent the runner fails with a clear message.
 */
object Aapt2Runner {

    private const val BIN = "aapt2"

    fun aapt2(context: Context): File {
        val dir = ToolchainManager.toolchainDir(context)
        val bin = File(dir, BIN)
        if (bin.exists() && bin.canExecute()) return bin

        // Extract from assets if present.
        try {
            context.assets.open(BIN).use { input ->
                bin.outputStream().use { output -> input.copyTo(output) }
            }
            bin.setExecutable(true, false)
        } catch (t: Throwable) {
            throw RuntimeException(
                "aapt2 binary not packaged in APK assets. " +
                "Place it at app/src/main/assets/aapt2 and rebuild.",
                t
            )
        }
        return bin
    }

    /** Runs `aapt2 compile` on every file in res/ producing a .flat in outDir. */
    fun compileResources(context: Context, resDir: File, outDir: File) {
        if (!resDir.exists()) {
            BuildLogger.log("aapt2: no res/ dir, skipping resource compile.")
            return
        }
        outDir.mkdirs()
        val bin = aapt2(context)

        val resFiles = resDir.walkTopDown()
            .filter { it.isFile }
            .toList()
        if (resFiles.isEmpty()) {
            BuildLogger.log("aapt2: res/ empty, skipping.")
            return
        }

        BuildLogger.log("aapt2: compiling ${resFiles.size} resource files")
        val args = mutableListOf(bin.absolutePath, "compile",
            "--dir", resDir.absolutePath,
            "-o", outDir.absolutePath)
        run(args, context)
    }

    /** Links resources + manifest into an unsigned APK skeleton. */
    fun link(
        context: Context,
        flatDir: File,
        manifest: File,
        androidJar: File,
        outApk: File
    ) {
        val bin = aapt2(context)
        val flatFiles = flatDir.listFiles { f -> f.extension == "flat" }?.toList().orEmpty()
        require(flatFiles.isNotEmpty()) { "aapt2: no .flat files to link." }

        BuildLogger.log("aapt2: linking ${flatFiles.size} resource archives")
        outApk.parentFile?.mkdirs()
        if (outApk.exists()) outApk.delete()

        val args = mutableListOf<String>()
        args += bin.absolutePath
        args += "link"
        args += "-o"; args += outApk.absolutePath
        args += "-I"; args += androidJar.absolutePath
        args += "--manifest"; args += manifest.absolutePath
        args += "--min-sdk-version"; args += "26"
        args += "--target-sdk-version"; args += "35"
        args += "--version-code"; args += "1"
        args += "--version-name"; args += "1.0"
        args += "--auto-add-overlay"
        flatFiles.forEach { args += it.absolutePath }

        run(args, context)
    }

    private fun run(args: List<String>, context: Context) {
        val process = ProcessBuilder(args)
            .redirectErrorStream(true)
            .apply {
                environment()["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir
            }
            .start()

        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                BuildLogger.logRaw("aapt2: $line")
            }
        }
        val exit = process.waitFor()
        if (exit != 0) {
            throw RuntimeException("aapt2 exited with code $exit")
        }
    }
}
