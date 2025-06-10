package com.example.dungappedit.ui.edit

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.dungappedit.databinding.ActivityEditImageBinding
import com.example.dungappedit.R

class EditImageActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditImageBinding

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get the image URI from the intent
        val imageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_IMAGE_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_IMAGE_URI)
        }

        if (imageUri != null) {
            // Show the image in the ImageView
            binding.imagePreview.visibility = View.VISIBLE
            binding.placeholderText.visibility = View.GONE
            binding.imagePreview.setImageURI(imageUri)
        } else {
            // Show placeholder if no image was passed
            binding.imagePreview.visibility = View.GONE
            binding.placeholderText.visibility = View.VISIBLE
            Toast.makeText(this, R.string.no_image_provided, Toast.LENGTH_SHORT).show()
        }
    }
}
