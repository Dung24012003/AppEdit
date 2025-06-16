package com.example.dungappedit.ui.main

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentValues
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.dungappedit.R
import com.example.dungappedit.databinding.ActivitySelectionBinding
import com.example.dungappedit.ui.edit.EditActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SelectionActivity : AppCompatActivity(), ImagePreviewDialogFragment.OnImagePreviewListener {
    private lateinit var binding: ActivitySelectionBinding
    private lateinit var galleryAdapter: GalleryAdapter
    private var pendingDeleteUri: Uri? = null

    // --- Permissions ---
    private val storagePermissions by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    // --- Activity Result Launchers ---
    private var isRequestingForCamera = false
    private var newImageUri: Uri? = null

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            if (isRequestingForCamera) openCamera() else openImagePicker()
        } else {
            enableButtons()
            val permissionToCheck = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (!shouldShowRequestPermissionRationale(permissionToCheck)) {
                showSettingsDialog("Storage")
            } else {
                Toast.makeText(this, R.string.storage_permissions_required, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { openImageInEditor(it) }
        enableButtons()
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            newImageUri?.let { openImageInEditor(it) }
        } else {
            Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show()
        }
        enableButtons()
    }

    private val deleteRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingDeleteUri?.let { performDelete(it) }
        } else {
            Toast.makeText(this, getString(R.string.failed_to_delete_image), Toast.LENGTH_SHORT)
                .show()
        }
        pendingDeleteUri = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupClickListeners()
        checkPermissionsAndLoadImages()
    }

    private fun setupClickListeners() {
        binding.cameraButton.setOnClickListener {
            disableButtons()
            isRequestingForCamera = true
            if (hasStoragePermissions()) openCamera() else requestPermissionsLauncher.launch(
                storagePermissions
            )
        }

        binding.editImageButton.setOnClickListener {
            disableButtons()
            isRequestingForCamera = false
            if (hasStoragePermissions()) openImagePicker() else requestPermissionsLauncher.launch(
                storagePermissions
            )
        }
    }

    private fun setupRecyclerView() {
        galleryAdapter = GalleryAdapter(emptyList()) { uri ->
            showImagePreview(uri)
        }
        binding.galleryRecyclerView.layoutManager = GridLayoutManager(this, 3)
        binding.galleryRecyclerView.adapter = galleryAdapter
    }

    private fun checkPermissionsAndLoadImages() {
        if (hasStoragePermissions()) {
            loadAllImages()
        } else {
            requestPermissionsLauncher.launch(storagePermissions)
        }
    }

    private fun loadAllImages() {
        binding.progressBar.visibility = View.VISIBLE
        binding.galleryRecyclerView.visibility = View.GONE

        lifecycleScope.launch {
            val imageUris = queryAllImages()
            galleryAdapter.updateData(imageUris)

            binding.progressBar.visibility = View.GONE
            binding.galleryRecyclerView.visibility = View.VISIBLE
            binding.galleryTitle.visibility =
                if (imageUris.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private suspend fun queryAllImages(): List<Uri> = withContext(Dispatchers.IO) {
        val imageUris = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                imageUris.add(
                    Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                )
            }
        }
        imageUris
    }

    private fun showImagePreview(uri: Uri) {
        ImagePreviewDialogFragment.newInstance(uri).show(supportFragmentManager, "image_preview")
    }

    override fun onEdit(uri: Uri) {
        openImageInEditor(uri)
    }

    override fun onDelete(uri: Uri) {
        performDelete(uri)
    }

    private fun performDelete(uri: Uri) {
        try {
            contentResolver.delete(uri, null, null)
            Toast.makeText(this, R.string.image_deleted_successfully, Toast.LENGTH_SHORT).show()
            loadAllImages()
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                pendingDeleteUri = uri
                val intentSenderRequest =
                    IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                deleteRequestLauncher.launch(intentSenderRequest)
            } else {
                Toast.makeText(this, R.string.failed_to_delete_image, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.failed_to_delete_image, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onShare(uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "image/jpeg"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, resources.getText(R.string.share)))
    }

    private fun openImageInEditor(uri: Uri) {
        val intent = Intent(this, EditActivity::class.java).apply {
            putExtra(EditActivity.EXTRA_IMAGE_URI, uri.toString())
        }
        startActivity(intent)
    }

    private fun openImagePicker() {
        pickImageLauncher.launch("image/*")
    }

    private fun openCamera() {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/DungAppEdit")
            }
        }
        newImageUri =
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        newImageUri?.let { takePictureLauncher.launch(it) }
    }

    private fun hasStoragePermissions(): Boolean {
        return storagePermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
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

    private fun showSettingsDialog(permissionType: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_required_title)
            .setMessage(getString(R.string.permission_required_message, permissionType))
            .setPositiveButton(R.string.go_to_settings) { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
