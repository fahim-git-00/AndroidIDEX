package com.aidex

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.aidex.build.BuildService
import com.aidex.build.ToolchainManager
import com.aidex.databinding.ActivityMainBinding
import com.aidex.editor.Language
import com.aidex.fs.FileTreeAdapter
import com.aidex.fs.ProjectManager
import com.aidex.project.ProjectActions
import com.aidex.ui.BuildDialog
import com.aidex.ui.NewProjectDialog
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: FileTreeAdapter

    private val openFiles = LinkedHashMap<File, String>()
    private var currentFile: File? = null
    private var currentProjectRoot: File? = null

    private val openFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val file = uriToFile(uri)
            if (file != null && file.isDirectory) {
                loadProject(file)
            } else {
                toast("Could not resolve folder path")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(android.R.drawable.ic_menu_sort_by_size)
        supportActionBar?.title = "AIDEX"

        adapter = FileTreeAdapter(::openFile)
        binding.fileTree.adapter = adapter

        binding.toolbar.setNavigationOnClickListener {
            binding.drawer.openDrawer(GravityCompat.START)
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                (tab.tag as? File)?.let { switchTo(it) }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        binding.saveButton.setOnClickListener { saveCurrent() }
        binding.copyLogButton.setOnClickListener { copyCurrentToClipboard() }
        binding.newProjectBtn.setOnClickListener { promptNewProject() }
        binding.openProjectBtn.setOnClickListener { openFolderLauncher.launch(null) }
        binding.buildBtn.setOnClickListener { promptBuild() }

        // Ensure toolchain is present before first build.
        ensureToolchainThenLoadDefault()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.drawer.isDrawerOpen(GravityCompat.START)) {
            binding.drawer.closeDrawer(GravityCompat.START)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    // ─── TOOLCHAIN ────────────────────────────────────────────────────────

    private fun ensureToolchainThenLoadDefault() {
        loadDefaultProject()
        if (ToolchainManager.isReady(this)) return
        AlertDialog.Builder(this)
            .setTitle("Toolchain required")
            .setMessage(
                "AIDEX must download ~130 MB of compiler toolchain " +
                "(Kotlin 2.0, ECJ, R8, apksig, android.jar).\n\n" +
                "You can still edit files without it, but Build requires it.\n\n" +
                "Connect to Wi-Fi and tap Download."
            )
            .setCancelable(false)
            .setPositiveButton("Download") { _, _ -> downloadToolchain() }
            .setNegativeButton("Later") { _, _ -> }
            .show()
    }

    private fun downloadToolchain() {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Downloading toolchain…")
            .setMessage("Starting…")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch {
            try {
                ToolchainManager.download(this@MainActivity) { done, total, name ->
                    runOnUiThread {
                        val pct = if (total > 0) (done * 100 / total) else 0
                        progressDialog.setMessage("$pct%  •  $name\n" +
                            "${done / 1024 / 1024} MB / ${total / 1024 / 1024} MB")
                    }
                }
                progressDialog.dismiss()
                toast("Toolchain ready")
                loadDefaultProject()
            } catch (t: Throwable) {
                progressDialog.dismiss()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Download failed")
                    .setMessage(t.message ?: "Unknown error")
                    .setPositiveButton("Retry") { _, _ -> downloadToolchain() }
                    .setNegativeButton("Skip") { _, _ -> loadDefaultProject() }
                    .show()
            }
        }
    }

    // ─── PROJECT ──────────────────────────────────────────────────────────

    private fun loadDefaultProject() {
        val root = ProjectManager.ensureSampleProject(this)
        loadProject(root)
    }

    private fun loadProject(root: File) {
        currentProjectRoot = root
        openFiles.clear()
        binding.tabLayout.removeAllTabs()
        currentFile = null
        binding.editor.setText("")
        adapter.setRoot(root)
        binding.projectPath.text = root.absolutePath
        supportActionBar?.subtitle = root.name
        binding.drawer.closeDrawer(GravityCompat.START)
    }

    private fun promptNewProject() {
        NewProjectDialog.show(this) { created ->
            loadProject(created)
        }
    }

    private fun promptBuild() {
        val root = currentProjectRoot ?: run {
            toast("No project loaded")
            return
        }
        if (!ToolchainManager.isReady(this)) {
            AlertDialog.Builder(this)
                .setTitle("Toolchain required")
                .setMessage(
                    "Compiler jars are missing. Download them now?\n\n" +
                    "This is a one-time ~130 MB download."
                )
                .setPositiveButton("Download") { _, _ -> downloadToolchain() }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        val spec = BuildService.ProjectSpec(
            name = root.name,
            root = root,
            packageName = guessPackage(root)
        )
        BuildDialog.show(this, lifecycleScope, spec)
    }

    private fun guessPackage(root: File): String {
        return try {
            val manifest = File(root, "AndroidManifest.xml")
            val text = manifest.readText()
            Regex("package=\"([^\"]+)\"").find(text)?.groupValues?.get(1)
                ?: "com.aidex.user"
        } catch (_: Throwable) { "com.aidex.user" }
    }

    private fun uriToFile(uri: Uri): File? {
        // Try to resolve to a real path for tree URIs from the primary volume.
        return try {
            if (uri.scheme == "file") return File(uri.path!!)
            val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
                ?: return null
            val split = docId.split(":")
            val type = split[0]
            val rel = if (split.size > 1) split[1] else ""
            val base = when (type) {
                "primary" -> Environment.getExternalStorageDirectory()
                else -> File("/storage/$type")
            }
            File(base, rel)
        } catch (_: Throwable) { null }
    }

    // ─── FILES ────────────────────────────────────────────────────────────

    private fun openFile(file: File) {
        if (openFiles.containsKey(file)) {
            selectTabFor(file)
            binding.drawer.closeDrawer(GravityCompat.START)
            return
        }
        lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) {
                runCatching { file.readText() }
                    .getOrElse { "// Unable to read ${file.name}: ${it.message}\n" }
            }
            openFiles[file] = content
            val tab = binding.tabLayout.newTab()
                .setText(file.name)
                .also { it.tag = file }
            binding.tabLayout.addTab(tab, true)
            binding.drawer.closeDrawer(GravityCompat.START)
        }
    }

    private fun selectTabFor(file: File) {
        for (i in 0 until binding.tabLayout.tabCount) {
            val t = binding.tabLayout.getTabAt(i) ?: continue
            if (t.tag == file) { t.select(); return }
        }
    }

    private fun switchTo(file: File) {
        currentFile?.let { prev ->
            openFiles[prev] = binding.editor.text?.toString() ?: ""
        }
        currentFile = file
        binding.editor.setLanguage(languageOf(file))
        binding.editor.setText(openFiles[file] ?: "")
        binding.editor.setSelection(0)
        supportActionBar?.subtitle = file.name
    }

    private fun saveCurrent() {
        val f = currentFile ?: run {
            toast("No file open")
            return
        }
        val content = binding.editor.text?.toString() ?: ""
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { f.writeText(content) }.isSuccess
            }
            if (ok) openFiles[f] = content
            toast(if (ok) "Saved ${f.name}" else "Save failed for ${f.name}")
        }
    }

    private fun copyCurrentToClipboard() {
        val text = binding.editor.text?.toString() ?: ""
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("AIDEX source", text))
        toast("Editor buffer copied to clipboard")
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun languageOf(file: File): Language = when (file.extension.lowercase()) {
        "kt", "kts" -> Language.KOTLIN
        "java"      -> Language.JAVA
        "xml", "html", "svg" -> Language.XML
        else        -> Language.PLAIN
    }
}
