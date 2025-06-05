package com.example.dungappedit.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.dungappedit.R
import com.example.dungappedit.databinding.FragmentCameraBinding
import com.google.android.material.tabs.TabLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

    private var isGridVisible = false

    private var scaleGestureDetector: ScaleGestureDetector? = null

    private var isZooming = false

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
        scaleGestureDetector = ScaleGestureDetector(requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isZooming = true  // Bắt đầu zoom
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoomRatio = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                val scale = detector.scaleFactor
                camera?.cameraControl?.setZoomRatio(currentZoomRatio * scale)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                // Kết thúc zoom, delay 1 chút rồi cho phép lấy nét lại
                Handler(Looper.getMainLooper()).postDelayed({
                    isZooming = false
                }, 300)
            }
        })

        timerOptions.forEach { time ->
            val tab = binding.tabTimer.newTab().setText(time)
            binding.tabTimer.addTab(tab)
        }
        aspectRatios.forEach { ratio ->
            val tab = binding.tabRatio.newTab().setText(ratio)
            binding.tabRatio.addTab(tab)
        }

        viewModel.aspectRatio.observe(viewLifecycleOwner) { ratio ->
            updatePreviewSize(ratio) //cap nhat kich thuoc preview
            restartCameraWithRatio(ratio) //khoi dong lai camera voi ratio moi
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initListeners() {
        binding.viewFinder.setOnTouchListener { _, event ->
            scaleGestureDetector!!.onTouchEvent(event)

            // Nếu đang zoom, bỏ qua lấy nét
            if (isZooming) return@setOnTouchListener true

            when (event.action) {
                MotionEvent.ACTION_DOWN -> true
                MotionEvent.ACTION_UP -> {
                    val factory = binding.viewFinder.meteringPointFactory
                    val point = factory.createPoint(event.x, event.y)
                    val action = FocusMeteringAction.Builder(point)
                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                        .build()
                    camera?.cameraControl?.startFocusAndMetering(action)
                    showFocus(event.x, event.y)
                    true
                }
                else -> false
            }
        }

        binding.imgBtnGrit.setOnClickListener {
            isGridVisible = !isGridVisible
            binding.gridOverlay.visibility = if (isGridVisible) View.VISIBLE else View.GONE
        }

        binding.tabRatio.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val position = tab.position
                viewModel.setAspectRatio(aspectRatios[position])
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        binding.root.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                // Kiểm tra nếu không chạm vào tabTimer hoặc tabRatio thì ẩn chúng
                val locationTimer = IntArray(2)
                val locationRatio = IntArray(2)
                binding.tabTimer.getLocationOnScreen(locationTimer)
                binding.tabRatio.getLocationOnScreen(locationRatio)

                val x = event.rawX.toInt()
                val y = event.rawY.toInt()

                val inTabTimer =
                    x in locationTimer[0]..(locationTimer[0] + binding.tabTimer.width) &&
                            y in locationTimer[1]..(locationTimer[1] + binding.tabTimer.height)

                val inTabRatio =
                    x in locationRatio[0]..(locationRatio[0] + binding.tabRatio.width) &&
                            y in locationRatio[1]..(locationRatio[1] + binding.tabRatio.height)

                if (!inTabTimer && !inTabRatio) {
                    binding.tabTimer.visibility = View.GONE
                    binding.tabRatio.visibility = View.GONE
                }
            }
            false
        }


        binding.imgBtnTimer.setOnClickListener {
            binding.tabTimer.visibility =
                if (binding.tabTimer.isVisible) View.GONE else View.VISIBLE

            binding.tabRatio.visibility =
                if (binding.tabRatio.isVisible) View.GONE else View.VISIBLE
        }

        // Flash toggle
        binding.imgBtnFlas.setOnClickListener {
            isTorchOn = !isTorchOn //bat tat den flas + update icon
            camera?.cameraInfo?.hasFlashUnit()?.let { hasFlash ->
                if (hasFlash) {
                    camera?.cameraControl?.enableTorch(isTorchOn)
                    binding.imgBtnFlas.setBackgroundResource(
                        if (isTorchOn) R.drawable.offlas else R.drawable.onflas
                    )
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
            binding.tabTimer.visibility = View.GONE

            binding.tabRatio.visibility = View.GONE
            startCaptureWithTimer() //chup anh + dem nguoc
        }
    }

    private fun showFocus(x: Float, y: Float) {
        val focusView = View(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(200, 200).apply {
                leftMargin = (x - 100).toInt()
                topMargin = (y - 100).toInt()
                Log.d("checkxy", "${x.toInt()} ${y.toInt()}")
            }
            background = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.showfocus
            )
        }
        binding.viewFinder.addView(focusView)
        focusView.animate()
            .scaleX(1.5f).scaleY(1.5f)
            .alpha(0f)
            .setDuration(600)
            .withEndAction { binding.root.removeView(focusView) }
            .start()
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
