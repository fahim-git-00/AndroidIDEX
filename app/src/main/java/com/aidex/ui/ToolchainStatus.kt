package com.aidex.ui

import android.app.AlertDialog
import android.content.Context
import com.aidex.build.ToolchainManager

object ToolchainStatus {

    fun show(context: Context) {
        val dir = ToolchainManager.toolchainDir(context)
        val lines = StringBuilder()
        lines.appendLine("Toolchain directory:")
        lines.appendLine(dir.absolutePath)
        lines.appendLine()

        ToolchainManager.let { tm ->
            listOf(
                "kotlin-compiler-embeddable.jar",
                "kotlin-stdlib.jar",
                "kotlin-reflect.jar",
                "kotlin-script-runtime.jar",
                "kotlin-daemon-embeddable.jar",
                "trove4j.jar",
                "annotations.jar",
                "ecj.jar",
                "r8.jar",
                "apksig.jar",
                "android.jar",
                "aidex-debug.keystore"
            ).forEach { name ->
                val f = java.io.File(dir, name)
                val status = when {
                    !f.exists() -> "MISSING"
                    f.length() < 10_000 -> "TOO SMALL (${f.length()} B)"
                    else -> "OK (${f.length() / 1024} KB)"
                }
                lines.appendLine("$status  $name")
            }
        }

        AlertDialog.Builder(context)
            .setTitle("Toolchain Status")
            .setMessage(lines.toString())
            .setPositiveButton("OK", null)
            .show()
    }
}
