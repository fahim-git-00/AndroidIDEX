package com.aidex.ui

import android.app.Activity
import android.app.AlertDialog
import android.view.LayoutInflater
import android.widget.Toast
import com.aidex.databinding.DialogNewProjectBinding
import com.aidex.project.ProjectActions
import java.io.File

/**
 * Simple modal dialog that asks for a project name and creates the project.
 * Returns the created project root via [onCreated].
 */
object NewProjectDialog {

    fun show(
        activity: Activity,
        onCreated: (File) -> Unit
    ) {
        val binding = DialogNewProjectBinding.inflate(LayoutInflater.from(activity))

        val dialog = AlertDialog.Builder(activity)
            .setTitle("New Project")
            .setView(binding.root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = binding.projectName.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    binding.projectName.error = "Enter a name"
                    return@setOnClickListener
                }
                try {
                    val result = ProjectActions.createProject(activity, name)
                    Toast.makeText(
                        activity,
                        "Created ${result.root.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                    onCreated(result.root)
                } catch (t: Throwable) {
                    binding.projectName.error = t.message ?: "Failed to create"
                }
            }
        }
        dialog.show()
    }
}
