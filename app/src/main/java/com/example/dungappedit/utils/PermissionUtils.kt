package com.example.dungappedit.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlin.collections.all

/**
 * Utility class for handling permissions in the app
 */
object PermissionUtils {
    
    // Camera and storage permission constants
    val CAMERA_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_MEDIA_IMAGES
        )
    } else {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
    
    val STORAGE_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    
    /**
     * Check if all required permissions are granted
     */
    fun hasAllPermissions(context: Context, permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Check if camera permissions are granted
     */
    fun hasCameraPermissions(context: Context): Boolean {
        return hasAllPermissions(context, CAMERA_PERMISSIONS)
    }
    
    /**
     * Check if storage permissions are granted
     */
    fun hasStoragePermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Show settings dialog when permission is permanently denied
     */
    fun showSettingsDialog(fragment: Fragment, title: String = "Permission Required", 
                          message: String = "Permission is required for this feature. Please enable it in app settings.") {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Go to Settings") { _, _ ->
                // Open app settings
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", fragment.requireContext().packageName, null)
                intent.data = uri
                fragment.startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Check if user has permanently denied a permission and should be shown a settings dialog
     */
    fun shouldShowSettingsDialog(fragment: Fragment, permission: String): Boolean {
        return !fragment.shouldShowRequestPermissionRationale(permission)
    }
    
    /**
     * Request camera permissions and handle the result
     */
    fun requestCameraPermissions(
        fragment: Fragment,
        permissionLauncher: ActivityResultLauncher<Array<String>>
    ) {
        if (!hasCameraPermissions(fragment.requireContext())) {
            permissionLauncher.launch(CAMERA_PERMISSIONS)
        }
    }
    
    /**
     * Request storage permissions and handle the result
     */
    fun requestStoragePermissions(
        fragment: Fragment,
        permissionLauncher: ActivityResultLauncher<Array<String>>
    ) {
        if (!hasStoragePermissions(fragment.requireContext())) {
            permissionLauncher.launch(STORAGE_PERMISSIONS)
        }
    }
    
    /**
     * Get the appropriate storage permission based on Android version
     */
    fun getStoragePermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
} 
