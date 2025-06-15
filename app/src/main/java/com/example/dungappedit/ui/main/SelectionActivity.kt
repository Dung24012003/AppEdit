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
import com.example.dungappedit.R
import com.example.dungappedit.common.Constans
import com.example.dungappedit.databinding.ActivitySelectionBinding
import com.example.dungappedit.ui.edit.EditActivity

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
            // Re-enable buttons if permission is denied.
            enableButtons()
            if (!shouldShowRequestPermissionRationale(CAMERA_PERMISSION)) {
                showSettingsDialog("Camera")
            } else {
                Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_SHORT).show()
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
            // Re-enable buttons if permission is denied.
            enableButtons()
            val permissionToCheck = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            if (!shouldShowRequestPermissionRationale(permissionToCheck)) {
                showSettingsDialog("Storage")
            } else {
                Toast.makeText(this, R.string.storage_permissions_required, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Flag to track which button triggered the storage permission request.
    private var fromCameraButton = false

    // Image picker
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val intent = Intent(this, EditActivity::class.java)
            intent.putExtra(EditActivity.EXTRA_IMAGE_URI, it)
            startActivity(intent)
        }
        // Buttons are re-enabled in onResume.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cameraButton.setOnClickListener {
            disableButtons()
            fromCameraButton = true
            checkCameraPermission()
        }

        binding.editImageButton.setOnClickListener {
            disableButtons()
            fromCameraButton = false
            checkStoragePermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-enable buttons whenever the user returns to this screen.
        enableButtons()
    }

    private fun disableButtons() {
        binding.cameraButton.isEnabled = false
        binding.editImageButton.isEnabled = false
    }

    private fun enableButtons() {
        binding.cameraButton.isEnabled = true
        binding.editImageButton.isEnabled = true
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            checkStoragePermissionsForCamera()
        } else {
            cameraPermissionLauncher.launch(CAMERA_PERMISSION)
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
            .setTitle(R.string.permission_required_title)
            .setMessage(getString(R.string.permission_required_message, permissionType))
            .setPositiveButton(R.string.go_to_settings) { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                enableButtons()
                dialog.dismiss()
            }
            .setOnCancelListener {
                enableButtons()
            }
            .show()
    }
}
