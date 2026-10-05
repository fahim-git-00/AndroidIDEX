package com.aidex

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.aidex.databinding.ActivityMainBinding
import com.aidex.editor.Language
import com.aidex.fs.FileTreeAdapter
import com.aidex.fs.ProjectManager
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

        val root = ProjectManager.ensureSampleProject(this)
        adapter.setRoot(root)
        binding.projectPath.text = root.absolutePath

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
