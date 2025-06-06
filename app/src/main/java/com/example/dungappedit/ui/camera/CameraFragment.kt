package com.example.dungappedit.ui.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
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
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.dungappedit.R
import com.example.dungappedit.databinding.FragmentCameraBinding
import com.example.dungappedit.ui.camera.filter.CameraFilter
import com.example.dungappedit.ui.camera.filter.CameraFilterManager
import com.example.dungappedit.ui.camera.filter.FilterTabAdapter
import com.google.android.material.tabs.TabLayout
import jp.co.cyberagent.android.gpuimage.GPUImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ceil

class CameraFragment : Fragment(R.layout.fragment_camera) {

    private lateinit var binding: FragmentCameraBinding
    private val viewModel: CameraViewModel by viewModels()
    private var isTorchOn = false

    private val aspectRatios = listOf("3:4", "9:16", "1:1", "Full")
    private val timerOptions = listOf("0", "3", "5", "10")

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService? = null
    private var imageCapture: ImageCapture? = null

    private var camera: Camera? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    private var timer: CountDownTimer? = null
    private var isGridVisible = false
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var isZooming = false

    private lateinit var gpuView: GPUImageView
    private lateinit var imageAnalysis: ImageAnalysis
    private lateinit var filterManager: CameraFilterManager

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentCameraBinding.bind(view)

