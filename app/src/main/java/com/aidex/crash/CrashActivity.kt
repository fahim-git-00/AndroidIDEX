package com.aidex.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Process
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aidex.databinding.ActivityCrashBinding

class CrashActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REPORT = "extra_report"
    }

    private lateinit var binding: ActivityCrashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val report = intent.getStringExtra(EXTRA_REPORT)
            ?: CrashHandler.lastReport
            ?: "No crash report available."

        binding.crashLog.text = report

        binding.copyButton.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("AIDEX Crash Report", report))
            Toast.makeText(
                this,
                "Crash report copied — paste it anywhere to debug.",
                Toast.LENGTH_LONG
            ).show()
        }

        binding.closeButton.setOnClickListener { terminate() }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = terminate()

    private fun terminate() {
        finishAffinity()
        Process.killProcess(Process.myPid())
        @Suppress("DEPRECATION")
        kotlin.system.exitProcess(1)
    }
}
