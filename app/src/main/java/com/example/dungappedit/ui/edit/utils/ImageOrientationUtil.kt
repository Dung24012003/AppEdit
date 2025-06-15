package com.example.dungappedit.ui.edit.utils

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import java.io.InputStream

/**
 * Utility class to handle image orientation issues from camera photos
 */
object ImageOrientationUtil {

    private const val TAG = "ImageOrientationUtil"

    /**
     * Loads a bitmap from a URI with proper orientation based on EXIF data
     * 
     * @param contentResolver ContentResolver to open the image URI
     * @param imageUri URI of the image to load
     * @return Properly oriented Bitmap or null if loading fails
     */
    fun loadBitmapWithCorrectOrientation(contentResolver: ContentResolver, imageUri: Uri): Bitmap? {
        var inputStream: InputStream? = null
        var exifInputStream: InputStream? = null
        
        try {
            var orientation = ExifInterface.ORIENTATION_NORMAL
            try {
                exifInputStream = contentResolver.openInputStream(imageUri)
                if (exifInputStream != null) {
                    val exif = ExifInterface(exifInputStream)
                    orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    Log.d(TAG, "EXIF orientation: $orientation")
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error reading EXIF data: ${e.message}")
            } finally {
                exifInputStream?.close()
            }
            
            inputStream = contentResolver.openInputStream(imageUri)
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream for image")
                return null
            }
            
            val options = BitmapFactory.Options()
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            val originalBitmap = BitmapFactory.decodeStream(inputStream, null, options)
                ?: return null
                
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postScale(-1f, 1f)
                    matrix.postRotate(90f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postScale(-1f, 1f)
                    matrix.postRotate(270f)
                }
            }
            
            return if (matrix.isIdentity) {
                originalBitmap
            } else {
                val rotatedBitmap = Bitmap.createBitmap(
                    originalBitmap, 0, 0,
                    originalBitmap.width, originalBitmap.height,
                    matrix, true
                )
                originalBitmap.recycle()
                rotatedBitmap
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap: ${e.message}")
            return null
        } finally {
            try {
                inputStream?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing input stream: ${e.message}")
            }
        }
    }
    
    /**
     * Detect if we need to handle orientation manually based on Android version
     * Android 10+ handles orientation automatically in most cases
     */
    fun shouldHandleOrientationManually(): Boolean {
        // For Android 10 (API 29) and above, orientation is often handled automatically
        // But we'll handle it manually for all versions to be consistent
        return true
    }
} 