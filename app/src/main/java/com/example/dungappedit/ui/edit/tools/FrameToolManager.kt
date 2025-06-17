package com.example.dungappedit.ui.edit.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.example.dungappedit.R
import com.example.dungappedit.model.FrameItem
import com.example.dungappedit.ui.edit.ui.adapter.FrameAdapter
import com.example.dungappedit.ui.edit.utils.ImageLayerController
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
    private var activeFrame: FrameItem? = null

    init {
        val frames = loadFrames()
        frameAdapter = FrameAdapter(frames) { frame ->
            onFrameSelected(frame)
        }
        frameRecyclerView.adapter = frameAdapter

        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8
        frameCache = LruCache(cacheSize)
    }

    fun preloadFrames() {
        coroutineScope.launch {
            // Get image dimensions directly from the DrawOnImageView's visible image rectangle.
            val targetWidth = (imageLayerController.drawView.imageRectRight - imageLayerController.drawView.imageRectLeft).toInt()
            val targetHeight = (imageLayerController.drawView.imageRectBottom - imageLayerController.drawView.imageRectTop).toInt()

            // If dimensions are invalid (e.g., image not loaded yet), do nothing.
            if (targetWidth <= 0 || targetHeight <= 0) return@launch

            val framesToLoad = loadFrames().filter { it.name != "None" }
            for (frame in framesToLoad) {
                launch(Dispatchers.IO) {
                    if (frameCache.get(frame.frameResourceId) == null) {
                        try {
                            val bitmap = decodeSampledBitmapFromResource(frame.frameResourceId, targetWidth, targetHeight)
                            // Scaling ensures the frame precisely matches the target dimensions.
                            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                            frameCache.put(frame.frameResourceId, scaledBitmap)
                            bitmap.recycle() // Recycle the intermediate bitmap to save memory.
                        } catch (e: Exception) {
                            // Handle potential loading errors.
                        }
                    }
                }
            }
        }
    }

    override fun activate() {
        frameRecyclerView.visibility = View.VISIBLE
    }

    override fun deactivate() {
        frameRecyclerView.visibility = View.GONE
    }

    override fun isToolActive(): Boolean {
        return frameRecyclerView.visibility == View.VISIBLE
    }

    override fun applyChanges() {
        // Not needed; frames are applied instantly.
    }

    fun removeFrame() {
        imageLayerController.clearFrame()
        activeFrame = null
    }

    private fun loadFrames(): List<FrameItem> {
        return listOf(
            FrameItem(R.drawable.text, "None"),
            FrameItem(R.drawable.frame, "Frame 1"),
            FrameItem(R.drawable.frame2, "Frame 2"),
            FrameItem(R.drawable.frame3, "Frame 3"),
            FrameItem(R.drawable.frame4, "Frame 4"),
            FrameItem(R.drawable.frame5, "Frame 5"),
            FrameItem(R.drawable.frame, "Frame 1"),
            FrameItem(R.drawable.frame2, "Frame 2"),
            FrameItem(R.drawable.frame3, "Frame 3"),
            FrameItem(R.drawable.frame4, "Frame 4"),
            FrameItem(R.drawable.frame5, "Frame 5")
        )
    }

    private fun onFrameSelected(frame: FrameItem) {
        if (frame.name == "None") {
            removeFrame()
            return
        }

        activeFrame = frame

        // Get the latest image dimensions from DrawOnImageView.
        val targetWidth = (imageLayerController.drawView.imageRectRight - imageLayerController.drawView.imageRectLeft).toInt()
        val targetHeight = (imageLayerController.drawView.imageRectBottom - imageLayerController.drawView.imageRectTop).toInt()

        // If dimensions are invalid, do nothing.
        if (targetWidth <= 0 || targetHeight <= 0) return

        // Check the cache first for a matching bitmap.
        val cachedBitmap = frameCache.get(frame.frameResourceId)
        if (cachedBitmap != null && cachedBitmap.width == targetWidth && cachedBitmap.height == targetHeight) {
            imageLayerController.addFrame(cachedBitmap)
            return
        }

        // If not in cache or size mismatches, load and scale it.
        coroutineScope.launch {
            val scaledBitmap = withContext(Dispatchers.IO) {
                val bitmap = decodeSampledBitmapFromResource(frame.frameResourceId, targetWidth, targetHeight)
                Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true).also {
                    bitmap.recycle()
                }
            }
            withContext(Dispatchers.Main) {
                // Remove the old cache entry and add the new one.
                frameCache.remove(frame.frameResourceId)
                frameCache.put(frame.frameResourceId, scaledBitmap)
                imageLayerController.addFrame(scaledBitmap)
            }
        }
    }

    fun getActiveFrameBitmap(): Bitmap? {
        return activeFrame?.let { frameCache.get(it.frameResourceId) }
    }

    private fun decodeSampledBitmapFromResource(resId: Int, reqWidth: Int, reqHeight: Int): Bitmap {
        val resources = frameRecyclerView.context.resources
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeResource(resources, resId, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeResource(resources, resId, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
