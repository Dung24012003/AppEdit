package com.example.dungappedit.ui.main

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.dungappedit.databinding.ActivitySplashBinding

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Delay 2 seconds then open SelectionActivity
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, SelectionActivity::class.java))
            finish() // close SplashActivity to prevent returning with Back button
        }, 2000) // 2000 ms = 2 seconds
    }
}
