package com.example.dungappedit.ui.edit.tools

import androidx.recyclerview.widget.RecyclerView
import com.example.dungappedit.adapter.FrameAdapter
import com.example.dungappedit.model.FrameItem
import com.example.dungappedit.ui.edit.utils.ImageLayerController
import com.example.dungappedit.R
import android.graphics.BitmapFactory
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import android.view.View
import com.example.dungappedit.model.Frame
import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FrameToolManager(
    private val frameRecyclerView: RecyclerView,
    private val imageLayerController: ImageLayerController,
    private val coroutineScope: CoroutineScope
) : BaseToolManager {

    private val frameAdapter: FrameAdapter
    private val frameCache: LruCache<Int, Bitmap>

    init {
        val frames = loadFrames()
        frameAdapter = FrameAdapter(frames) { frame ->
            onFrameSelected(frame)
        }
        frameRecyclerView.adapter = frameAdapter

        // Initialize cache with a reasonable size (e.g., 4 frames)
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8 // Use 1/8th of the available memory for this cache.
        frameCache = LruCache(cacheSize)
    }

    fun preloadFrames() {
        coroutineScope.launch {
            val targetWidth = (imageLayerController.drawView.imageRectRight - imageLayerController.drawView.imageRectLeft).toInt()
            val targetHeight = (imageLayerController.drawView.imageRectBottom - imageLayerController.drawView.imageRectTop).toInt()

            if (targetWidth == 0 || targetHeight == 0) return@launch

            val framesToLoad = loadFrames().filter { it.name != "None" }
            for (frame in framesToLoad) {
                // Launch each frame decoding in a separate job to allow for potential parallelism
                launch(Dispatchers.IO) {
                    // Skip if already cached
                    if (frameCache.get(frame.frameResourceId) == null) {
                        val bitmap = decodeSampledBitmapFromResource(
                            frame.frameResourceId,
                            targetWidth,
                            targetHeight
                        )
                        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                        // Put into cache
                        frameCache.put(frame.frameResourceId, scaledBitmap)
                    }
                }
            }
        }
    }

    override fun activate() {
        frameRecyclerView.visibility = RecyclerView.VISIBLE
    }

    override fun deactivate() {
        frameRecyclerView.visibility = RecyclerView.GONE
    }

    override fun isToolActive(): Boolean {
        return frameRecyclerView.visibility == View.VISIBLE
    }

    override fun applyChanges() {
        // Frame is applied immediately on selection, so this might not be needed.
    }

    private fun loadFrames(): List<FrameItem> {
        // This should be loaded from a more dynamic source
        return listOf(
            FrameItem(R.drawable.text, "None"), // Using a placeholder for "none"
            FrameItem(R.drawable.frame, "Frame 1"),
            FrameItem(R.drawable.frame2, "Frame 2"),
            FrameItem(R.drawable.frame3, "Frame 3"),
            FrameItem(R.drawable.frame4, "Frame 4"),
            FrameItem(R.drawable.frame5, "Frame 5")
        )
    }

    private fun onFrameSelected(frame: FrameItem) {
        if (frame.name == "None") {
            imageLayerController.clearFrame()
            return
        }

        // Check cache first
        val cachedBitmap = frameCache.get(frame.frameResourceId)
        if (cachedBitmap != null) {
            imageLayerController.addFrame(cachedBitmap)
            return
        }

        coroutineScope.launch {
            val targetWidth = (imageLayerController.drawView.imageRectRight - imageLayerController.drawView.imageRectLeft).toInt()
            val targetHeight = (imageLayerController.drawView.imageRectBottom - imageLayerController.drawView.imageRectTop).toInt()

            if (targetWidth == 0 || targetHeight == 0) return@launch

            // Perform processing in a background thread
            val scaledBitmap = withContext(Dispatchers.IO) {
                val bitmap = decodeSampledBitmapFromResource(
                    frame.frameResourceId,
                    targetWidth,
                    targetHeight
                )
                Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            }

            // Switch back to the main thread to update the UI and cache
            withContext(Dispatchers.Main) {
                frameCache.put(frame.frameResourceId, scaledBitmap)
                imageLayerController.addFrame(scaledBitmap)
            }
        }
    }

    private fun decodeSampledBitmapFromResource(resId: Int, reqWidth: Int, reqHeight: Int): Bitmap {
        val resources = frameRecyclerView.context.resources
        // First decode with inJustDecodeBounds=true to check dimensions
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeResource(resources, resId, options)

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeResource(resources, resId, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        // Raw height and width of image
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
} 
