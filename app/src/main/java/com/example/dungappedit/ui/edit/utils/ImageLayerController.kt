package com.example.dungappedit.ui.edit.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.example.dungappedit.R
import com.example.dungappedit.canvas.DrawOnImageView
import com.example.dungappedit.model.Filter

class ImageLayerController(val hostView: FrameLayout) {
    private val frameOverlay: ImageView = hostView.findViewById(R.id.image_frame_overlay)
    private val filterOverlay: ImageView = hostView.findViewById(R.id.filter_overlay)
    val drawView: DrawOnImageView = hostView.findViewById(R.id.draw_view)

    fun addFrame(frameBitmap: Bitmap) {
        // Get the layout params for the overlay
        val params = frameOverlay.layoutParams as FrameLayout.LayoutParams

        // Set the size and position of the frame overlay to match the image
        params.width = (drawView.imageRectRight - drawView.imageRectLeft).toInt()
        params.height = (drawView.imageRectBottom - drawView.imageRectTop).toInt()
        params.leftMargin = drawView.imageRectLeft.toInt()
        params.topMargin = drawView.imageRectTop.toInt()

        frameOverlay.layoutParams = params
        frameOverlay.setImageBitmap(frameBitmap)
        frameOverlay.visibility = View.VISIBLE
    }

    fun clearFrame() {
        frameOverlay.setImageBitmap(null)
        frameOverlay.visibility = View.GONE
    }

    fun applyFilter(filter: Filter) {
        // Get the current view as a bitmap
        val originalBitmap = drawView.getBitmap()

        // Create a mutable bitmap to apply the filter to
        val filteredBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(filteredBitmap)

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(filter.matrix)
        }

        // Draw the original bitmap onto the new canvas with the filter applied
        canvas.drawBitmap(originalBitmap, 0f, 0f, paint)

        filterOverlay.setImageBitmap(filteredBitmap)
        filterOverlay.visibility = View.VISIBLE
    }

    fun clearFilter() {
        filterOverlay.setImageBitmap(null)
        filterOverlay.visibility = View.GONE
    }

    fun drawPath(path: Path, paint: Paint) {
        // TODO: Implement drawing logic
    }

    fun exportBitmap(): Bitmap {
        // Get the combined bitmap from the drawing view (includes background, drawings, text, stickers)
        var finalBitmap = drawView.getBitmap()

        // If a filter is active, apply it to the bitmap
        if (filterOverlay.visibility == View.VISIBLE && filterOverlay.drawable != null) {
            val paint = Paint().apply {
                colorFilter = (filterOverlay.drawable as android.graphics.drawable.BitmapDrawable).paint.colorFilter
            }
            val filteredBitmap = Bitmap.createBitmap(finalBitmap.width, finalBitmap.height, finalBitmap.config!!)
            val canvas = Canvas(filteredBitmap)
            canvas.drawBitmap(finalBitmap, 0f, 0f, paint)
            finalBitmap = filteredBitmap
        }

        // If the frame is visible, draw it on top
        if (frameOverlay.visibility == View.VISIBLE && frameOverlay.drawable != null) {
            val config = finalBitmap.config ?: Bitmap.Config.ARGB_8888
            val bitmapWithFrame = Bitmap.createBitmap(finalBitmap.width, finalBitmap.height, config)
            val canvas = Canvas(bitmapWithFrame)
            canvas.drawBitmap(finalBitmap, 0f, 0f, null)

            // Scale frame to fit and draw
            val frameDrawable = frameOverlay.drawable
            frameDrawable.setBounds(0, 0, finalBitmap.width, finalBitmap.height)
            frameDrawable.draw(canvas)
            return bitmapWithFrame
        }

        return finalBitmap
    }
} 
