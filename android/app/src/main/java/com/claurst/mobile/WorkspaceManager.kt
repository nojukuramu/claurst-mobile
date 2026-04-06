package com.claurst.mobile

import android.content.Context
import java.io.File

/**
 * Manages the sandboxed workspace directory inside the app's private storage.
 *
 * The workspace lives at `<filesDir>/workspace/` and is completely isolated
 * from the rest of the file system.  CLAURST receives this path as its
 * HOME and working directory so that all file operations stay sandboxed.
 */
class WorkspaceManager(private val context: Context) {

    /** Root of the sandboxed workspace. */
    val workspaceDir: File
        get() = File(context.filesDir, "workspace")

    /** Creates the workspace directory (and a sample README) if it does not exist. */
    fun ensureWorkspaceExists() {
        if (!workspaceDir.exists()) {
            workspaceDir.mkdirs()
            File(workspaceDir, "README.md").writeText(
                "# ClaURST Workspace\n\nThis is your sandboxed workspace.\n" +
                "CLAURST will create and modify files here.\n"
            )
        }
    }

    /**
     * Returns a sorted list of [File] entries in [dir]:
     * directories first (sorted by name), then files (sorted by name).
     *
     * Returns an empty list if [dir] is not inside the workspace root
     * (to prevent path traversal).
     */
    fun listDirectory(dir: File): List<File> {
        val root = workspaceDir.canonicalPath
        if (!dir.canonicalPath.startsWith(root)) return emptyList()

        val entries = dir.listFiles() ?: return emptyList()
        return entries
            .filter { !it.name.startsWith('.') }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    /** Deletes [file] if it is inside the workspace (safe delete). */
    fun deleteFile(file: File): Boolean {
        val root = workspaceDir.canonicalPath
        if (!file.canonicalPath.startsWith(root)) return false
        return file.deleteRecursively()
    }

    /** Returns the total size of the workspace in bytes. */
    fun workspaceSize(): Long = workspaceDir.walkTopDown().sumOf { it.length() }
}
