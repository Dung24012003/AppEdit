package com.example.dungappedit.ui

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.dungappedit.R
import com.example.dungappedit.databinding.FragmentCameraBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.core.view.isVisible
import com.google.android.material.tabs.TabLayout

class CameraFragment : Fragment(R.layout.fragment_camera) {

    private lateinit var binding: FragmentCameraBinding
    private val viewModel: CameraViewModel by viewModels()
    private var isTorchOn = false //check on/off flas

    private val aspectRatios = listOf("3:4", "9:16", "1:1", "Full")
    private val timerOptions = listOf("0", "3", "5", "10") // giây đếm ngược

    private var cameraProvider: ProcessCameraProvider? = null //quan ly vong doi camera
    private var cameraExecutor: ExecutorService? = null //luong rieng de chup anh
    private var imageCapture: ImageCapture? = null // ???

    private var camera: Camera? = null //doi tuong camera
    private var lensFacing = CameraSelector.LENS_FACING_BACK //select camera truoc or sau

    private var timer: CountDownTimer? = null //timer

    companion object { //???
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentCameraBinding.bind(view)

        cameraExecutor = Executors.newSingleThreadExecutor() //tao thread de xu ly trong background

        if (allPermissionsGranted()) { //check quyen camera
            startCamera()
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        initUi() // thiet lap giao dien nguoi dung
        initListeners()
    }

    private fun initUi() {
        timerOptions.forEach { time ->
            val tab = binding.tabTimer.newTab().setText(time)
            binding.tabTimer.addTab(tab)
        }

        // Spinner Aspect Ratio
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, aspectRatios)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerAspectRatio.adapter = adapter //hien thi danh sach chon retios

        viewModel.aspectRatio.observe(viewLifecycleOwner) { ratio ->
            updatePreviewSize(ratio) //cap nhat kich thuoc preview
            restartCameraWithRatio(ratio) //khoi dong lai camera voi ratio moi
        }
    }

