package com.example.dungappedit.ui.edit.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.dungappedit.R
import com.example.dungappedit.canvas.DrawOnImageView
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class CropToolManager(
    private val fragment: Fragment,
    private val drawView: DrawOnImageView,
    private val filterToolManager: FilterToolManager
) : BaseToolManager {

    private var sourceUri: Uri? = null
    private var isActive: Boolean = false
    private var latestTempUri: Uri? = null

    fun setSourceUri(uri: Uri) {
        this.sourceUri = uri
    }

    override fun activate() {
        if (isActive) return
        isActive = true
        startCrop()
    }

    private fun startCrop(aspectRatioX: Float = 0f, aspectRatioY: Float = 0f) {
        // Deselect any selected item (text/sticker) to hide its controls.
        drawView.clearSelection()

        // Capture the current image state to pass to the cropper.
        val bitmap = captureFullLayout()
        val tempUri = saveBitmapToTempFile(bitmap)
        latestTempUri = tempUri

        val destinationUri =
            Uri.fromFile(File(fragment.requireContext().cacheDir, "cropped_image_${UUID.randomUUID()}.jpg"))

        val context = fragment.requireContext()
        val uCropOptions = UCrop.of(tempUri, destinationUri)
            .withMaxResultSize(2000, 2000)
            .withOptions(UCrop.Options().apply {
                setHideBottomControls(false)
                setFreeStyleCropEnabled(true)
                setShowCropFrame(true)
                setShowCropGrid(true)
                setCircleDimmedLayer(false)
                setCropGridColumnCount(3)
                setCropGridRowCount(3)
                setToolbarWidgetColor(ContextCompat.getColor(context, R.color.white))
                setRootViewBackgroundColor(ContextCompat.getColor(context, R.color.white))
                setStatusBarColor(ContextCompat.getColor(context, R.color.purple_500))
                setToolbarColor(ContextCompat.getColor(context, R.color.purple_500))
                setToolbarTitle(context.getString(R.string.crop_title))
                setActiveControlsWidgetColor(ContextCompat.getColor(context, R.color.purple_500))
            })

        if (aspectRatioX > 0 && aspectRatioY > 0) {
            uCropOptions.withAspectRatio(aspectRatioX, aspectRatioY)
        }

        uCropOptions.start(context, fragment)
    }

    private fun captureFullLayout(): Bitmap {
        return drawView.getCurrentImageState()
    }

    private fun saveBitmapToTempFile(bitmap: Bitmap): Uri {
        val context = fragment.requireContext()
        val tempFile = File(context.cacheDir, "temp_crop_${System.currentTimeMillis()}.jpg")
        FileOutputStream(tempFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        return Uri.fromFile(tempFile)
    }

    override fun deactivate() {
        isActive = false
    }

    override fun isToolActive(): Boolean {
        return isActive
    }

    override fun applyChanges() {
        // Not needed for this tool as changes are handled by the cropping activity.
    }

    fun handleCropResult(uri: Uri) {
        val bitmap = BitmapFactory.decodeStream(fragment.requireContext().contentResolver.openInputStream(uri))

        drawView.clearAll()
        drawView.setBackgroundBitmap(bitmap)
        filterToolManager.applyOriginalFilter()

        setSourceUri(uri)
        isActive = false
    }
}
