package com.example.dungappedit.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.dungappedit.databinding.ActivityMainBinding
import com.example.dungappedit.ui.camera.CameraFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, CameraFragment())
            .commit()
    }
}
