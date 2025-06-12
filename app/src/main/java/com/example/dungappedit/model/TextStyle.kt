package com.example.dungappedit.model

import android.graphics.Color
import android.graphics.Typeface

/**
 * Data class that holds all styling information for text items
 */
data class TextStyle(
    var text: String = "Sample Text",
    var textColor: Int = Color.BLACK,
    var textSize: Float = 60f,
    var fontFamily: String = "sans-serif",
    var typeface: Int = Typeface.NORMAL,

    // Stroke properties
    var isStrokeEnabled: Boolean = false,
    var strokeColor: Int = Color.BLACK,
    var strokeWidth: Float = 2f,

    // Shadow properties
    var isShadowEnabled: Boolean = false,
    var shadowColor: Int = Color.parseColor("#80000000"), // 50% transparent black
    var shadowRadius: Float = 5f,
    var shadowDx: Float = 5f,
    var shadowDy: Float = 5f
) {
    /**
     * Create a copy of this TextStyle
     */
    fun copy(): TextStyle {
        return TextStyle(
            text = text,
            textColor = textColor,
            textSize = textSize,
            fontFamily = fontFamily,
            typeface = typeface,
            isStrokeEnabled = isStrokeEnabled,
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            isShadowEnabled = isShadowEnabled,
            shadowColor = shadowColor,
            shadowRadius = shadowRadius,
            shadowDx = shadowDx,
            shadowDy = shadowDy
        )
    }
} 
