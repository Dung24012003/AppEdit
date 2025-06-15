package com.example.dungappedit.ui.edit.utils

import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.example.dungappedit.R
import com.example.dungappedit.canvas.DrawOnImageView

/**
 * Manages the layering of frames and other image elements over the main drawing canvas.
 */
class ImageLayerController(val hostView: FrameLayout) {
    private val frameOverlay: ImageView = hostView.findViewById(R.id.image_frame_overlay)
    val drawView: DrawOnImageView = hostView.findViewById(R.id.draw_view)

    /**
     * Adds a frame bitmap to the overlay, resizing it to fit the main image.
     */
    fun addFrame(frameBitmap: Bitmap) {
        // Update the frame's bounds to match the image every time a new frame is added.
        updateFrameBounds()

        frameOverlay.setImageBitmap(frameBitmap)
        frameOverlay.visibility = View.VISIBLE
    }

    /**
     * Updates the position and size of the frame overlay.
     * This is called when the background image changes size (e.g., after cropping)
     * or when a new frame is added.
     */
    fun updateFrameBounds() {
        val params = frameOverlay.layoutParams as FrameLayout.LayoutParams
        val imageWidth = (drawView.imageRectRight - drawView.imageRectLeft).toInt()
        val imageHeight = (drawView.imageRectBottom - drawView.imageRectTop).toInt()

        // Only update if the image dimensions are valid (greater than 0).
        if (imageWidth > 0 && imageHeight > 0) {
            params.width = imageWidth
            params.height = imageHeight
            params.leftMargin = drawView.imageRectLeft.toInt()
            params.topMargin = drawView.imageRectTop.toInt()
            frameOverlay.layoutParams = params
        }
    }

    /**
     * Removes the current frame from the overlay.
     */
    fun clearFrame() {
        frameOverlay.setImageBitmap(null)
        frameOverlay.visibility = View.GONE
    }
}
