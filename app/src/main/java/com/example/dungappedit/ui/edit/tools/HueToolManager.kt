package com.example.dungappedit.ui.edit.tools

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import com.example.dungappedit.R
import com.example.dungappedit.canvas.DrawOnImageView
import kotlin.math.cos
import kotlin.math.sin

class HueToolManager(
    private val hueControlsContainer: View,
    private val hueSeekBar: SeekBar,
    private val saturationSeekBar: SeekBar,
    private val drawView: DrawOnImageView
) : BaseToolManager {

    // Reset button reference
    private val resetButton: Button? = hueControlsContainer.findViewById(R.id.btn_reset_hue)

    // Current, real-time values being applied.
    private var currentHue = 180f        // Range: 0-360 degrees, where 180 is the default.
    private var currentSaturation = 1f // Range: 0-2, where 1 is the default.

    // Values saved from the last modification to persist state.
    private var savedHue = 180f
    private var savedSaturation = 1f

    private var valuesModified = false

    init {
        setupListeners()
        setupResetButton()
    }

    private fun setupResetButton() {
        resetButton?.setOnClickListener {
            // Reset all values to their defaults.
            currentHue = 180f
            currentSaturation = 1f
            savedHue = 180f
            savedSaturation = 1f

            // Update UI controls.
            hueSeekBar.progress = 180
            saturationSeekBar.progress = 100

            // Re-apply the combined filter/hue effect.
            applyHueAndSaturation()

            // Provide visual feedback for the reset action.
            resetButton.alpha = 0.5f
            resetButton.isEnabled = false
            resetButton.postDelayed({
                resetButton.alpha = 1.0f
                updateResetButtonState()
            }, 300)
        }
    }

    private fun setupListeners() {
        hueSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentHue = progress.toFloat()
                    applyHueAndSaturation()
                    updateResetButtonState()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                savedHue = currentHue // Save the value only when user finishes adjustment.
            }
        })

        saturationSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentSaturation = progress / 100f
                    applyHueAndSaturation()
                    updateResetButtonState()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                savedSaturation = currentSaturation // Save the value only when user finishes adjustment.
            }
        })
    }

    private fun applyHueAndSaturation() {
        // Get the current base filter from the FilterToolManager.
        val activeFilter = filterToolManager?.getActiveFilter()
        val filterMatrix = activeFilter?.let {
            if (it.name != "None") it.matrix else null
        }

        // Create a new color matrix for the current hue and saturation adjustments.
        val adjustmentMatrix = ColorMatrix()
        adjustSaturation(adjustmentMatrix, currentSaturation)
        adjustHue(adjustmentMatrix, currentHue)

        // Combine the base filter matrix with the hue/saturation adjustments.
        val combinedMatrix = when {
            filterMatrix != null -> {
                ColorMatrix(filterMatrix).apply { postConcat(adjustmentMatrix) }
            }
            else -> adjustmentMatrix
        }

        // Apply the final combined effect to the image view.
        val colorFilter = ColorMatrixColorFilter(combinedMatrix)
        drawView.setBackgroundImageFilter(colorFilter)
    }

    private fun adjustHue(cm: ColorMatrix, hue: Float) {
        // Hue is adjusted from a default of 180.
        val adjustedHue = (hue - 180)

        if (adjustedHue == 0f) return // No change needed.

        val value = adjustedHue * Math.PI.toFloat() / 180f
        val cos = cos(value)
        val sin = sin(value)

        // Luminance constants for color rotation.
        val lumR = 0.213f
        val lumG = 0.715f
        val lumB = 0.072f

        // Matrix for hue rotation.
        val mat = floatArrayOf(
            lumR + cos * (1 - lumR) + sin * (-lumR),      lumG + cos * (-lumG) + sin * (-lumG),      lumB + cos * (-lumB) + sin * (1 - lumB),  0f, 0f,
            lumR + cos * (-lumR) + sin * (0.143f),       lumG + cos * (1 - lumG) + sin * (0.140f),  lumB + cos * (-lumB) + sin * (-0.283f), 0f, 0f,
            lumR + cos * (-lumR) + sin * (-(1 - lumR)),  lumG + cos * (-lumG) + sin * (lumG),       lumB + cos * (1 - lumB) + sin * (lumB),   0f, 0f,
            0f, 0f, 0f, 1f, 0f,
            0f, 0f, 0f, 0f, 1f
        )

        cm.postConcat(ColorMatrix(mat))
    }

    private fun adjustSaturation(cm: ColorMatrix, saturation: Float) {
        cm.setSaturation(saturation)
    }

    // Reference to FilterToolManager for combining filter effects.
    private var filterToolManager: FilterToolManager? = null

    /**
     * Sets the FilterToolManager reference to allow combining color effects.
     */
    fun setFilterToolManager(filterManager: FilterToolManager) {
        this.filterToolManager = filterManager
    }

    override fun activate() {
        // Restore saved values.
        currentHue = savedHue
        currentSaturation = savedSaturation

        // Update UI controls to match the restored values.
        hueSeekBar.progress = currentHue.toInt()
        saturationSeekBar.progress = (currentSaturation * 100).toInt()

        // Re-apply the combined filter and hue/saturation effect.
        applyHueAndSaturation()

        // Show the controls and update the reset button state.
        hueControlsContainer.visibility = View.VISIBLE
        updateResetButtonState()
    }

    /**
     * Updates the reset button's appearance based on whether adjustments have been made.
     */
    private fun updateResetButtonState() {
        resetButton?.let { button ->
            val isDefault = currentHue == 180f && currentSaturation == 1f
            button.alpha = if (isDefault) 0.5f else 1.0f
            button.isEnabled = !isDefault
        }
    }

    override fun deactivate() {
        hueControlsContainer.visibility = View.GONE
    }

    override fun isToolActive(): Boolean {
        return hueControlsContainer.visibility == View.VISIBLE
    }

    override fun applyChanges() {
        // Changes are applied in real-time.
    }

    fun resetHueAndSaturation() {
        // Reset both current and saved values to defaults
        currentHue = 180f
        currentSaturation = 1f
        savedHue = 180f
        savedSaturation = 1f
        valuesModified = false

        // Update UI if visible
        if (hueControlsContainer.visibility == View.VISIBLE) {
            hueSeekBar.progress = 180
            saturationSeekBar.progress = 100
        }

        // Get current filter from FilterToolManager if available
        val activeFilter = filterToolManager?.getActiveFilter()
        val filterMatrix = activeFilter?.let {
            if (it.name != "None") it.matrix else null
        }

        // Apply just the filter without hue adjustments
        if (filterMatrix != null) {
            drawView.setBackgroundImageFilter(ColorMatrixColorFilter(filterMatrix))
        } else {
            drawView.setBackgroundImageFilter(null)
        }
    }

    /**
     * Combines the current hue/saturation matrix with a given filter matrix.
     * Returns the combined matrix, or the original filter/hue matrix if one is null.
     */
    fun combineWithFilterMatrix(filterMatrix: ColorMatrix?): ColorMatrix? {
        val hueSaturationMatrix = getHueSaturationMatrix()

        return when {
            filterMatrix != null && hueSaturationMatrix != null -> {
                ColorMatrix(filterMatrix).apply { postConcat(hueSaturationMatrix) }
            }
            else -> filterMatrix ?: hueSaturationMatrix
        }
    }

    /**
     * Returns a ColorMatrix representing the current hue and saturation adjustments.
     * Returns null if no adjustments are active (i.e., values are at their defaults).
     */
    private fun getHueSaturationMatrix(): ColorMatrix? {
        if (currentHue == 180f && currentSaturation == 1f) {
            return null
        }

        return ColorMatrix().apply {
            adjustSaturation(this, currentSaturation)
            adjustHue(this, currentHue)
        }
    }
} 