        setupViews()
        setupObservers()
        setupListeners()
        setupCamera()
    }

    private fun setupViews() {
        scaleGestureDetector = ScaleGestureDetector(
            requireContext(),
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    isZooming = true
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val currentZoomRatio = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                    val scale = detector.scaleFactor
                    camera?.cameraControl?.setZoomRatio(currentZoomRatio * scale)
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    Handler(Looper.getMainLooper()).postDelayed({ isZooming = false }, 300)
                }
            })

        gpuView = binding.gpuView
        cameraExecutor = Executors.newSingleThreadExecutor()
        filterManager = CameraFilterManager(gpuView)

        setupTabs()
        setupAspectRatioTabs()
        setupTimerTabs()
    }

    private fun setupObservers() {
        viewModel.aspectRatio.observe(viewLifecycleOwner) { ratio ->
            updatePreviewSize(ratio)
            restartCamera()
        }

        viewModel.isGridVisible.observe(viewLifecycleOwner) { isVisible ->
            binding.gridOverlay.visibility = if (isVisible) View.VISIBLE else View.GONE
        }

        viewModel.isFlashEnabled.observe(viewLifecycleOwner) { isEnabled ->
            camera?.cameraControl?.enableTorch(isEnabled)
            binding.imgBtnFlas.setBackgroundResource(
                if (isEnabled) R.drawable.offlas else R.drawable.onflas
            )
        }

        viewModel.currentFilter.observe(viewLifecycleOwner) { filter ->
            filterManager.applyFilter(filter)
        }

        viewModel.lensFacing.observe(viewLifecycleOwner) { facing ->
            restartCamera()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        // Set touch listener on the root container to handle camera interactions
        binding.previewContainer.setOnTouchListener { _, event ->
            scaleGestureDetector!!.onTouchEvent(event)
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
            viewModel.toggleGrid()
        }

        binding.imgBtnFlas.setOnClickListener {
            viewModel.toggleFlash()
        }

        binding.imgBtnRotate.setOnClickListener {
            viewModel.switchCamera()
        }

        binding.imgBtnCapture.setOnClickListener {
            startCaptureWithTimer()
        }

        binding.imgBtnTimerAndRatio.setOnClickListener {
            viewModel.toggleTimerAndRatio()
        }

        // Observe timer and ratio visibility from ViewModel
        viewModel.isTimerAndRatioVisible.observe(viewLifecycleOwner) { isVisible ->
            binding.tabTimer.visibility = if (isVisible) View.VISIBLE else View.GONE
            binding.tabRatio.visibility = if (isVisible) View.VISIBLE else View.GONE
        }
    }

    private fun setupCamera() {
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTabs() {
        val filterTabAdapter = FilterTabAdapter(requireContext())
        filterTabAdapter.setupFilterTabs(binding.tabFilter) { filter ->
            viewModel.setFilter(filter)
        }

        // Căn giữa bộ lọc gốc ban đầu
        binding.tabFilter.post {
            val screenWidth = resources.displayMetrics.widthPixels
            val tabWidth = resources.displayMetrics.density * 80
            val paddingTabsCount = ceil((screenWidth / (2 * tabWidth)).toDouble()).toInt()

            val originalTabPosition = paddingTabsCount
            val originalTab = binding.tabFilter.getTabAt(originalTabPosition)
            originalTab?.select()

            val tabView = originalTab?.view

            if (tabView != null) {
                val tabCenter = tabView.width / 2
                val targetScrollX = tabView.left - (screenWidth / 2) + tabCenter
                binding.tabFilter.scrollTo(targetScrollX, 0)
            }

            // Phát hiện khi bộ lọc được căn giữa
            binding.tabFilter.setOnScrollChangeListener { _, scrollX, _, _, _ ->
                handler.removeCallbacks(detectCenteredRunnable)
                detectCenteredRunnable = Runnable {
                    detectCenteredTab(scrollX)
                }
                handler.postDelayed(detectCenteredRunnable, 50)
            }

            binding.tabFilter.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(flingRunnable)
                        flingRunnable = Runnable {
                            val scrollX = binding.tabFilter.scrollX
                            val closestTab = findClosestTabToCenter(scrollX)
                            closestTab?.let { tab ->
                                val tabView = tab.view
                                val tabCenter = tabView.width / 2
                                val targetScrollX = tabView.left - (screenWidth / 2) + tabCenter
                                binding.tabFilter.scrollTo(targetScrollX, 0)
                                tab.select()
                            }
                        }
                        handler.postDelayed(flingRunnable, 150)
                    }
                }
                false // Không tiêu thụ sự kiện
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var detectCenteredRunnable: Runnable = Runnable {}
    private var flingRunnable: Runnable = Runnable {}

    private fun findClosestTabToCenter(scrollX: Int): TabLayout.Tab? {
        val screenCenter = resources.displayMetrics.widthPixels / 2
        var closestTab: TabLayout.Tab? = null
        var minDistance = Int.MAX_VALUE

        val screenWidth = resources.displayMetrics.widthPixels
        val tabWidth = resources.displayMetrics.density * 80
        val paddingTabsCount = ceil((screenWidth / (2 * tabWidth)).toDouble()).toInt()

        val startIndex = paddingTabsCount
        val endIndex = binding.tabFilter.tabCount - paddingTabsCount

        for (i in startIndex until endIndex) {
            val tab = binding.tabFilter.getTabAt(i) ?: continue
            val tabView = tab.view

            // Tính vị trí trung tâm của tab này
            val tabCenter = tabView.left + tabView.width / 2 - scrollX

            // Tính khoảng cách từ trung tâm màn hình
            val distance = abs(screenCenter - tabCenter)

            // Cập nhật tab gần nhất nếu tab này gần hơn
            if (distance < minDistance) {
                minDistance = distance
                closestTab = tab
            }
        }

        return closestTab
    }

    private fun detectCenteredTab(scrollX: Int) {
        val screenCenter = resources.displayMetrics.widthPixels / 2
        var closestTab: TabLayout.Tab? = null
        var minDistance = Int.MAX_VALUE

        val screenWidth = resources.displayMetrics.widthPixels
        val tabWidth = resources.displayMetrics.density * 80
        val paddingTabsCount = ceil((screenWidth / (2 * tabWidth)).toDouble()).toInt()

        val startIndex = paddingTabsCount
        val endIndex = binding.tabFilter.tabCount - paddingTabsCount

        for (i in startIndex until endIndex) {
            val tab = binding.tabFilter.getTabAt(i) ?: continue
            val tabView = tab.view
            val tabCenter = tabView.left + tabView.width / 2 - scrollX
            val distance = abs(screenCenter - tabCenter)

            if (distance < minDistance) {
                minDistance = distance
                closestTab = tab
            }
        }

        // Chọn tab gần trung tâm nhất nếu nó chưa được chọn
        closestTab?.let { tab ->
            // Sử dụng ngưỡng nhỏ hơn để tự động chọn trong khi cuộn
            // Điều này làm cho việc chọn cảm thấy phản hồi hơn
            val tabView = tab.view
            val threshold = tabView.width * 0.4 // 40% chiều rộng tab để chọn mượt mà hơn
            val tabCenter = tabView.left + tabView.width / 2 - scrollX
            val distance = abs(screenCenter - tabCenter)

            if (distance <= threshold && binding.tabFilter.selectedTabPosition != tab.position) {
                tab.select()
            }
        }
    }

    private fun setupAspectRatioTabs() {
        aspectRatios.forEach { ratio ->
            binding.tabRatio.addTab(binding.tabRatio.newTab().setText(ratio))
        }

        binding.tabRatio.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.setAspectRatio(aspectRatios[tab.position])
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupTimerTabs() {
        timerOptions.forEach { time ->
            binding.tabTimer.addTab(binding.tabTimer.newTab().setText(time))
        }

        binding.tabTimer.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val seconds = timerOptions[tab.position].toIntOrNull() ?: 0
                viewModel.setTimerSeconds(seconds)
                // Hide timer and ratio tabs after selection
                viewModel.toggleTimerAndRatio()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            try {
                cameraProvider?.unbindAll()
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e("CameraFragment", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return

        val preview = Preview.Builder()
            .setTargetRotation(binding.gpuView.display.rotation)
            .build()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(viewModel.lensFacing.value ?: CameraSelector.LENS_FACING_BACK)
            .build()

        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .build()

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(requireContext())) { imageProxy ->
            val bitmap = imageProxy.toBitmap()
            // Tính toán độ xoay chính xác dựa trên hướng thiết bị và hướng camera
            val rotation = when {
                viewModel.lensFacing.value == CameraSelector.LENS_FACING_FRONT -> 270
                else -> 90
            }
            val rotatedBitmap = rotateBitmap(bitmap, rotation.toFloat())

            // Cập nhật GPUImageView với khung hình mới nhất và bộ lọc hiện tại
            binding.viewFinder.visibility = View.GONE
            binding.gpuView.setImage(rotatedBitmap)
            binding.gpuView.filter = viewModel.currentFilter.value?.createFilter()
            imageProxy.close()
        }

        imageCapture = ImageCapture.Builder()
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalysis,
                imageCapture
            )
            preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch (e: Exception) {
            Log.e("CameraFragment", "Use case binding failed", e)
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

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

                    val filteredBitmap = filterManager.processImage(
                        bitmap,
                        viewModel.currentFilter.value ?: CameraFilter.ORIGINAL
                    )

                    saveImageToGallery(filteredBitmap)
                }
            }
        )
    }

    private fun showFocus(x: Float, y: Float) {
        val focusView = View(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(200, 200).apply {
                leftMargin = (x - 100).toInt()
                topMargin = (y - 100).toInt()
            }
            background = ContextCompat.getDrawable(requireContext(), R.drawable.showfocus)
        }
        binding.viewFinder.addView(focusView)
        focusView.animate()
            .scaleX(1.5f).scaleY(1.5f)
            .alpha(0f)
            .setDuration(600)
            .withEndAction { binding.viewFinder.removeView(focusView) }
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

    private fun restartCamera() {
        cameraProvider?.unbindAll()
        binding.viewFinder.visibility = View.VISIBLE
        bindCameraUseCases()
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        // Lật ngang cho camera trước
        if (viewModel.lensFacing.value == CameraSelector.LENS_FACING_FRONT) {
            matrix.postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun saveImageToGallery(bitmap: Bitmap) {
        lifecycleScope.launch(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
            val filename = "IMG_$timestamp.jpg"

            val croppedBitmap =
                cropToAspectRatio(bitmap, viewModel.aspectRatio.value ?: "3:4")

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val uri = requireContext().contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return@launch

            try {
                requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                    croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    requireContext().contentResolver.update(uri, contentValues, null, null)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Ảnh đã lưu: $filename", Toast.LENGTH_SHORT)
                        .show()
                }
            } catch (e: Exception) {
                Log.e("CameraFragment", "Failed to save image", e)
                requireContext().contentResolver.delete(uri, null, null)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Lưu ảnh thất bại", Toast.LENGTH_SHORT).show()
                }
            }
        }
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

    private fun startCaptureWithTimer() {
        val seconds = viewModel.timerSeconds.value ?: 0

        if (seconds == 0) {
            takePhoto()
        } else {
            binding.timerCountDown.visibility = View.VISIBLE
            timer = object : CountDownTimer((seconds * 1000).toLong(), 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    binding.timerCountDown.text = (millisUntilFinished / 1000).toInt().toString()
                }

                override fun onFinish() {
                    binding.timerCountDown.visibility = View.GONE
                    takePhoto()
                }
            }.start()
        }
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
        return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Cần cấp quyền để sử dụng camera",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        restartCamera()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor?.shutdown()
        timer?.cancel()
    }
}
