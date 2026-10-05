package com.aidex.build

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader

/**
 * Converts compiled .class files into DEX using R8/D8 loaded in-process.
 */
object Dexer {

    fun dex(
        context: Context,
        classDir: File,
        classpath: List<File>,
        outputDir: File
    ) {
        val tc = ToolchainManager.toolchainDir(context)
        outputDir.mkdirs()

        val r8Jar = File(tc, "r8.jar")
        require(r8Jar.exists()) { "r8.jar missing — run toolchain download." }

        val classFiles = classDir.walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .toList()

        if (classFiles.isEmpty()) {
            throw RuntimeException("No .class files produced by compilers.")
        }

        BuildLogger.log("D8: dexing ${classFiles.size} class files")

        val loader = URLClassLoader(
            arrayOf(r8Jar.toURI().toURL()),
            ClassLoader.getSystemClassLoader().parent
        )

        val d8Class = Class.forName("com.android.tools.r8.D8", true, loader)
        val mainMethod = d8Class.getMethod("main", Array<String>::class.java)

        val args = mutableListOf<String>()
        args += "--output"; args += outputDir.absolutePath
        args += "--min-api"; args += "26"
        args += "--lib"; args += File(tc, "android.jar").absolutePath
        classpath.forEach {
            args += "--classpath"; args += it.absolutePath
        }
        classFiles.forEach { args += it.absolutePath }

        val errBuf = ByteArrayOutputStream()
        val outBuf = ByteArrayOutputStream()
        val prevErr = System.err
        val prevOut = System.out
        try {
            System.setErr(PrintStream(errBuf, true, "UTF-8"))
            System.setOut(PrintStream(outBuf, true, "UTF-8"))
            mainMethod.invoke(null, args.toTypedArray())
        } catch (t: Throwable) {
            dumpStream("D8 stdout", outBuf)
            dumpStream("D8 stderr", errBuf)
            val cause = t.cause ?: t
            throw RuntimeException("D8 failed: ${cause.message}", cause)
        } finally {
            System.setErr(prevErr)
            System.setOut(prevOut)
        }
        dumpStream("D8 stdout", outBuf)
        dumpStream("D8 stderr", errBuf)
    }

    private fun dumpStream(label: String, buf: ByteArrayOutputStream) {
        val text = buf.toString("UTF-8").trim()
        if (text.isNotEmpty()) {
            BuildLogger.section(label)
            text.lineSequence().forEach { BuildLogger.logRaw(it) }
        }
    }
}
