package com.claurst.mobile

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.claurst.mobile.databinding.ActivityWorkspaceBinding
import java.io.File

/**
 * Workspace file browser.
 *
 * Shows the contents of the sandboxed workspace directory in a list.
 * Tapping a directory navigates into it; tapping a file shows a simple
 * read-only preview (or can be extended to open an editor).
 */
class WorkspaceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorkspaceBinding
    private lateinit var workspaceManager: WorkspaceManager
    private lateinit var adapter: FileAdapter

    /** Current directory being shown. */
    private var currentDir: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkspaceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            title = getString(R.string.workspace_title)
            setDisplayHomeAsUpEnabled(true)
        }

        workspaceManager = WorkspaceManager(this)
        workspaceManager.ensureWorkspaceExists()

        adapter = FileAdapter { file ->
            if (file.isDirectory) {
                navigateTo(file)
            } else {
                FilePreviewDialog(file).show(supportFragmentManager, "preview")
            }
        }

        binding.recyclerFiles.layoutManager = LinearLayoutManager(this)
        binding.recyclerFiles.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            refresh()
            binding.swipeRefresh.isRefreshing = false
        }

        navigateTo(workspaceManager.workspaceDir)
    }

    private fun navigateTo(dir: File) {
        currentDir = dir
        supportActionBar?.subtitle = dir.relativeTo(workspaceManager.workspaceDir)
            .path
            .ifEmpty { "/" }
        refresh()
    }

    private fun refresh() {
        val dir = currentDir ?: return
        val entries = workspaceManager.listDirectory(dir)
        adapter.submitList(entries)
        binding.tvEmpty.visibility =
            if (entries.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            val parent = currentDir?.parentFile
            val root = workspaceManager.workspaceDir
            if (parent != null && parent.canonicalPath.startsWith(root.canonicalPath)) {
                navigateTo(parent)
            } else {
                finish()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val parent = currentDir?.parentFile
        val root = workspaceManager.workspaceDir
        if (parent != null && parent.canonicalPath.startsWith(root.canonicalPath)) {
            navigateTo(parent)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
