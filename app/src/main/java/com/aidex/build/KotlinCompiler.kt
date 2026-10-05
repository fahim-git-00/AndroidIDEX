package com.aidex.build

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader

/**
 * In-process Kotlin compiler invocation.
 *
 * Loads kotlin-compiler-embeddable.jar via URLClassLoader and calls the
 * K2JVMCompiler entry point reflectively, capturing stdout/stderr.
 */
object KotlinCompiler {

    fun compile(
        context: Context,
        srcDirs: List<File>,
        classpath: List<File>,
        outputDir: File
    ) {
        val tc = ToolchainManager.toolchainDir(context)
        outputDir.mkdirs()

        val compilerJars = listOf(
            "kotlin-compiler-embeddable.jar",
            "kotlin-stdlib.jar",
            "kotlin-reflect.jar",
            "kotlin-script-runtime.jar",
            "kotlin-daemon-embeddable.jar",
            "trove4j.jar",
            "annotations.jar"
        ).map { File(tc, it) }.filter { it.exists() }

        require(compilerJars.isNotEmpty()) { "Kotlin compiler jars missing." }

        val allJars = compilerJars + classpath
        val loader = URLClassLoader(
            allJars.map { it.toURI().toURL() }.toTypedArray(),
            ClassLoader.getSystemClassLoader().parent
        )

        val srcFiles = srcDirs
            .filter { it.exists() }
            .flatMap { it.walkTopDown().toList() }
            .filter { it.isFile && it.extension.equals("kt", true) }

        if (srcFiles.isEmpty()) {
            BuildLogger.log("Kotlin: no .kt files, skipping.")
            return
        }

        BuildLogger.log("Kotlin: compiling ${srcFiles.size} files")

        val k2Class = Class.forName(
            "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            true, loader
        )
        val compiler = k2Class.getDeclaredConstructor().newInstance()

        val freeArgs = mutableListOf<String>()
        freeArgs += "-d"; freeArgs += outputDir.absolutePath
        freeArgs += "-jvm-target"; freeArgs += "17"
        freeArgs += "-no-stdlib"; freeArgs += "-no-reflect"
        freeArgs += "-nowarn"
        freeArgs += "-classpath"; freeArgs += classpath.joinToString(":") { it.absolutePath }
        srcFiles.forEach { freeArgs += it.absolutePath }

        val errBuf = ByteArrayOutputStream()
        val outBuf = ByteArrayOutputStream()
        val prevErr = System.err
        val prevOut = System.out
        try {
            System.setErr(PrintStream(errBuf, true, "UTF-8"))
            System.setOut(PrintStream(outBuf, true, "UTF-8"))

            val execMethod = k2Class.getMethod(
                "exec",
                PrintStream::class.java,
                Array<String>::class.java
            )
            val exitCode = execMethod.invoke(
                compiler,
                System.err,
                freeArgs.toTypedArray()
            ) as Int

            dumpStream("Kotlin stdout", outBuf)
            dumpStream("Kotlin stderr", errBuf)

            if (exitCode != 0) {
                val msg = errBuf.toString("UTF-8").ifBlank { "exit=$exitCode" }
                throw RuntimeException("Kotlin compilation failed:\n$msg")
            }
        } finally {
            System.setErr(prevErr)
            System.setOut(prevOut)
        }
    }

    private fun dumpStream(label: String, buf: ByteArrayOutputStream) {
        val text = buf.toString("UTF-8").trim()
        if (text.isNotEmpty()) {
            BuildLogger.section(label)
            text.lineSequence().forEach { BuildLogger.logRaw(it) }
        }
    }
}
