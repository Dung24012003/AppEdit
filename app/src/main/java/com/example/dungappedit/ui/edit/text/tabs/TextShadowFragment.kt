package com.example.dungappedit.ui.edit.text.tabs

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.dungappedit.R
import com.example.dungappedit.databinding.FragmentTextShadowBinding
import com.example.dungappedit.ui.edit.text.ColorPickerAdapter
import com.example.dungappedit.ui.edit.text.TextEditorDialogFragment

class TextShadowFragment : Fragment() {

    private lateinit var binding: FragmentTextShadowBinding
    private val textEditorDialog: TextEditorDialogFragment
        get() = parentFragment as TextEditorDialogFragment

    private val colors = listOf(
        Color.BLACK, Color.GRAY, Color.DKGRAY,
        Color.RED, Color.GREEN, Color.BLUE
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTextShadowBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupInitialState()
        setupListeners()
    }

    private fun setupInitialState() {
        val style = textEditorDialog.currentTextStyle
        val isEnabled = style.isShadowEnabled
        val maxRadius = binding.shadowRadiusSeekBar.max.toFloat()

        // Set switch state
        binding.shadowSwitch.isChecked = isEnabled

        // Set seekbar progress
        binding.shadowRadiusSeekBar.progress = (maxRadius - style.shadowRadius).toInt().coerceIn(0, maxRadius.toInt())
        binding.shadowDxSeekBar.progress = style.shadowDx.toInt()
        binding.shadowDySeekBar.progress = style.shadowDy.toInt()

        // Set control visibility
        updateControlsVisibility(isEnabled)
    }

    private fun setupListeners() {
        // Switch listener
        binding.shadowSwitch.setOnCheckedChangeListener { _, isChecked ->
            textEditorDialog.currentTextStyle.isShadowEnabled = isChecked
            updateControlsVisibility(isChecked)
            textEditorDialog.updatePreview()
        }

        // SeekBar listener
        val maxRadius = binding.shadowRadiusSeekBar.max.toFloat()
        val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                when (seekBar.id) {
                    R.id.shadow_radius_seek_bar -> {
                        // Coerce at 1f to prevent shadow from disappearing completely
                        textEditorDialog.currentTextStyle.shadowRadius = (maxRadius - progress.toFloat()).coerceAtLeast(1f)
                    }
                    R.id.shadow_dx_seek_bar -> textEditorDialog.currentTextStyle.shadowDx = progress.toFloat()
                    R.id.shadow_dy_seek_bar -> textEditorDialog.currentTextStyle.shadowDy = progress.toFloat()
                }
                textEditorDialog.updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        }
        binding.shadowRadiusSeekBar.setOnSeekBarChangeListener(seekBarListener)
        binding.shadowDxSeekBar.setOnSeekBarChangeListener(seekBarListener)
        binding.shadowDySeekBar.setOnSeekBarChangeListener(seekBarListener)
        
        // Color picker
        val adapter = ColorPickerAdapter(colors) { color ->
            textEditorDialog.currentTextStyle.shadowColor = color
            textEditorDialog.updatePreview()
        }
        binding.shadowColorRecyclerView.layoutManager = GridLayoutManager(context, 6)
        binding.shadowColorRecyclerView.adapter = adapter
        adapter.setSelectedColor(textEditorDialog.currentTextStyle.shadowColor)
    }
    
    private fun updateControlsVisibility(isEnabled: Boolean) {
        val visibility = if (isEnabled) View.VISIBLE else View.GONE
        
        binding.shadowRadiusLabel.visibility = visibility
        binding.shadowRadiusSeekBar.visibility = visibility
        binding.shadowDxLabel.visibility = visibility
        binding.shadowDxSeekBar.visibility = visibility
        binding.shadowDyLabel.visibility = visibility
        binding.shadowDySeekBar.visibility = visibility
        binding.shadowColorLabel.visibility = visibility
        binding.shadowColorRecyclerView.visibility = visibility
    }
} 
