package com.example.dungappedit.ui.camera

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.dungappedit.R
import com.example.dungappedit.common.Constans
import com.example.dungappedit.databinding.FragmentCameraBinding
import com.example.dungappedit.ui.camera.filter.CameraFilter
import com.example.dungappedit.ui.camera.filter.CameraFilterManager
import com.example.dungappedit.ui.camera.filter.FilterTabAdapter
import com.example.dungappedit.ui.camera.stikcer.Sticker
import com.example.dungappedit.ui.camera.stikcer.StickerTabAdapter
import com.example.dungappedit.ui.edit.EditActivity
import com.example.dungappedit.utils.PermissionUtils
import com.google.android.material.tabs.TabLayout
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CameraViewModel by viewModels()

    private lateinit var faceDetector: FaceDetector
    private lateinit var stickerTabAdapter: StickerTabAdapter
    private var isStickerMode = false

    private var lastDetectedFace: Face? = null
    private var lastPreviewWidth: Int = -1
    private var lastPreviewHeight: Int = -1

    // Flag to prevent multiple captures while processing
    private var isCapturing = false

    // CameraX Components
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService
    private var currentRotation: Int = -1

    // UI and Interaction Components
    private lateinit var filterManager: CameraFilterManager
    private var timer: CountDownTimer? = null
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private lateinit var orientationEventListener: OrientationEventListener
    private val handler = Handler(Looper.getMainLooper())

    // Activity Result Launchers
    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                startActivity(Intent(requireContext(), EditActivity::class.java).apply {
                    putExtra(Constans.KEY_DATA_IMG, it)
                })
            }
        }
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.entries.all { it.value }) openImagePicker() else showToast(
                "Storage permission is required"
            )
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.viewFinder.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

        cameraExecutor = Executors.newSingleThreadExecutor()
        filterManager = CameraFilterManager(binding.gpuView)

        setupViews()
        setupListeners()
        setupObservers()
        setupOrientationListener()

        initFaceDetector()

        viewModel.aspectRatio.value?.let { updatePreviewContainerLayout(it) }

        if (!PermissionUtils.hasCameraPermissions(requireContext())) {
            showToast("Camera permission is required")
        } else {
            loadMostRecentPhoto()
        }
    }

    private fun initFaceDetector() {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .enableTracking()
            .build()
        faceDetector = FaceDetection.getClient(options)
    }

    //region --- SETUP METHODS ---

    private fun setupViews() {
        setupTabs()
        setupAspectRatioTabs()
        setupTimerTabs()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        binding.imgBtnCapture.setOnClickListener { startCaptureWithTimer() }
        binding.imgBtnRotate.setOnClickListener { viewModel.switchCamera() }
        binding.imgBtnFlas.setOnClickListener { viewModel.toggleFlash() }
        binding.imgBtnGrit.setOnClickListener { viewModel.toggleGrid() }
        binding.imgBtnBrightness.setOnClickListener { viewModel.toggleBrightnessControl() }
        binding.imgBtnTimerAndRatio.setOnClickListener { viewModel.toggleTimerRatioContainer() }

        binding.imgBtnAlbum.setOnClickListener {
            if (PermissionUtils.hasStoragePermissions(requireContext())) openImagePicker()
            else PermissionUtils.requestStoragePermissions(this, requestPermissionLauncher)
        }

        binding.brightnessSeekBar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) viewModel.setBrightnessLevel(p)
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        binding.btnResetBrightness.setOnClickListener {
            viewModel.resetBrightnessLevel()
            showToast("Brightness has been reset")
        }

        scaleGestureDetector = ScaleGestureDetector(
            requireContext(),
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val currentZoomRatio = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                    camera?.cameraControl?.setZoomRatio(currentZoomRatio * detector.scaleFactor)
                    return true
                }
            })

        binding.previewContainer.setOnTouchListener { view, event ->
            viewModel.toggleTimerRatioContainerAndBrightnessControl()
            scaleGestureDetector?.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) {
                val factory = binding.viewFinder.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action =
                    FocusMeteringAction.Builder(point).setAutoCancelDuration(3, TimeUnit.SECONDS)
                        .build()
                camera?.cameraControl?.startFocusAndMetering(action)
                showFocus(event.x, event.y)
                view.performClick()
            }
            true
        }

        binding.imgBtnFace.setOnClickListener {
            isStickerMode = !isStickerMode

            if (isStickerMode) {
                binding.tabFilter.visibility = View.GONE
                binding.tabSticker.visibility = View.VISIBLE
                viewModel.setFilter(CameraFilter.ORIGINAL)
            } else {
                binding.tabFilter.visibility = View.VISIBLE
                binding.tabSticker.visibility = View.GONE
                viewModel.setSelectedSticker(Sticker.NONE)
            }
        }
    }

    private fun setupObservers() {
        viewModel.lensFacing.observe(viewLifecycleOwner) { bindCameraUseCases() }
        viewModel.aspectRatio.observe(viewLifecycleOwner) { ratio ->
            updatePreviewContainerLayout(ratio)
            bindCameraUseCases()
        }
        viewModel.isFlashEnabled.observe(viewLifecycleOwner) { isEnabled ->
            camera?.cameraControl?.enableTorch(isEnabled)
            binding.imgBtnFlas.setImageResource(if (isEnabled) R.drawable.onflas else R.drawable.offlas)
        }
        viewModel.currentFilter.observe(viewLifecycleOwner) { filter ->
            filter?.let {
                binding.gpuView.filter = it.createFilter()
                if (it == CameraFilter.ORIGINAL) {
                    binding.gpuView.visibility = View.GONE
                } else {
                    binding.gpuView.visibility = View.VISIBLE
                }
            }
        }
        viewModel.isGridVisible.observe(viewLifecycleOwner) { isVisible ->
            binding.gridOverlay.visibility = if (isVisible) View.VISIBLE else View.GONE
        }
        viewModel.isTimerRatioContainerVisible.observe(viewLifecycleOwner) { isVisible ->
            binding.timerRatioContainer.visibility = if (isVisible) View.VISIBLE else View.GONE
        }
        viewModel.isBrightnessControlVisible.observe(viewLifecycleOwner) { isVisible ->
            binding.brightnessControlLayout.visibility = if (isVisible) View.VISIBLE else View.GONE
        }
        viewModel.brightnessLevel.observe(viewLifecycleOwner) { level ->
            binding.brightnessSeekBar.progress = level
            camera?.cameraInfo?.exposureState?.let {
                if (it.isExposureCompensationSupported) {
                    val range = it.exposureCompensationRange
                    val value = range.lower + (range.upper - range.lower) * (level / 100.0f)
                    camera?.cameraControl?.setExposureCompensationIndex(value.toInt())
                }
            }
        }
        viewModel.selectedSticker.observe(viewLifecycleOwner) { sticker ->
            binding.faceStickerOverlay.selectedSticker = sticker
        }
    }

    private fun updatePreviewContainerLayout(ratioString: String) {
        val viewFinder = binding.viewFinder
        val gpuView = binding.gpuView
        val gridOverlay = binding.gridOverlay
        val faceStickerOverlay = binding.faceStickerOverlay
        val previewContainer = binding.previewContainer

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val isSideways =
            resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        val (width, height) = when (ratioString) {
            "1:1" -> {
                val size = min(screenWidth, screenHeight); Pair(size, size)
            }

            "3:4" -> if (isSideways) {
                if (screenWidth * 3 <= screenHeight * 4) Pair(screenWidth, screenWidth * 3 / 4)
                else Pair(screenHeight * 4 / 3, screenHeight)
            } else {
                if (screenWidth * 4 <= screenHeight * 3) Pair(screenWidth, screenWidth * 4 / 3)
                else Pair(screenHeight * 3 / 4, screenHeight)
            }

            "9:16" -> if (isSideways) {
                if (screenWidth * 9 <= screenHeight * 16) Pair(screenWidth, screenWidth * 9 / 16)
                else Pair(screenHeight * 16 / 9, screenHeight)
            } else {
                if (screenWidth * 16 <= screenHeight * 9) Pair(screenWidth, screenWidth * 16 / 9)
                else Pair(screenHeight * 9 / 16, screenHeight)
            }

            else -> Pair(screenWidth, screenHeight) // Full
        }

        val viewsToUpdate = listOf(viewFinder, gpuView, gridOverlay, faceStickerOverlay)
        for (view in viewsToUpdate) {
            val params = view.layoutParams as FrameLayout.LayoutParams
            params.width = width
            params.height = height
            params.gravity = android.view.Gravity.CENTER
            view.layoutParams = params
            view.requestLayout()
        }
        previewContainer.requestLayout()
    }

    private fun setupOrientationListener() {
        orientationEventListener = object : OrientationEventListener(requireContext()) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                val newDisplayRotation =
                    if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) Surface.ROTATION_90 else Surface.ROTATION_0
                if (newDisplayRotation != currentRotation) {
                    currentRotation = newDisplayRotation
                    viewModel.aspectRatio.value?.let { updatePreviewContainerLayout(it) }
                    bindCameraUseCases()
                }

                val imageCaptureRotation = when {
                    orientation in 45..134 -> Surface.ROTATION_270
                    orientation in 135..224 -> Surface.ROTATION_180
                    orientation in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                imageCapture?.targetRotation = imageCaptureRotation
            }
        }
        if (orientationEventListener.canDetectOrientation()) orientationEventListener.enable()
    }
    //endregion

    //region --- CAMERA CORE (HYBRID VERSION) ---

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                viewModel.aspectRatio.value?.let { updatePreviewContainerLayout(it) }
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e("CameraFragment", "Could not start camera", e)
                showToast("Could not start camera")
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {
        val cameraProvider = this.cameraProvider ?: return
        val currentOrientation = resources.configuration.orientation
        val rotation =
            if (currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) Surface.ROTATION_90 else Surface.ROTATION_0

        cameraProvider.unbindAll()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(viewModel.lensFacing.value ?: CameraSelector.LENS_FACING_BACK)
            .build()

        val isSideways =
            currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val aspectRatioString = viewModel.aspectRatio.value ?: "3:4"
        val finalRational = when (aspectRatioString) {
            "Full" -> {
                val metrics = resources.displayMetrics; if (isSideways) Rational(
                    metrics.heightPixels,
                    metrics.widthPixels
                )
                else Rational(metrics.widthPixels, metrics.heightPixels)
            }

            "1:1" -> Rational(1, 1)
            else -> {
                val parts = aspectRatioString.split(":").map { it.toInt() }
                if (isSideways) Rational(parts[1], parts[0]) else Rational(parts[0], parts[1])
            }
        }
        val viewPort = ViewPort.Builder(finalRational, rotation).build()

        val preview = Preview.Builder().setTargetRotation(rotation).build()
        imageCapture = ImageCapture.Builder().setTargetRotation(rotation).build()

        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, this::processImageFrame)
            }

        val useCaseGroup = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(imageCapture!!)
            .addUseCase(imageAnalysis!!)
            .setViewPort(viewPort)
            .build()

        try {
            preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            camera =
                cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, useCaseGroup)
            viewModel.isFlashEnabled.value?.let { camera?.cameraControl?.enableTorch(it) }
        } catch (exc: Exception) {
            Log.e("CameraFragment", "Use case binding failed", exc)
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageFrame(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        val imageWidth = if (rotationDegrees == 90 || rotationDegrees == 270) {
            imageProxy.height
        } else {
            imageProxy.width
        }
        val imageHeight = if (rotationDegrees == 90 || rotationDegrees == 270) {
            imageProxy.width
        } else {
            imageProxy.height
        }

        if (binding.gpuView.visibility == View.VISIBLE) {
            val bitmap = imageProxy.toBitmap()
            val rotatedBitmap = rotateBitmapForPreview(bitmap, rotationDegrees.toFloat())
            activity?.runOnUiThread {
                if (isAdded && _binding != null && binding.gpuView.visibility == View.VISIBLE) {
                    binding.gpuView.setImage(rotatedBitmap)
                }
            }
        }

        val isFrontCamera = viewModel.lensFacing.value == CameraSelector.LENS_FACING_FRONT

        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                this.lastDetectedFace = faces.firstOrNull()
                this.lastPreviewWidth = imageWidth
                this.lastPreviewHeight = imageHeight

                activity?.runOnUiThread {
                    _binding?.let {
                        it.faceStickerOverlay.setSourceInfo(imageWidth, imageHeight, isFrontCamera)
                        it.faceStickerOverlay.updateFaces(faces)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("CameraFragment", "Face detection failed", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
    //endregion

    //region --- UI AND CAPTURE LOGIC ---
    private fun rotateBitmapForPreview(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        if (viewModel.lensFacing.value == CameraSelector.LENS_FACING_FRONT) {
            matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun startCaptureWithTimer() {
        // Prevent multiple captures while processing
        if (isCapturing) {
            return
        }

        // Set capturing flag to true
        isCapturing = true

        // Disable the capture button visually
        binding.imgBtnCapture.alpha = 0.5f

        val seconds = viewModel.timerSeconds.value ?: 0
        if (seconds > 0) {
            binding.timerCountDown.visibility = View.VISIBLE
            timer = object : CountDownTimer(seconds * 1000L, 1000) {
                @SuppressLint("SetTextI18n")
                override fun onTick(millisUntilFinished: Long) {
                    binding.timerCountDown.text = "${millisUntilFinished / 1000 + 1}"
                }

                override fun onFinish() {
                    binding.timerCountDown.visibility = View.GONE
                    captureAndSaveImage()
                }
            }.start()
        } else {
            captureAndSaveImage()
        }
    }

    private fun captureAndSaveImage() {
        val imageCapture = this.imageCapture ?: return
        imageCapture.flashMode =
            if (viewModel.isFlashEnabled.value == true) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            requireContext().contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()
        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    showToast("Failed to save image: ${exc.message}")
                    // Reset capturing flag and button appearance on error
                    isCapturing = false
                    activity?.runOnUiThread {
                        binding.imgBtnCapture.alpha = 1.0f
                    }
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri ?: return

                    // Process the image with filter and sticker
                    lifecycleScope.launch {
                        try {
                            // Apply filter and sticker to the saved image
                            applyFilterAndStickerToSavedImage(savedUri)

                            // Navigate to EditActivity with the saved image
                            withContext(Dispatchers.Main) {
                                if (isAdded) {
                                    startActivity(
                                        Intent(
                                            requireContext(),
                                            EditActivity::class.java
                                        ).apply {
                                            putExtra(Constans.KEY_DATA_IMG, savedUri)
                                        })
                                }
                            }
                        } finally {
                            // Reset capturing flag and button appearance
                            isCapturing = false
                            withContext(Dispatchers.Main) {
                                binding.imgBtnCapture.alpha = 1.0f
                            }
                        }
                    }
                }
            })
    }

    private suspend fun applyFilterAndStickerToSavedImage(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Get original bitmap
                val originalBitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(
                            requireContext().contentResolver,
                            uri
                        )
                    ) { d, _, _ ->
                        d.isMutableRequired = true; d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
                        .copy(Bitmap.Config.ARGB_8888, true)
                }

                // 2. Flip for front camera
                val isFrontCamera = viewModel.lensFacing.value == CameraSelector.LENS_FACING_FRONT
                val processedBitmap = if (isFrontCamera) {
                    val matrix = Matrix().apply { preScale(-1f, 1f) }
                    Bitmap.createBitmap(
                        originalBitmap,
                        0,
                        0,
                        originalBitmap.width,
                        originalBitmap.height,
                        matrix,
                        false
                    )
                } else {
                    originalBitmap
                }

                // 3. Apply color filter
                val currentFilter = viewModel.currentFilter.value ?: CameraFilter.ORIGINAL
                val filteredBitmap = filterManager.processImage(processedBitmap, currentFilter)
                    .copy(Bitmap.Config.ARGB_8888, true)

                // 4. Draw sticker
                val faceToDraw = lastDetectedFace
                val stickerToDraw = viewModel.selectedSticker.value
                val previewWidth = lastPreviewWidth
                val previewHeight = lastPreviewHeight

                if (faceToDraw != null && stickerToDraw != null && stickerToDraw != Sticker.NONE && previewWidth > 0 && previewHeight > 0) {
                    val canvas = Canvas(filteredBitmap)
                    binding.faceStickerOverlay.setSourceInfo(
                        previewWidth,
                        previewHeight,
                        isFrontCamera
                    )
                    binding.faceStickerOverlay.drawStickerOnCanvas(canvas, faceToDraw)
                }

                // 5. Overwrite the final image
                requireContext().contentResolver.openOutputStream(uri)?.use {
                    filteredBitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
                }

            } catch (e: Exception) {
                Log.e("CameraFragment", "Failed to apply filter and thumbnail", e)
            }
        }
    }

    private fun setupTabs() {
        val filterTabAdapter = FilterTabAdapter(requireContext())
        filterTabAdapter.setupFilterTabs(binding.tabFilter) { filter ->
            viewModel.setFilter(filter)
        }

        stickerTabAdapter = StickerTabAdapter(requireContext()) { sticker ->
            viewModel.setSelectedSticker(sticker)
        }
        stickerTabAdapter.setupStickerTabs(binding.tabSticker, viewModel.stickerOptions)

        binding.root.post {
            val screenWidth = resources.displayMetrics.widthPixels
            val tabWidth = resources.displayMetrics.density * 80
            val paddingTabsCount = ceil((screenWidth / (2 * tabWidth)).toDouble()).toInt()
            val initialFilterIndex = paddingTabsCount
            val initialStickerIndex = paddingTabsCount

            setupCenteringTabLayout(
                tabLayout = binding.tabFilter,
                initialSelectionIndex = initialFilterIndex,
            )

            setupCenteringTabLayout(
                tabLayout = binding.tabSticker,
                initialSelectionIndex = initialStickerIndex,
            )
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCenteringTabLayout(
        tabLayout: TabLayout,
        initialSelectionIndex: Int
    ) {
        var localDetectCenteredRunnable: Runnable? = null
        var localFlingRunnable: Runnable? = null

        val screenWidth = resources.displayMetrics.widthPixels
        val initialTab = tabLayout.getTabAt(initialSelectionIndex)
        initialTab?.select()

        val tabView = initialTab?.view ?: return
        tabLayout.post {
            val tabCenter = tabView.width / 2
            val targetScrollX = tabView.left - (screenWidth / 2) + tabCenter
            tabLayout.scrollTo(targetScrollX, 0)
        }

        tabLayout.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            localDetectCenteredRunnable?.let { handler.removeCallbacks(it) }
            localDetectCenteredRunnable = Runnable { detectCenteredTab(tabLayout, scrollX) }
            handler.postDelayed(localDetectCenteredRunnable!!, 50)
        }

        tabLayout.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                localFlingRunnable?.let { handler.removeCallbacks(it) }
                localFlingRunnable = Runnable {
                    findClosestTabToCenter(tabLayout, tabLayout.scrollX)?.let { closestTab ->
                        val cTabView = closestTab.view
                        val cTabCenter = cTabView.width / 2
                        val cTargetScrollX = cTabView.left - (screenWidth / 2) + cTabCenter
                        tabLayout.smoothScrollTo(cTargetScrollX, 0)
                        if (!closestTab.isSelected) {
                            closestTab.select()
                        }
                    }
                }
                handler.postDelayed(localFlingRunnable!!, 100)
            }
            false
        }
    }

    private fun findClosestTabToCenter(tabLayout: TabLayout, scrollX: Int): TabLayout.Tab? {
        val screenCenter = resources.displayMetrics.widthPixels / 2
        var closestTab: TabLayout.Tab? = null
        var minDistance = Int.MAX_VALUE
        for (i in 0 until tabLayout.tabCount) {
            val tab = tabLayout.getTabAt(i) ?: continue
            val tabView = tab.view
            val tabCenterOnScreen = tabView.left + tabView.width / 2 - scrollX
            val distance = abs(screenCenter - tabCenterOnScreen)
            if (distance < minDistance) {
                minDistance = distance
                closestTab = tab
            }
        }
        return closestTab
    }

    private fun detectCenteredTab(tabLayout: TabLayout, scrollX: Int) {
        val closestTab = findClosestTabToCenter(tabLayout, scrollX)
        if (closestTab != null && !closestTab.isSelected) {
            closestTab.select()
        }
    }

    private fun setupAspectRatioTabs() {
        binding.tabRatio.removeAllTabs()
        viewModel.aspectRatios.forEach { ratio ->
            binding.tabRatio.addTab(binding.tabRatio.newTab().setText(ratio))
        }
        
        binding.tabRatio.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.setAspectRatio(viewModel.aspectRatios[tab.position])
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        val defaultIndex = viewModel.aspectRatios.indexOf(viewModel.aspectRatio.value)
        if (defaultIndex != -1) binding.tabRatio.getTabAt(defaultIndex)?.select()
    }

    private fun setupTimerTabs() {
        binding.tabTimer.removeAllTabs()
        viewModel.timerOptions.forEach { time ->
            binding.tabTimer.addTab(
                binding.tabTimer.newTab().setText(if (time == 0) "Tắt" else "${time}s")
            )
        }
        
        binding.tabTimer.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.setTimerSeconds(viewModel.timerOptions[tab.position])
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        val defaultIndex = viewModel.timerOptions.indexOf(viewModel.timerSeconds.value)
        if (defaultIndex != -1) binding.tabTimer.getTabAt(defaultIndex)?.select()
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
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, this.width, this.height), 90, out)
        return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
    }

    private fun showToast(message: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            if (isAdded) Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openImagePicker() {
        pickImage.launch("image/*")
    }

    private fun showFocus(x: Float, y: Float) {
        val focusView = View(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(120, 120)
                .apply { leftMargin = (x - 60).toInt(); topMargin = (y - 60).toInt() }
            background = ContextCompat.getDrawable(requireContext(), R.drawable.showfocus)
        }
        binding.previewContainer.addView(focusView)
        focusView.animate().alpha(0f)
            .setStartDelay(500).setDuration(300)
            .withEndAction { binding.previewContainer.removeView(focusView) }.start()
    }

    private fun loadMostRecentPhoto() {
        if (!PermissionUtils.hasStoragePermissions(requireContext())) return
        lifecycleScope.launch(Dispatchers.IO) {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            try {
                requireContext().contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                        val uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        val thumb =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) requireContext().contentResolver.loadThumbnail(
                                uri,
                                Size(200, 200),
                                null
                            )
                            else @Suppress("DEPRECATION") MediaStore.Images.Thumbnails.getThumbnail(
                                requireContext().contentResolver,
                                id,
                                MediaStore.Images.Thumbnails.MINI_KIND,
                                null
                            )
                        withContext(Dispatchers.Main) { binding.imgBtnAlbum.setImageBitmap(thumb) }
                    }
                }
            } catch (e: Exception) {
                Log.e("CameraFragment", "Error loading photo", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (PermissionUtils.hasCameraPermissions(requireContext())) {
            viewModel.aspectRatio.value?.let { updatePreviewContainerLayout(it) }
            handler.post { startCamera() }
        }
        if (::orientationEventListener.isInitialized) {
            orientationEventListener.enable()
        }
    }

    override fun onPause() {
        super.onPause()
        cameraProvider?.unbindAll()
        if (::orientationEventListener.isInitialized) {
            orientationEventListener.disable()
        }

        timer?.cancel()
        timer = null

        if (isCapturing) {
            isCapturing = false
            binding.imgBtnCapture.alpha = 1.0f
        }

        loadMostRecentPhoto()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        timer?.cancel()
        handler.removeCallbacksAndMessages(null)
        faceDetector.close()
        _binding = null
    }
}
