package com.example.dungappedit.ui.camera.stikcer

import androidx.annotation.DrawableRes
import com.example.dungappedit.R

enum class Sticker(@DrawableRes val drawableId: Int) {
    NONE(0), // Sticker đặc biệt để chỉ không chọn gì
    HAT(R.drawable.stikcer6),
    HATBunny(R.drawable.stikcerbunny),
    FaceBatman(R.drawable.stikcerbatman),
    GLASSES(R.drawable.stikcer1),
    FaceCat(R.drawable.stikcetcat),
    MASK(R.drawable.stikcermask);

    // Get the display name
    fun getDisplayName(): String {
        return when (this) {
            NONE -> "Off"
            FaceBatman -> "Batman"
            HATBunny -> "Bunny Ears"
            HAT -> "Hat"
            GLASSES -> "Glasses"
            FaceCat -> "Cat Face"
            MASK -> "Mask"
        }
    }
}
