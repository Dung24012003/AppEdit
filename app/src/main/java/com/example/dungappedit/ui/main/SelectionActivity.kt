package com.example.dungappedit.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.dungappedit.databinding.ActivitySelectionBinding
import com.example.dungappedit.ui.edit.EditImageActivity

class SelectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySelectionBinding
    
    // Permission constants
    private val CAMERA_PERMISSION = Manifest.permission.CAMERA
    private val STORAGE_PERMISSIONS_BELOW_API_33 = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    private val STORAGE_PERMISSIONS_API_33_AND_ABOVE = arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO
    )
    
    // Permission request launchers
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkStoragePermissionsForCamera()
        } else {
            if (!shouldShowRequestPermissionRationale(CAMERA_PERMISSION)) {
                showSettingsDialog("Camera")
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            if (fromCameraButton) {
                openCameraActivity()
            } else {
                openImagePicker()
            }
        } else {
            // Check if we should show the settings dialog
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_IMAGES)) {
                    showSettingsDialog("Storage")
                } else {
                    Toast.makeText(this, "Storage permissions are required", Toast.LENGTH_SHORT).show()
                }
            } else {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    showSettingsDialog("Storage")
                } else {
                    Toast.makeText(this, "Storage permissions are required", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // Flag to track which button triggered the storage permission request
    private var fromCameraButton = false
    
    // Image picker
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Start EditImageActivity with the selected image URI
            val intent = Intent(this, EditImageActivity::class.java)
            intent.putExtra(EditImageActivity.EXTRA_IMAGE_URI, uri)
            startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up click listeners for the buttons
        binding.cameraButton.setOnClickListener {
            fromCameraButton = true
            checkCameraPermission()
        }

        binding.editImageButton.setOnClickListener {
            fromCameraButton = false
            checkStoragePermissions()
        }
    }
    
    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION) == PackageManager.PERMISSION_GRANTED -> {
                // Camera permission already granted, now check storage
                checkStoragePermissionsForCamera()
            }
            else -> {
                // Request camera permission
                cameraPermissionLauncher.launch(CAMERA_PERMISSION)
            }
        }
    }
    
    private fun checkStoragePermissionsForCamera() {
        if (hasStoragePermissions()) {
            openCameraActivity()
        } else {
            requestStoragePermissions()
        }
    }
    
    private fun checkStoragePermissions() {
        if (hasStoragePermissions()) {
            openImagePicker()
        } else {
            requestStoragePermissions()
        }
    }
    
    private fun hasStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            STORAGE_PERMISSIONS_API_33_AND_ABOVE.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
        } else {
            STORAGE_PERMISSIONS_BELOW_API_33.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
        }
    }
    
    private fun requestStoragePermissions() {
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            STORAGE_PERMISSIONS_API_33_AND_ABOVE
        } else {
            STORAGE_PERMISSIONS_BELOW_API_33
        }
        storagePermissionLauncher.launch(permissionsToRequest)
    }
    
    private fun openCameraActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("OPEN_CAMERA", true)
        startActivity(intent)
    }
    
    private fun openImagePicker() {
        pickImage.launch("image/*")
    }
    
    private fun showSettingsDialog(permissionType: String) {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("$permissionType permission is required for this feature. Please enable it in app settings.")
            .setPositiveButton("Go to Settings") { _, _ ->
                // Open app settings
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
} 