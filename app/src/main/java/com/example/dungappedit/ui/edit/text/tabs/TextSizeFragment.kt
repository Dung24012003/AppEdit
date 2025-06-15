package com.example.dungappedit.ui.edit.text.tabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import com.example.dungappedit.databinding.FragmentTextSizeBinding
import com.example.dungappedit.ui.edit.text.TextEditorDialogFragment

class TextSizeFragment : Fragment() {

    private lateinit var binding: FragmentTextSizeBinding
    private val textEditorDialog: TextEditorDialogFragment
        get() = parentFragment as TextEditorDialogFragment

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTextSizeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textSizeSeekBar.progress = (textEditorDialog.currentTextStyle.textSize / 2).toInt()
        binding.textSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                textEditorDialog.currentTextStyle.textSize = progress.toFloat() * 2 // Scale to 0-200
                textEditorDialog.updatePreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }
} 