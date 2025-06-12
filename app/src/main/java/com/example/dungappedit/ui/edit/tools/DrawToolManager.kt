package com.example.dungappedit.ui.edit.tools

import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import com.example.dungappedit.canvas.DrawOnImageView
import com.github.dhaval2404.colorpicker.ColorPickerDialog
import com.github.dhaval2404.colorpicker.model.ColorShape

class DrawToolManager(
    private val drawView: DrawOnImageView,
    private val drawControls: View, // A layout containing color picker button, size seekbar, etc.
) : BaseToolManager {

    private var lastColor = Color.BLACK

    override fun activate() {
        drawControls.visibility = View.VISIBLE
        drawView.enableDrawing(true)
        drawView.setEraseMode(false) // Default to drawing
    }

    override fun deactivate() {
        drawControls.visibility = View.GONE
        drawView.enableDrawing(false)
    }

    override fun applyChanges() {
        // Drawing is real-time. This could merge the drawing layer if needed.
    }

    override fun isToolActive(): Boolean {
        return drawView.isDrawingEnabled
    }

    fun setupListeners(colorPickerButton: Button, brushSizeSeekBar: SeekBar, eraserButton: Button, clearButton: Button) {
        colorPickerButton.setOnClickListener {
            ColorPickerDialog
                .Builder(it.context)
                .setColorShape(ColorShape.SQAURE)
                .setColorListener { color, _ ->
                    lastColor = color
                    drawView.setPaintColor(color)
                }
                .show()
        }

        brushSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Map progress (0-100) to a reasonable stroke width range (e.g., 1-100)
                drawView.setDrawingSize(progress.toFloat().coerceAtLeast(1f))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        eraserButton.setOnClickListener {
            drawView.setEraseMode(true)
        }
        
        clearButton.setOnClickListener {
            drawView.clearDrawings()
        }
    }
} 