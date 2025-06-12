package com.example.dungappedit.ui.edit

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.dungappedit.R
import com.example.dungappedit.common.Constans
import com.example.dungappedit.databinding.ActivityEditBinding
import com.example.dungappedit.ui.edit.fragment.EditHostFragment
import com.google.android.material.tabs.TabLayout


class EditActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditBinding
    private var editHostFragment: EditHostFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val imageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Constans.KEY_DATA_IMG, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Constans.KEY_DATA_IMG)
        }

        if (imageUri != null) {
            editHostFragment = EditHostFragment.newInstance(imageUri)
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, editHostFragment!!)
                .commit()
        } else {
            Toast.makeText(this, "Failed to load image.", Toast.LENGTH_SHORT).show()
            finish()
        }

        setupTabLayout()
    }

    private fun setupTabLayout() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Frame"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Tool"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Filter"))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                editHostFragment?.handleTabSelection(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }
}
