package com.aidex.build

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader

object KotlinCompiler {

    fun compile(
        context: Context,
        srcDirs: List<File>,
        classpath: List<File>,
        outputDir: File
    ) {
        val tc = ToolchainManager.toolchainDir(context)
        outputDir.mkdirs()

        // 1. List what's actually in the toolchain dir.
        BuildLogger.section("TOOLCHAIN INSPECTION")
        BuildLogger.log("Directory: ${tc.absolutePath}")
        BuildLogger.log("Exists: ${tc.exists()}")
        val allFiles = tc.listFiles()?.sortedBy { it.name } ?: emptyList()
        allFiles.forEach { f ->
            BuildLogger.logRaw("  ${f.name}  (${f.length() / 1024} KB)")
        }

        val needed = listOf(
            "kotlin-compiler-embeddable.jar",
            "kotlin-stdlib.jar",
            "kotlin-reflect.jar",
            "kotlin-script-runtime.jar",
            "kotlin-daemon-embeddable.jar",
            "trove4j.jar",
            "annotations.jar"
        )
        val compilerJars = mutableListOf<File>()
        needed.forEach { name ->
            val f = File(tc, name)
            val status = when {
                !f.exists() -> "MISSING"
                f.length() < 100_000 -> "TOO SMALL (${f.length()} B)"
                else -> "OK (${f.length() / 1024} KB)"
            }
            BuildLogger.log("  $status  $name")
            if (f.exists() && f.length() >= 100_000) compilerJars.add(f)
        }

        if (compilerJars.size < needed.size) {
            throw RuntimeException(
                "Kotlin toolchain incomplete: ${compilerJars.size}/${needed.size} jars OK. " +
                "Delete app data and re-download."
            )
        }

        val allJars = compilerJars + classpath
        val parent = KotlinCompiler::class.java.classLoader
        BuildLogger.log("Parent classloader: ${parent?.javaClass?.name ?: "null"}")

        val loader = URLClassLoader(
            allJars.map { it.toURI().toURL() }.toTypedArray(),
            parent
        )

        // 2. Try to find the class and show ALL causes.
        BuildLogger.log("Loading K2JVMCompiler...")
        val k2Class: Class<*> = try {
            Class.forName("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", true, loader)
        } catch (t: Throwable) {
            var cause: Throwable? = t
            while (cause != null) {
                BuildLogger.log("CAUSE: ${cause.javaClass.name}")
                BuildLogger.log("  msg: ${cause.message}")
                cause = cause.cause
            }
            // 3. Try a simpler class from the same jar to isolate the problem.
            BuildLogger.log("Diagnostic: trying to load kotlin.Unit from same loader...")
            try {
                Class.forName("kotlin.Unit", false, loader)
                BuildLogger.log("  kotlin.Unit: LOADED OK")
            } catch (e: Throwable) {
                BuildLogger.log("  kotlin.Unit FAILED: ${e.javaClass.simpleName}: ${e.message}")
            }
            BuildLogger.log("Diagnostic: trying kotlin.jvm.internal.Intrinsics...")
            try {
                Class.forName("kotlin.jvm.internal.Intrinsics", false, loader)
                BuildLogger.log("  Intrinsics: LOADED OK")
            } catch (e: Throwable) {
                BuildLogger.log("  Intrinsics FAILED: ${e.javaClass.simpleName}: ${e.message}")
            }
            // 4. Try loading from the compiler jar directly without the others.
            BuildLogger.log("Diagnostic: isolating compiler jar only...")
            val compilerOnlyLoader = URLClassLoader(
                arrayOf(File(tc, "kotlin-compiler-embeddable.jar").toURI().toURL()),
                parent
            )
            try {
                Class.forName("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", false, compilerOnlyLoader)
                BuildLogger.log("  K2JVMCompiler from isolated loader: LOADED OK")
            } catch (e: Throwable) {
                BuildLogger.log("  Isolated load FAILED: ${e.javaClass.simpleName}: ${e.message}")
            }
            throw RuntimeException("Kotlin compiler class not loadable", t)
        }

        val srcFiles = srcDirs
            .filter { it.exists() }
            .flatMap { it.walkTopDown().toList() }
            .filter { it.isFile && it.extension.equals("kt", true) }

        if (srcFiles.isEmpty()) {
            BuildLogger.log("Kotlin: no .kt files, skipping.")
            return
        }

        BuildLogger.log("Kotlin: compiling ${srcFiles.size} files")

        val compiler = k2Class.getDeclaredConstructor().newInstance()

        val freeArgs = mutableListOf<String>()
        freeArgs += "-d"; freeArgs += outputDir.absolutePath
        freeArgs += "-jvm-target"; freeArgs += "17"
        freeArgs += "-no-stdlib"; freeArgs += "-no-reflect"
        freeArgs += "-nowarn"
        freeArgs += "-classpath"
        freeArgs += classpath.joinToString(":") { it.absolutePath }
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
