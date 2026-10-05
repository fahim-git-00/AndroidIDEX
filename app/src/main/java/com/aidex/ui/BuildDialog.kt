package com.aidex.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleCoroutineScope
import com.aidex.build.BuildLogger
import com.aidex.build.BuildResult
import com.aidex.build.BuildService
import com.aidex.build.BuildStage
import com.aidex.databinding.DialogBuildBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Live build log dialog with progress indicator, copy-to-clipboard button
 * and install shortcut on success.
 */
object BuildDialog {

    fun show(
        context: Context,
        lifecycleScope: LifecycleCoroutineScope,
        project: BuildService.ProjectSpec
    ) {
        val binding = DialogBuildBinding.inflate(LayoutInflater.from(context))

        val dialog = AlertDialog.Builder(context)
            .setTitle("Building ${project.name}")
            .setView(binding.root)
            .setNegativeButton("Close", null)
            .create()

        var builtApk: File? = null

        binding.copyBuildLog.setOnClickListener {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("AIDEX Build Log", binding.buildLog.text))
            Toast.makeText(context, "Build log copied", Toast.LENGTH_SHORT).show()
        }

        binding.installApk.setOnClickListener {
            val apk = builtApk ?: return@setOnClickListener
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        binding.installApk.isEnabled = false

        dialog.setOnShowListener {
            // Subscribe to the log stream to update the TextView live.
            lifecycleScope.launch {
                BuildLogger.stream.collect { line ->
                    withContext(Dispatchers.Main) {
                        binding.buildLog.append(line)
                        binding.buildLog.append("\n")
                        val scroll = binding.buildLog.layout?.let {
                            binding.buildLog.height - binding.buildLog.scrollY
                        } ?: 0
                        if (scroll < 200) {
                            binding.buildLogScroll.post {
                                binding.buildLogScroll.fullScroll(android.view.View.FOCUS_DOWN)
                            }
                        }
                    }
                }
            }

            lifecycleScope.launch {
                val result = BuildService.build(context, project) { stage ->
                    binding.stageLabel.post {
                        binding.stageLabel.text = stage.display
                    }
                    binding.progress.post {
                        binding.progress.progress = stageProgress(stage)
                    }
                }
                withContext(Dispatchers.Main) {
                    when (result) {
                        is BuildResult.Success -> {
                            builtApk = result.apk
                            binding.stageLabel.text = "Built in ${result.durationMs / 1000}s"
                            binding.progress.progress = 100
                            binding.installApk.isEnabled = true
                        }
                        is BuildResult.Failure -> {
                            binding.stageLabel.text = "Failed: ${result.message}"
                            binding.progress.progress = 0
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun stageProgress(stage: BuildStage): Int = when (stage) {
        BuildStage.PREPARE   -> 5
        BuildStage.KOTLIN    -> 25
        BuildStage.JAVA      -> 40
        BuildStage.DEX       -> 55
        BuildStage.RESOURCES -> 70
        BuildStage.PACKAGE   -> 85
        BuildStage.SIGN      -> 95
        BuildStage.DONE      -> 100
    }
}
