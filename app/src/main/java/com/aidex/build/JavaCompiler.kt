package com.aidex.build

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader

/**
 * In-process ECJ (Eclipse Compiler for Java) invocation.
 * Loads ecj.jar via URLClassLoader and calls the Main entry point reflectively.
 */
object JavaCompiler {

    fun compile(
        context: Context,
        srcDirs: List<File>,
        classpath: List<File>,
        outputDir: File
    ) {
        val tc = ToolchainManager.toolchainDir(context)
        outputDir.mkdirs()

        val ecjJar = File(tc, "ecj.jar")
        require(ecjJar.exists()) { "ecj.jar missing — run toolchain download." }

        val loader = URLClassLoader(
            arrayOf(ecjJar.toURI().toURL()),
            ClassLoader.getSystemClassLoader().parent
        )

        val javaFiles = srcDirs
            .filter { it.exists() }
            .flatMap { it.walkTopDown().toList() }
            .filter { it.isFile && it.extension.equals("java", true) }

        if (javaFiles.isEmpty()) {
            BuildLogger.log("Java: no .java files, skipping.")
            return
        }

        BuildLogger.log("Java: compiling ${javaFiles.size} files")

        val mainClass = Class.forName("org.eclipse.jdt.internal.compiler.batch.Main", true, loader)
        val main = mainClass.getDeclaredConstructor().newInstance()

        val args = mutableListOf<String>()
        args += "-17"
        args += "-nowarn"
        args += "-proc:none"
        args += "-d"; args += outputDir.absolutePath
        args += "-classpath"
        args += classpath.joinToString(":") { it.absolutePath }
        javaFiles.forEach { args += it.absolutePath }

        val errBuf = ByteArrayOutputStream()
        val outBuf = ByteArrayOutputStream()
        val prevErr = System.err
        val prevOut = System.out
        try {
            System.setErr(PrintStream(errBuf, true, "UTF-8"))
            System.setOut(PrintStream(outBuf, true, "UTF-8"))

            val compileMethod = mainClass.getMethod(
                "compile",
                Array<String>::class.java
            )
            val result = compileMethod.invoke(main, args.toTypedArray())

            dumpStream("Java stdout", outBuf)
            dumpStream("Java stderr", errBuf)

            val failed = when (result) {
                is Boolean -> !result
                is Int -> result != 0
                else -> false
            }
            if (failed) {
                val msg = errBuf.toString("UTF-8").ifBlank { "ECJ returned failure" }
                throw RuntimeException("Java compilation failed:\n$msg")
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
