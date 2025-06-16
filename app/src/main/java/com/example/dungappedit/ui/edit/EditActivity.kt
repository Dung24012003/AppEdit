package com.example.dungappedit.ui.edit

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.dungappedit.R
import com.example.dungappedit.databinding.ActivityEditBinding
import com.example.dungappedit.ui.edit.fragment.EditHostFragment
import com.example.dungappedit.ui.edit.utils.ImageOrientationUtil
import com.google.android.material.tabs.TabLayout
import java.io.IOException

class EditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditBinding
    private var editHostFragment: EditHostFragment? = null

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTabLayout()
        setupButtons()
        loadImage()
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener { handleSave() }
    }

    private fun handleSave() {
        val bitmapToSave = editHostFragment?.captureEdits()
        if (bitmapToSave != null) {
            try {
                saveBitmapToGallery(bitmapToSave)
                Toast.makeText(this, "Image saved successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to save image: ${e.message}", Toast.LENGTH_SHORT).show()
                bitmapToSave.recycle()
            }
        } else {
            Toast.makeText(this, "Failed to capture image for saving.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadImage() {
        val imageUri = intent.getParcelableExtra<Uri>(EXTRA_IMAGE_URI)
        if (imageUri == null) {
            Toast.makeText(this, "No image provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            // Use the utility to load the bitmap with correct orientation.
            val bitmap = ImageOrientationUtil.loadBitmapWithCorrectOrientation(contentResolver, imageUri)
            if (bitmap != null) {
                editHostFragment = EditHostFragment.newInstance(imageUri)
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, editHostFragment!!)
                    .commitNow()
            } else {
                throw IOException("Failed to decode bitmap from Uri.")
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val timestamp = System.currentTimeMillis()
        val fileName = "IMG_$timestamp.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            try {
                resolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(it, contentValues, null, null)
                }
            } catch (e: Exception) {
                throw e
            }
        } ?: throw IOException("Failed to create new MediaStore record.")
    }

    private fun setupTabLayout() {
        binding.apply {
            toolbar.addTab(toolbar.newTab().setText("Frame"))
            toolbar.addTab(toolbar.newTab().setText("Tool"))
            toolbar.addTab(toolbar.newTab().setText("Filter"))

            toolbar.clearOnTabSelectedListeners()
            toolbar.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    editHostFragment?.onTabSelected(tab.position)
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {
                    editHostFragment?.onTabUnselected(tab?.position ?: -1)
                }

                override fun onTabReselected(tab: TabLayout.Tab) {
                    editHostFragment?.onTabReselected(tab.position)
                }
            })

            saveOrNo.addTab(saveOrNo.newTab().setIcon(R.drawable.ic_close))
            saveOrNo.addTab(saveOrNo.newTab().setIcon(R.drawable.ic_check))

            saveOrNo.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) { /* TODO: Implement save/cancel logic */ }
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })

            // Select the first tab by default.
            toolbar.getTabAt(0)?.select()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // The bitmap is managed by the fragment and DrawOnImageView, no need to recycle here.
    }
}
