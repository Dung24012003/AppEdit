package com.example.dungappedit.ui.main

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.example.dungappedit.databinding.FragmentImagePreviewBinding

class ImagePreviewDialogFragment : DialogFragment() {

    private var _binding: FragmentImagePreviewBinding? = null
    private val binding get() = _binding!!

    private var imageUri: Uri? = null
    private var listener: OnImagePreviewListener? = null

    interface OnImagePreviewListener {
        fun onEdit(uri: Uri)
        fun onDelete(uri: Uri)
        fun onShare(uri: Uri)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnImagePreviewListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnImagePreviewListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            imageUri = it.getParcelable(ARG_IMAGE_URI)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImagePreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageUri?.let { uri ->
            Glide.with(this)
                .load(uri)
                .into(binding.previewImageView)

            binding.editButton.setOnClickListener {
                listener?.onEdit(uri)
                dismiss()
            }
            binding.deleteButton.setOnClickListener {
                listener?.onDelete(uri)
                dismiss()
            }
            binding.shareButton.setOnClickListener {
                listener?.onShare(uri)
                dismiss()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    companion object {
        private const val ARG_IMAGE_URI = "image_uri"

        @JvmStatic
        fun newInstance(uri: Uri) =
            ImagePreviewDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_IMAGE_URI, uri)
                }
            }
    }
} 
