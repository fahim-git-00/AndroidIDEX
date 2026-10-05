package com.aidex.crash

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Global uncaught exception handler.
 *
 * Instead of letting the system kill the process silently, we launch
 * [CrashActivity] which presents the full stack trace with a
 * "Copy to Clipboard" button. The process is intentionally kept alive
 * until the user taps Close (or presses back).
 */
object CrashHandler {

    private const val TAG = "AIDEX.Crash"
    private val handling = AtomicBoolean(false)

    @Volatile
    var lastReport: String? = null
        private set

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught on ${thread.name}", throwable)

            if (handling.getAndSet(true)) {
                previous?.uncaughtException(thread, throwable)
                return@setDefaultUncaughtExceptionHandler
            }

            val report = buildReport(thread, throwable)
            lastReport = report

            try {
                val intent = Intent(app, CrashActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                    )
                    putExtra(CrashActivity.EXTRA_REPORT, report)
                }
                app.startActivity(intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to launch CrashActivity", t)
                previous?.uncaughtException(thread, throwable)
            }
        }
    }

    fun buildReport(thread: Thread, throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        pw.flush()

        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return buildString {
            appendLine("═══════════════════════════════════════════")
            appendLine("  AIDEX — CRASH REPORT")
            appendLine("═══════════════════════════════════════════")
            appendLine("Time     : $time")
            appendLine("Thread   : ${thread.name}")
            appendLine("Device   : ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android  : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("ABI      : ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("───────────────────────────────────────────")
            appendLine("STACK TRACE")
            appendLine("───────────────────────────────────────────")
            append(sw.toString())
            appendLine()
            appendLine("═══════════════════════════════════════════")
        }
    }
}
