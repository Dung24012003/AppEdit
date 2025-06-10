package com.example.dungappedit.ui.camera.filter

import android.graphics.Bitmap
import android.graphics.Matrix
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.GPUImageView

class CameraFilterManager(private val gpuView: GPUImageView) {
    fun applyFilter(filter: CameraFilter) {
        gpuView.filter = filter.createFilter()
    }

    fun processImage(bitmap: Bitmap, filter: CameraFilter, rotation: Int = 0): Bitmap {
        val gpuImage = GPUImage(gpuView.context)
        gpuImage.setImage(bitmap)
        gpuImage.setFilter(filter.createFilter())
        val filteredBitmap = gpuImage.bitmapWithFilterApplied ?: bitmap

        // Apply rotation if needed
        return if (rotation != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotation.toFloat())
            Bitmap.createBitmap(
                filteredBitmap,
                0,
                0,
                filteredBitmap.width,
                filteredBitmap.height,
                matrix,
                true
            )
        } else {
            filteredBitmap
        }
    }

    //fun getCurrentFilter(): GPUImageFilter? = gpuView.filter

    //fun getCurrentBitmap(): Bitmap? = gpuView.drawToBitmap()
}
