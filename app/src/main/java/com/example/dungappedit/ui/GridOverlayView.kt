package com.example.dungappedit.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class GridOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        color = android.graphics.Color.WHITE
        strokeWidth = 2f
        alpha = 100
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val thirdWidth = width / 3f
        val thirdHeight = height / 3f

        for (i in 1..2) {
            canvas.drawLine(i * thirdWidth, 0f, i * thirdWidth, height.toFloat(), paint)
            canvas.drawLine(0f, i * thirdHeight, width.toFloat(), i * thirdHeight, paint)
        }
    }
}
