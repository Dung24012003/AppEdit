package com.example.dungappedit.ui.edit.text

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class StrokeTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    var strokeWidthValue: Float = 0f
    var strokeColorValue: Int = 0

    override fun onDraw(canvas: Canvas) {
        // If there is a stroke, draw it first
        if (strokeWidthValue > 0) {
            val originalColor = currentTextColor
            // Set the paint for the stroke
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidthValue
            setTextColor(strokeColorValue)
            // Draw the stroke
            super.onDraw(canvas)

            // Reset the paint for the fill
            paint.style = Paint.Style.FILL
            setTextColor(originalColor)
        }

        // Draw the filled text
        super.onDraw(canvas)
    }
} 