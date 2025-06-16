package com.example.dungappedit.ui.main

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.example.dungappedit.R
import com.example.dungappedit.common.Constans
import com.example.dungappedit.databinding.ActivitySelectionBinding
import com.example.dungappedit.ui.edit.EditActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SelectionActivity : AppCompatActivity(), ImagePreviewDialogFragment.OnImagePreviewListener {
    private lateinit var binding: ActivitySelectionBinding
    private lateinit var galleryAdapter: GalleryAdapter
    private var pendingDeleteUri: Uri? = null

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

    private val permissionsToRequest by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            STORAGE_PERMISSIONS_API_33_AND_ABOVE
        } else {
            STORAGE_PERMISSIONS_BELOW_API_33
        }
    }

    // Permission request launchers
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkStoragePermissionsForCamera()
        } else {
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

    private val initialStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            loadAllImages()
        } else {
            Toast.makeText(this, R.string.storage_permissions_required_for_gallery, Toast.LENGTH_LONG).show()
        }
    }

    private val deleteRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            pendingDeleteUri?.let { uri ->
                try {
                    contentResolver.delete(uri, null, null)
                    Toast.makeText(this, R.string.image_deleted_successfully, Toast.LENGTH_SHORT).show()
                    loadAllImages()
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.failed_to_delete_image), Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, getString(R.string.failed_to_delete_image), Toast.LENGTH_SHORT).show()
        }
        pendingDeleteUri = null
    }

    private var fromCameraButton = false

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            openImageInEditor(it)
        }
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

        setupRecyclerView()
        checkPermissionsAndLoadImages()
    }

    override fun onResume() {
        super.onResume()
        enableButtons()
        if (hasStoragePermissions()) {
            loadAllImages()
        }
    }

    private fun checkPermissionsAndLoadImages() {
        if (hasStoragePermissions()) {
            loadAllImages()
        } else {
            initialStoragePermissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun disableButtons() {
        binding.cameraButton.isEnabled = false
        binding.editImageButton.isEnabled = false
    }

    private fun enableButtons() {
        binding.cameraButton.isEnabled = true
        binding.editImageButton.isEnabled = true
    }

    private fun setupRecyclerView() {
        binding.galleryRecyclerView.layoutManager = GridLayoutManager(this, 3)
    }

    private fun loadAllImages() {
        binding.galleryTitle.visibility = View.VISIBLE
        binding.galleryRecyclerView.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val imageUris = queryAllImages()
            withContext(Dispatchers.Main) {
                galleryAdapter = GalleryAdapter(imageUris) { uri ->
                    showImagePreview(uri)
                }
                binding.galleryRecyclerView.adapter = galleryAdapter
            }
        }
    }

    private fun queryAllImages(): List<Uri> {
        val imageUris = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                imageUris.add(contentUri)
            }
        }
        return imageUris
    }

    private fun showImagePreview(uri: Uri) {
        val previewFragment = ImagePreviewDialogFragment.newInstance(uri)
        previewFragment.show(supportFragmentManager, "image_preview")
    }

    override fun onEdit(uri: Uri) {
        openImageInEditor(uri)
    }

    override fun onDelete(uri: Uri) {
        try {
            contentResolver.delete(uri, null, null)
            Toast.makeText(this, R.string.image_deleted_successfully, Toast.LENGTH_SHORT).show()
            loadAllImages()
        } catch (e: SecurityException) {
            val recoverableSecurityException = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                e as? RecoverableSecurityException
            } else {
                null
            }

            if (recoverableSecurityException != null) {
                pendingDeleteUri = uri
                val intentSenderRequest =
                    IntentSenderRequest.Builder(recoverableSecurityException.userAction.actionIntent.intentSender).build()
                deleteRequestLauncher.launch(intentSenderRequest)
            } else {
                Toast.makeText(this, R.string.failed_to_delete_image, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.failed_to_delete_image, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onShare(uri: Uri) {
        val shareIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "image/jpeg"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, resources.getText(R.string.share)))
    }

    private fun openImageInEditor(uri: Uri) {
        val intent = Intent(this, EditActivity::class.java)
        intent.putExtra(EditActivity.EXTRA_IMAGE_URI, uri)
        startActivity(intent)
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
            storagePermissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun checkStoragePermissions() {
        if (hasStoragePermissions()) {
            openImagePicker()
        } else {
            storagePermissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun hasStoragePermissions(): Boolean {
        return permissionsToRequest.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermissions() {
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
