package com.aidex.fs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aidex.databinding.ItemFileBinding
import java.io.File

/**
 * Recursive file tree with expand/collapse. Renders a flat list of
 * visible nodes for efficient RecyclerView usage.
 */
class FileTreeAdapter(
    private val onFileClick: (File) -> Unit
) : RecyclerView.Adapter<FileTreeAdapter.VH>() {

    class Node(val file: File, val depth: Int) {
        var expanded = false
        var loaded = false
        val children = mutableListOf<Node>()

        fun load() {
            if (loaded) return
            loaded = true
            if (!file.isDirectory) return
            file.listFiles()
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?.forEach { children.add(Node(it, depth + 1)) }
        }
    }

    private val rootNodes = mutableListOf<Node>()
    private val visible = mutableListOf<Node>()

    fun setRoot(root: File) {
        rootNodes.clear()
        val n = Node(root, 0).apply {
            expanded = true
            load()
        }
        rootNodes.add(n)
        rebuild()
    }

    private fun rebuild() {
        visible.clear()
        for (n in rootNodes) {
            visible.add(n)
            if (n.expanded) addDescendants(n)
        }
        notifyDataSetChanged()
    }

    private fun addDescendants(node: Node) {
        for (c in node.children) {
            visible.add(c)
            if (c.expanded) addDescendants(c)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(visible[position])

    override fun getItemCount(): Int = visible.size

    inner class VH(private val b: ItemFileBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(node: Node) {
            val ctx = b.root.context
            val d = ctx.resources.displayMetrics.density
            val start = ((16 + node.depth * 16) * d).toInt()
            b.root.setPaddingRelative(
                start, b.root.paddingTop, b.root.paddingEnd, b.root.paddingBottom
            )

            b.fileIcon.text = when {
                node.file.isDirectory -> if (node.expanded) "v [D]" else "> [D]"
                node.file.extension.equals("kt", true) ||
                node.file.extension.equals("kts", true) -> "[KT]"
                node.file.extension.equals("java", true) -> "[JV]"
                node.file.extension.equals("xml", true) -> "[XM]"
                else -> "[--]"
            }
            b.fileName.text = node.file.name

            b.root.setOnClickListener {
                if (node.file.isDirectory) {
                    node.load()
                    node.expanded = !node.expanded
                    rebuild()
                } else {
                    onFileClick(node.file)
                }
            }
        }
    }
}
