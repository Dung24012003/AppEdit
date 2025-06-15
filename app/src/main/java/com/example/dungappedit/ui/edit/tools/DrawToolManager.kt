package com.example.dungappedit.ui.edit.tools

import android.graphics.Color
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import com.example.dungappedit.R
import com.example.dungappedit.canvas.DrawOnImageView
import com.github.dhaval2404.colorpicker.ColorPickerDialog
import com.github.dhaval2404.colorpicker.model.ColorShape

class DrawToolManager(
    private val drawView: DrawOnImageView,
    private val drawControls: View // Layout containing drawing controls.
) : BaseToolManager {

    private var lastColor = Color.BLACK
    private var penSize = 20f
    private var eraserSize = 50f
    private var onDrawingFinishedListener: (() -> Unit)? = null

    override fun activate() {
        drawControls.visibility = View.VISIBLE
        drawView.enableDrawing(true)
        drawView.setEraseMode(false) // Default to drawing mode.

        // Listener for the check button to finalize the drawing.
        val checkButton = drawControls.findViewById<ImageButton>(R.id.btn_finish_drawing)
        checkButton?.setOnClickListener {
            onDrawingFinishedListener?.invoke()
        }
    }

    override fun deactivate() {
        drawControls.visibility = View.GONE
        drawView.enableDrawing(false)
    }

    override fun applyChanges() {
        // Drawing is applied in real-time, so no explicit action is needed here.
    }

    override fun isToolActive(): Boolean {
        return drawView.isDrawingEnabled
    }

    fun setupListeners(colorPickerButton: ImageButton, brushSizeSeekBar: SeekBar, eraserButton: ImageButton, clearButton: ImageButton) {
        colorPickerButton.setOnClickListener {
            drawView.setEraseMode(false)
            drawView.setDrawingSize(penSize)
            brushSizeSeekBar.progress = penSize.toInt()
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
                val size = progress.toFloat().coerceAtLeast(1f)
                if (drawView.isEraseMode()) {
                    eraserSize = size
                } else {
                    penSize = size
                }
                drawView.setDrawingSize(size)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        eraserButton.setOnClickListener {
            drawView.setEraseMode(true)
            drawView.setDrawingSize(eraserSize)
            brushSizeSeekBar.progress = eraserSize.toInt()
        }
        
        clearButton.setOnClickListener {
            drawView.clearDrawings()
        }
    }

    fun setOnDrawingFinishedListener(listener: () -> Unit) {
        onDrawingFinishedListener = listener
    }

    fun clearDrawing() {
        drawView.clearDrawings()
    }
} 
