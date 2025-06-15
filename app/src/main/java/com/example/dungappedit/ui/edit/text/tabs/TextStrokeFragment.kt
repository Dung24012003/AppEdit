package com.example.dungappedit.ui.edit.text.tabs

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.dungappedit.databinding.FragmentTextStrokeBinding
import com.example.dungappedit.ui.edit.text.ColorPickerAdapter
import com.example.dungappedit.ui.edit.text.TextEditorDialogFragment

class TextStrokeFragment : Fragment() {

    private lateinit var binding: FragmentTextStrokeBinding
    private val textEditorDialog: TextEditorDialogFragment
        get() = parentFragment as TextEditorDialogFragment

    private val colors = listOf(
        Color.BLACK, Color.WHITE, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW,
        Color.CYAN, Color.MAGENTA, Color.GRAY, Color.DKGRAY, Color.LTGRAY
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTextStrokeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupStrokeSwitch()
        setupStrokeWidthSeekBar()
        setupStrokeColorPicker()
    }

    private fun setupStrokeSwitch() {
        binding.strokeSwitch.isChecked = textEditorDialog.currentTextStyle.isStrokeEnabled
        binding.strokeSwitch.setOnCheckedChangeListener { _, isChecked ->
            textEditorDialog.currentTextStyle.isStrokeEnabled = isChecked
            updateControls()
            textEditorDialog.updatePreview()
        }
        updateControls()
    }

    private fun setupStrokeWidthSeekBar() {
        binding.strokeWidthSeekBar.progress = textEditorDialog.currentTextStyle.strokeWidth.toInt()
        binding.strokeWidthSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                textEditorDialog.currentTextStyle.strokeWidth = progress.toFloat()
                textEditorDialog.updatePreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun setupStrokeColorPicker() {
        val adapter = ColorPickerAdapter(colors) { color ->
            textEditorDialog.currentTextStyle.strokeColor = color
            textEditorDialog.updatePreview()
        }
        binding.strokeColorRecyclerView.layoutManager = GridLayoutManager(context, 6)
        binding.strokeColorRecyclerView.adapter = adapter
        adapter.setSelectedColor(textEditorDialog.currentTextStyle.strokeColor)
    }
    
    private fun updateControls() {
        val isEnabled = textEditorDialog.currentTextStyle.isStrokeEnabled
        binding.strokeWidthSeekBar.isEnabled = isEnabled
        binding.strokeColorRecyclerView.visibility = if (isEnabled) View.VISIBLE else View.GONE
    }
} 