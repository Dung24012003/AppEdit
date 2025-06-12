package com.example.dungappedit.ui.album

import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.dungappedit.databinding.AlbumActivityBinding
import java.io.File

class AlbumActivity : AppCompatActivity() {
    private lateinit var binding: AlbumActivityBinding
    private lateinit var adapter: AdapterImage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AlbumActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            //setDisplayShowTitleEnabled(false) // hide title
        }

        initData()
        initListeners()

    }

    private fun initData() {
        // get list Drawings
        val drawingsDir =
            File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Drawings")
        val imageFiles = if (drawingsDir.exists() && drawingsDir.isDirectory) {
            drawingsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".png") } ?: emptyList()
        } else {
            emptyList()
        }
        setupRecyclerView(imageFiles)
    }

    private fun setupRecyclerView(imageFiles: List<File>) {
        adapter = AdapterImage(imageFiles)
        binding.rvImages.layoutManager =
            GridLayoutManager(this, 3, GridLayoutManager.VERTICAL, false)
        binding.rvImages.adapter = adapter
    }

    private fun initListeners() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
}
