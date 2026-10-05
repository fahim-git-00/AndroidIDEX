package com.aidex.build

import java.io.File

sealed class BuildResult {
    data class Success(
        val apk: File,
        val durationMs: Long
    ) : BuildResult()

    data class Failure(
        val stage: String,
        val message: String,
        val cause: Throwable?,
        val durationMs: Long
    ) : BuildResult()
}

enum class BuildStage(val display: String) {
    PREPARE("Preparing build directories"),
    KOTLIN("Compiling Kotlin sources"),
    JAVA("Compiling Java sources"),
    DEX("Dexing classes to DEX format"),
    RESOURCES("Compiling resources with AAPT2"),
    PACKAGE("Packaging APK"),
    SIGN("Signing APK"),
    DONE("Build complete")
}
