package com.example.dungappedit.ui.edit.tools

import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.dungappedit.R
import com.yalantis.ucrop.UCrop
import java.io.File
import com.example.dungappedit.canvas.DrawOnImageView
import com.example.dungappedit.ui.edit.utils.ImageLayerController
import android.graphics.BitmapFactory
import android.view.ViewGroup
import java.io.FileOutputStream
import java.util.UUID

class CropToolManager(
    private val fragment: Fragment,
    private val drawView: DrawOnImageView,
    private val imageLayerController: ImageLayerController
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
        val bitmap = captureFullLayout()
        val tempUri = saveBitmapToTempFile(bitmap)
        latestTempUri = tempUri

        val destinationUri =
            Uri.fromFile(File(fragment.requireContext().cacheDir, "cropped_image_${UUID.randomUUID()}.jpg"))

        val context = fragment.requireContext()
        val uCropOptions = UCrop.of(tempUri, destinationUri)
            .withMaxResultSize(1080, 1080)
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
                setActiveControlsWidgetColor(
                    ContextCompat.getColor(
                        context,
                        R.color.purple_500
                    )
                )
            })

        if (aspectRatioX > 0 && aspectRatioY > 0) {
            uCropOptions.withAspectRatio(aspectRatioX, aspectRatioY)
        }

        uCropOptions.start(context, fragment)
    }

    private fun captureFullLayout(): Bitmap {
        val bitmap = Bitmap.createBitmap(drawView.width, drawView.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawView.draw(canvas)
        return bitmap
    }

    private fun saveBitmapToTempFile(bitmap: Bitmap): Uri {
        val context = fragment.requireContext()
        val tempFile = File(context.cacheDir, "temp_crop_${System.currentTimeMillis()}.jpg")
        FileOutputStream(tempFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return Uri.fromFile(tempFile)
    }

    override fun deactivate() {
        // Called when the user cancels the crop or selects another tool.
        // We reset the active state here.
        isActive = false
    }

    override fun isToolActive(): Boolean {
        return isActive
    }

    override fun applyChanges() {
        // Crop is applied via UCrop's result in the fragment's onActivityResult
    }

    fun handleCropResult(uri: Uri) {
        val bitmap =
            BitmapFactory.decodeStream(fragment.requireContext().contentResolver.openInputStream(uri))
        drawView.setBackgroundBitmap(bitmap)

        // Force the view to wrap its new content.
        val params = drawView.layoutParams
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        drawView.layoutParams = params

        // Force the view to remeasure and redraw to fit the new cropped image
        drawView.requestLayout()
        drawView.invalidate()

        setSourceUri(uri)
        isActive = false
    }
} 
