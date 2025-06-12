package com.example.dungappedit.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

sealed class MovableItem {
    abstract var x: Float
    abstract var y: Float
    var scale: Float = 1.0f
    var rotation: Float = 0f  // Rotation in degrees
    var isSelected: Boolean = false
    var doubleClickCount: Int = 0  // Track double-click events

    class TextItem(
        var text: String,
        override var x: Float,
        override var y: Float,
        val paint: Paint,
        var textStyle: TextStyle? = null
    ) : MovableItem() {

        init {
            // Initialize textStyle if not provided
            if (textStyle == null) {
                textStyle = TextStyle(
                    text = text,
                    textColor = paint.color,
                    textSize = paint.textSize
                )
            }
        }

        /**
         * Apply the current text style to the paint object
         */
        fun applyStyle() {
            textStyle?.let { style ->
                // Update the text
                text = style.text

                // Update paint properties
                paint.color = style.textColor
                paint.textSize = style.textSize

                // Set typeface based on font family and style
                val typeface = Typeface.create(style.fontFamily, style.typeface)
                paint.typeface = typeface

                // Set stroke properties
                if (style.isStrokeEnabled) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = style.strokeWidth
                    paint.color = style.strokeColor
                } else {
                    paint.style = Paint.Style.FILL
                    paint.color = style.textColor
                }

                // Set shadow properties
                if (style.isShadowEnabled) {
                    paint.setShadowLayer(
                        style.shadowRadius,
                        style.shadowDx,
                        style.shadowDy,
                        style.shadowColor
                    )
                } else {
                    paint.clearShadowLayer()
                }
            }
        }

        /**
         * Get the bounding box for multi-line text.
         * The bounds are unscaled.
         */
        fun getBounds(): RectF {
            val lines = text.split('\n')
            if (lines.isEmpty()) return RectF()

            var maxWidth = 0f
            lines.forEach { line ->
                val width = paint.measureText(line)
                if (width > maxWidth) {
                    maxWidth = width
                }
            }

            val fontMetrics = paint.fontMetrics
            // y is the baseline of the first line.
            val top = y + fontMetrics.ascent
            // The baseline of the last line is at y + (lines.size - 1) * paint.fontSpacing
            val bottom = y + ((lines.size - 1) * paint.fontSpacing) + fontMetrics.descent
            
            val left = x
            val right = x + maxWidth
            
            return RectF(left, top, right, bottom)
        }

        /**
         * Draw text with both fill and stroke if stroke is enabled
         */
        fun drawWithStyle(canvas: Canvas) {
            textStyle?.let { style ->
                // Save canvas state
                canvas.save()
                
                // Get unscaled bounds to calculate the center for transformations
                val bounds = getBounds()
                val centerX = bounds.centerX()
                val centerY = bounds.centerY()

                // Apply transformations to the canvas
                canvas.rotate(rotation, centerX, centerY)
                canvas.scale(scale, scale, centerX, centerY)

                val lines = text.split('\n')
                val lineHeight = paint.fontSpacing
                var currentY = y // Start drawing from the first line's baseline

                for (line in lines) {
                    // If stroke is enabled, draw stroke and fill separately
                    if (style.isStrokeEnabled) {
                        // Draw stroke
                        val strokePaint = Paint(paint)
                        strokePaint.style = Paint.Style.STROKE
                        strokePaint.strokeWidth = style.strokeWidth
                        strokePaint.color = style.strokeColor
                        // Apply shadow from original paint if enabled
                        if (style.isShadowEnabled) {
                            strokePaint.setShadowLayer(style.shadowRadius, style.shadowDx, style.shadowDy, style.shadowColor)
                        }
                        canvas.drawText(line, x, currentY, strokePaint)
                        
                        // Draw fill
                        val fillPaint = Paint(paint)
                        fillPaint.style = Paint.Style.FILL
                        fillPaint.color = style.textColor
                        // Apply shadow from original paint if enabled
                        if (style.isShadowEnabled) {
                            fillPaint.setShadowLayer(style.shadowRadius, style.shadowDx, style.shadowDy, style.shadowColor)
                        }
                        canvas.drawText(line, x, currentY, fillPaint)
                    } else {
                        // Just draw the text normally
                        canvas.drawText(line, x, currentY, paint)
                    }
                    currentY += lineHeight
                }
                
                // Restore canvas state
                canvas.restore()
            }
        }
    }

    class ImageItem(
        val bitmap: Bitmap,
        override var x: Float,
        override var y: Float
    ) : MovableItem()
} 