    private fun initListeners() {
        binding.tabTimer.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                // TODO: Thực hiện xử lý filter tại đây nếu cần

                // Ẩn TabLayout sau khi chọn
                binding.tabTimer.visibility = View.GONE
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })


        binding.imgBtnTimer.setOnClickListener {
            binding.tabTimer.visibility =
                if (binding.tabTimer.isVisible) View.GONE else View.VISIBLE
        }

        binding.spinnerAspectRatio.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    v: View?,
                    position: Int,
                    id: Long
                ) {
                    val ratio = aspectRatios[position]
                    viewModel.setAspectRatio(ratio)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        // Flash toggle
        binding.imgBtnFlas.setOnClickListener {
            isTorchOn = !isTorchOn //bat tat den flas + update icon
            camera?.cameraInfo?.hasFlashUnit()?.let { hasFlash ->
                if (hasFlash) {
                    camera?.cameraControl?.enableTorch(isTorchOn)
                    binding.imgBtnFlas.setBackgroundResource(
                        if (isTorchOn) R.drawable.offlas else R.drawable.onflas                    )
                } else {
                    Toast.makeText(context, "Camera không hỗ trợ đèn flash", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }

        // Rotate camera
        binding.imgBtnRotate.setOnClickListener { //chuyen doi camera
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            restartCameraWithRatio(viewModel.aspectRatio.value ?: "3:4")
        }

        // Capture button click
        binding.imgBtnCapture.setOnClickListener {
            startCaptureWithTimer() //chup anh + dem nguoc
        }
    }

    private fun updatePreviewSize(ratio: String) {
        val metrics = DisplayMetrics()
        requireActivity().windowManager.defaultDisplay.getMetrics(metrics)
        val screenWidth = metrics.widthPixels

        val height = when (ratio) {
            "3:4" -> screenWidth * 4 / 3
            "9:16" -> screenWidth * 16 / 9
            "1:1" -> screenWidth
            "Full" -> requireView().height
            else -> screenWidth * 4 / 3
        }

        val layoutParams = binding.previewContainer.layoutParams
        layoutParams.height = if (ratio == "Full") 0 else height
        binding.previewContainer.layoutParams = layoutParams
    }

    private fun startCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(requireContext()) //lay doi tuong quan ly vong doi
        cameraProviderFuture.addListener({ // khi doi tuong quan ly vong doi san sang thi callback duoc goi
            cameraProvider = cameraProviderFuture.get() //lay ra doi tuong quan ly vong doi thuc te
            bindCameraUseCases(viewModel.aspectRatio.value ?: "3:4")
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun restartCameraWithRatio(ratio: String) {
        cameraProvider?.unbindAll() //huy toan bo use case
        bindCameraUseCases(ratio) //tao lai ratio moi ti le moi
    }

    private fun bindCameraUseCases(ratio: String) {
        val cameraProvider = cameraProvider ?: return

        val preview = Preview.Builder() //khoi tao use case de hien thi preview
            .setTargetAspectRatio(getCameraXAspectRatio(ratio)) //set khung hinh ratio
            .build()
            .also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider) // noi render camera
            }

        imageCapture = ImageCapture.Builder() //tao use case de chup hinh
            .setTargetAspectRatio(getCameraXAspectRatio(ratio)) //set khung hinh ratio
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY) //uu tien toc do chup hon chat luong chup
            .build()

        try {
            cameraProvider.unbindAll() //huy toan bo use case neu khong se bi crack
            camera = cameraProvider.bindToLifecycle( // gan let qua cam bien de co the zoom...
                this,
                CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                preview,
                imageCapture
            )
        } catch (exc: Exception) {
            Log.e("CameraFragment", "Use case binding failed", exc)
        }
    }

    private fun getCameraXAspectRatio(ratio: String): Int {
        return when (ratio) {
            "3:4" -> AspectRatio.RATIO_4_3
            "9:16" -> AspectRatio.RATIO_16_9
            "1:1" -> AspectRatio.RATIO_4_3 // fallback
            "Full" -> AspectRatio.RATIO_16_9
            else -> AspectRatio.RATIO_4_3
        }
    }

    private fun startCaptureWithTimer() {
        val selectedTab = binding.tabTimer.getTabAt(binding.tabTimer.selectedTabPosition)
        val selectedTimer = selectedTab?.text?.toString()?.toIntOrNull() ?: 0

        if (selectedTimer <= 0) {
            takePhoto()
            return
        }

        binding.timerCountDown.visibility = View.VISIBLE
        timer?.cancel()

        timer = object : CountDownTimer(selectedTimer * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                binding.timerCountDown.text = (millisUntilFinished / 1000).toInt().toString()
            }

            override fun onFinish() {
                binding.timerCountDown.visibility = View.GONE
                takePhoto()
            }
        }.start()
    }


    //    private fun takePhoto() {
//        val imageCapture = imageCapture ?: return
//
//        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
//        val contentValues = ContentValues().apply {
//            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
//            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-App")
//            }
//        }
//
//        val outputOptions = ImageCapture.OutputFileOptions
//            .Builder(
//                requireContext().contentResolver,
//                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
//                contentValues
//            )
//            .build()
//
//        imageCapture.takePicture(
//            outputOptions,
//            cameraExecutor!!,
//            object : ImageCapture.OnImageSavedCallback {
//                override fun onError(exc: ImageCaptureException) {
//                    Log.e("CameraFragment", "Photo capture failed: ${exc.message}", exc)
//                    lifecycleScope.launchWhenResumed {
//                        Toast.makeText(
//                            requireContext(),
//                            "Chụp ảnh thất bại: ${exc.message}",
//                            Toast.LENGTH_SHORT
//                        ).show()
//                    }
//                }
//
//                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
//                    val msg = "Ảnh đã lưu: ${outputFileResults.savedUri}"
//                    Log.d("CameraFragment", msg)
//                    lifecycleScope.launchWhenResumed {
//                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
//                    }
//                }
//            })
//    }
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        // Tạo file tạm trong cache
        val photoFile = File(requireContext().cacheDir, "temp_${System.currentTimeMillis()}.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor!!,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraFragment", "Photo capture failed: ${exc.message}", exc)
                    lifecycleScope.launchWhenResumed {
                        Toast.makeText(
                            requireContext(),
                            "Chụp ảnh thất bại: ${exc.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    val croppedBitmap =
                        cropToAspectRatio(bitmap, viewModel.aspectRatio.value ?: "3:4")

                    // Lưu ảnh đã crop vào MediaStore
                    val name = SimpleDateFormat(
                        "yyyyMMdd_HHmmss",
                        Locale.US
                    ).format(System.currentTimeMillis())
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-App")
                        }
                    }

                    val uri = requireContext().contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                    )

                    uri?.let {
                        requireContext().contentResolver.openOutputStream(it).use { outputStream ->
                            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream!!)
                        }

                        lifecycleScope.launchWhenResumed {
                            Toast.makeText(requireContext(), "Ảnh đã lưu: $uri", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            }
        )
    }

    private fun cropToAspectRatio(bitmap: Bitmap, ratio: String): Bitmap {
        val srcWidth = bitmap.width
        val srcHeight = bitmap.height

        val (targetW, targetH) = when (ratio) {
            "1:1" -> Pair(1, 1)
            "3:4" -> Pair(3, 4)
            "9:16" -> Pair(9, 16)
            "Full" -> Pair(9, 19) // không crop, giữ nguyên
            else -> Pair(4, 3) // fallback
        }

        val srcRatio = srcWidth.toFloat() / srcHeight
        val targetRatio = targetW.toFloat() / targetH

        return if (srcRatio > targetRatio) {
            // ảnh gốc quá rộng, cần cắt chiều ngang
            val newWidth = (srcHeight * targetRatio).toInt()
            val xOffset = (srcWidth - newWidth) / 2
            Bitmap.createBitmap(bitmap, xOffset, 0, newWidth, srcHeight)
        } else {
            // ảnh gốc quá cao, cần cắt chiều dọc
            val newHeight = (srcWidth / targetRatio).toInt()
            val yOffset = (srcHeight - newHeight) / 2
            Bitmap.createBitmap(bitmap, 0, yOffset, srcWidth, newHeight)
        }
    }


//    private fun cropToSquare(bitmap: Bitmap): Bitmap {
//        val size = min(bitmap.width, bitmap.height)
//        val xOffset = (bitmap.width - size) / 2
//        val yOffset = (bitmap.height - size) / 2
//        return Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)
//    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor?.shutdown()
        timer?.cancel()
    }
}
