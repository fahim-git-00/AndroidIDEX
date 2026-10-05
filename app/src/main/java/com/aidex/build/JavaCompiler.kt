package com.aidex.build

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLClassLoader

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

        val parent = JavaCompiler::class.java.classLoader
        val loader = URLClassLoader(
            arrayOf(ecjJar.toURI().toURL()),
            parent
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

        // BatchCompiler is the public embedded entry point. It avoids
        // referencing CLI-only classes like java.util.logging.Handler.
        val batchCls = Class.forName(
            "org.eclipse.jdt.core.compiler.batch.BatchCompiler",
            true, loader
        )

        val args = mutableListOf<String>()
        args += "-17"
        args += "-nowarn"
        args += "-proc:none"
        args += "-d"; args += outputDir.absolutePath
        args += "-classpath"
        args += classpath.joinToString(":") { it.absolutePath }
        javaFiles.forEach { args += it.absolutePath }

        val outWriter = StringWriter()
        val errWriter = StringWriter()

        // static boolean compile(String commandLine, PrintWriter out, PrintWriter err, CompilationProgress progress)
        val compileMethod = batchCls.getMethod(
            "compile",
            String::class.java,
            PrintWriter::class.java,
            PrintWriter::class.java,
            Class.forName("org.eclipse.jdt.core.compiler.CompilationProgress")
        )

        val cmdLine = args.joinToString(" ")
        BuildLogger.log("Java: cmd = $cmdLine")

        val success: Boolean
        val outPw = PrintWriter(outWriter)
        val errPw = PrintWriter(errWriter)
        try {
            success = compileMethod.invoke(
                null,
                cmdLine,
                outPw,
                errPw,
                null
            ) as Boolean
        } catch (t: Throwable) {
            val cause = t.cause ?: t
            BuildLogger.section("Java exception")
            cause.stackTrace.take(20).forEach { BuildLogger.logRaw("  at $it") }
            throw RuntimeException("ECJ invocation failed: ${cause.message}", cause)
        } finally {
            outPw.flush()
            errPw.flush()
        }

        val outText = outWriter.toString().trim()
        val errText = errWriter.toString().trim()
        if (outText.isNotEmpty()) {
            BuildLogger.section("Java stdout")
            outText.lineSequence().forEach { BuildLogger.logRaw(it) }
        }
        if (errText.isNotEmpty()) {
            BuildLogger.section("Java stderr")
            errText.lineSequence().forEach { BuildLogger.logRaw(it) }
        }

        if (!success) {
            throw RuntimeException("Java compilation failed (see log above).")
        }
    }
}
