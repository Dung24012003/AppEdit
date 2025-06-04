package com.example.dungappedit.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import com.example.dungappedit.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() { // ? bug
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Thêm fragment CameraFragment
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, CameraFragment())
            .commit()
    }
}
