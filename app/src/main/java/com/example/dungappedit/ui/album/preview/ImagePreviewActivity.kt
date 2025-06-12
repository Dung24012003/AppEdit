package com.example.dungappedit.ui.album.preview

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.dungappedit.common.Constans
import com.example.dungappedit.databinding.ImagePreviewActivityBinding
import java.io.File

class ImagePreviewActivity : AppCompatActivity() {
    private lateinit var binding: ImagePreviewActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ImagePreviewActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val path = intent.getStringExtra(Constans.KEY_DATA_IMG)

        if (path != null) {
            val file = File(path)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                binding.imageViewPreview.setImageBitmap(bitmap)
            }
        }
    }
}
