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
        binding.btnSave.isEnabled = false // Vô hiệu hóa nút để tránh click nhiều lần
        val bitmapToSave = editHostFragment?.captureEdits()
        if (bitmapToSave != null) {
            try {
                saveBitmapToGallery(bitmapToSave)
                Toast.makeText(this, "Image saved successfully", Toast.LENGTH_SHORT).show()
                finish() // Đóng màn hình chỉnh sửa sau khi lưu thành công
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to save image: ${e.message}", Toast.LENGTH_SHORT).show()
                bitmapToSave.recycle()
                binding.btnSave.isEnabled = true // Kích hoạt lại nút nếu lưu thất bại
            }
        } else {
            Toast.makeText(this, "Failed to capture image for saving.", Toast.LENGTH_SHORT).show()
            binding.btnSave.isEnabled = true
        }
    }

    private fun loadImage() {
        // Nhận Uri dưới dạng String và chuyển đổi lại để an toàn hơn
        val uriString = intent.getStringExtra(EXTRA_IMAGE_URI)
        if (uriString == null) {
            Toast.makeText(this, "No image provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val imageUri = Uri.parse(uriString)

        try {
            // Use the utility to load the bitmap with correct orientation.
            val bitmap = ImageOrientationUtil.loadBitmapWithCorrectOrientation(contentResolver, imageUri)
            if (bitmap != null) {
                // Pass a bitmap to fragment might be risky for large images, but let's assume it's handled.
                // A better approach would be passing the Uri and let the fragment load it.
                // For now, we will stick to creating a new fragment instance with the Uri.
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
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DungAppEdit")
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
                // Nếu có lỗi, xóa bản ghi đã tạo
                resolver.delete(it, null, null)
                throw e
            }
        } ?: throw IOException("Failed to create new MediaStore record.")
    }

    private fun setupTabLayout() {
        // Cấu hình cho toolbar chính
        binding.toolbar.apply {
            addTab(newTab().setText("Frame"))
            addTab(newTab().setText("Tool"))
            addTab(newTab().setText("Filter"))
            clearOnTabSelectedListeners()
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
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
            getTabAt(0)?.select()
        }
    }
}
