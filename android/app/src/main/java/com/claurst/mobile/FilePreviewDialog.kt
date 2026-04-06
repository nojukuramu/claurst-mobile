package com.claurst.mobile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.claurst.mobile.databinding.DialogFilePreviewBinding
import java.io.File

/**
 * Simple read-only dialog that shows the first 8 KB of a text file.
 */
class FilePreviewDialog(private val file: File) : DialogFragment() {

    private var _binding: DialogFilePreviewBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFilePreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.setTitle(file.name)
        binding.tvContent.text = try {
            file.readText(Charsets.UTF_8).take(8192)
        } catch (e: Exception) {
            "(binary or unreadable file)"
        }
        binding.btnClose.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
