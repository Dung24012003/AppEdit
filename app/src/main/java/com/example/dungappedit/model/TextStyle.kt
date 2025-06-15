package com.example.dungappedit.model

import android.graphics.Color
import android.graphics.Typeface
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Data class that holds all styling information for text items
 */
@Parcelize
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
) : Parcelable
