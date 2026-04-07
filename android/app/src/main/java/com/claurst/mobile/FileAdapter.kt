package com.claurst.mobile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.claurst.mobile.databinding.ItemFileBinding
import java.io.File

/**
 * [RecyclerView] adapter that displays workspace files and directories.
 *
 * @param onFileClick  Callback invoked when the user taps an entry.
 */
class FileAdapter(
    private val onFileClick: (File) -> Unit
) : ListAdapter<File, FileAdapter.FileViewHolder>(FILE_DIFF) {

    inner class FileViewHolder(
        private val binding: ItemFileBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(file: File) {
            binding.tvFileName.text = file.name
            binding.ivFileIcon.setImageResource(
                if (file.isDirectory) R.drawable.ic_folder else R.drawable.ic_file
            )
            binding.tvFileInfo.text = if (file.isDirectory) {
                val count = file.listFiles()?.size ?: 0
                "$count item${if (count != 1) "s" else ""}"
            } else {
                formatSize(file.length())
            }
            binding.root.setOnClickListener { onFileClick(file) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder =
        FileViewHolder(
            ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val FILE_DIFF = object : DiffUtil.ItemCallback<File>() {
            override fun areItemsTheSame(oldItem: File, newItem: File) =
                oldItem.absolutePath == newItem.absolutePath

            override fun areContentsTheSame(oldItem: File, newItem: File) =
                oldItem.absolutePath == newItem.absolutePath &&
                        oldItem.lastModified() == newItem.lastModified() &&
                        oldItem.length() == newItem.length()
        }

        private fun formatSize(bytes: Long): String = when {
            bytes < 1_024 -> "$bytes B"
            bytes < 1_048_576 -> "${bytes / 1_024} KB"
            else -> "${bytes / 1_048_576} MB"
        }
    }
}
