package com.example.dungappedit.ui.camera

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import android.view.WindowInsets
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
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
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.example.dungappedit.R
import com.example.dungappedit.databinding.FragmentCameraBinding
import com.example.dungappedit.ui.camera.filter.CameraFilter
import com.example.dungappedit.ui.camera.filter.CameraFilterManager
import com.example.dungappedit.ui.camera.filter.FilterTabAdapter
import com.example.dungappedit.ui.edit.EditImageActivity
import com.example.dungappedit.utils.PermissionUtils
import com.google.android.material.tabs.TabLayout
import jp.co.cyberagent.android.gpuimage.GPUImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow

class CameraFragment : Fragment(R.layout.fragment_camera) {

    private lateinit var binding: FragmentCameraBinding
    private val viewModel: CameraViewModel by viewModels()
    //private var isTorchOn = false

    private val aspectRatios = listOf("3:4", "9:16", "1:1", "Full")
    private val timerOptions = listOf("0", "3", "5", "10")

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService? = null
    private var imageCapture: ImageCapture? = null

    private var camera: Camera? = null
    //private var lensFacing = CameraSelector.LENS_FACING_BACK

    private var timer: CountDownTimer? = null

    //private var isGridVisible = false
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var isZooming = false

    private lateinit var gpuView: GPUImageView
    private lateinit var imageAnalysis: ImageAnalysis
    private lateinit var filterManager: CameraFilterManager

    private var deviceOrientation = 0
    private lateinit var orientationEventListener: OrientationEventListener

    companion object {
        // Constants can be added here if needed in the future
    }

    // Image picker result launcher
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Start EditImageActivity with the selected image URI
            val intent = Intent(requireContext(), EditImageActivity::class.java)
            intent.putExtra(EditImageActivity.EXTRA_IMAGE_URI, uri)
            startActivity(intent)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            openImagePicker()
        } else {
            // Check if user has permanently denied permission
            val permissionToCheck = PermissionUtils.getStoragePermission()

            if (PermissionUtils.shouldShowSettingsDialog(this, permissionToCheck)) {
                PermissionUtils.showSettingsDialog(
                    this, message = "Storage permission is required to access your images"
                )
            } else {
                Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val requestCameraPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            startCamera()
        } else {
            // Check if user has permanently denied camera permission
            if (PermissionUtils.shouldShowSettingsDialog(this, Manifest.permission.CAMERA)) {
                PermissionUtils.showSettingsDialog(
                    this, message = "Camera permission is required for this feature"
                )
            } else {
                Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentCameraBinding.bind(view)

        // Setup all UI components first
        setupViews()
        setupObservers()
        setupListeners()

        // Check permissions before setting up camera
        if (PermissionUtils.hasCameraPermissions(requireContext())) {
            setupCamera()
            setupOrientationListener()
            // Load most recent photo if we have permission
            loadMostRecentPhoto()
        } else {
            // Permission check should be handled by the SelectionActivity
            // If we somehow get here without permissions, go back to selection screen
            requireActivity().onBackPressedDispatcher.onBackPressed()

        }
    }


    private fun setupOrientationListener() {
        orientationEventListener = object : OrientationEventListener(requireContext()) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                // Chuyển đổi orientation thành một trong 4 hướng chính: 0, 90, 180, 270
                val rotation = when {
                    orientation <= 45 || orientation > 315 -> Surface.ROTATION_0
                    orientation <= 135 -> Surface.ROTATION_90
                    orientation <= 225 -> Surface.ROTATION_180
                    else -> Surface.ROTATION_270
                }

                // Lưu hướng hiện tại của thiết bị
                when (rotation) {
                    Surface.ROTATION_0 -> deviceOrientation = 0
                    Surface.ROTATION_90 -> deviceOrientation = 90
                    Surface.ROTATION_180 -> deviceOrientation = 180
                    Surface.ROTATION_270 -> deviceOrientation = 270
                }

                imageCapture?.targetRotation = rotation
            }
        }

        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable()
        }
    }

    private fun setupViews() {
        // Initialize scale gesture detector with improved handling
        scaleGestureDetector = ScaleGestureDetector(
            requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
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
        setupBrightnessControl()
    }

    private fun setupBrightnessControl() {
        binding.imgBtnBrightness.setOnClickListener {
            viewModel.toggleBrightnessControl()
        }

        // Tăng kích thước vùng chạm của SeekBar để dễ kéo hơn
        binding.brightnessSeekBar.setPadding(0, 30, 0, 30)

        binding.brightnessSeekBar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    viewModel.setBrightnessLevel(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Hiển thị giá trị độ sáng khi bắt đầu kéo
                Log.d("CameraFragment", "Bắt đầu điều chỉnh độ sáng")
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Lưu giá trị độ sáng khi kết thúc kéo
                Log.d("CameraFragment", "Kết thúc điều chỉnh độ sáng: ${seekBar?.progress}")
            }
        })

        // Xử lý sự kiện cho nút reset độ sáng
        binding.btnResetBrightness.setOnClickListener {
            viewModel.resetBrightnessLevel()
            // Hiển thị thông báo nhỏ
            Toast.makeText(
                requireContext(), "Đã đặt lại độ sáng về mức mặc định", Toast.LENGTH_SHORT
            ).show()
        }
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

        viewModel.isBrightnessControlVisible.observe(viewLifecycleOwner) { isVisible ->
            binding.brightnessControlLayout.visibility = if (isVisible) View.VISIBLE else View.GONE
            // Khi hiển thị thanh điều chỉnh độ sáng, cập nhật giá trị hiện tại
            if (isVisible) {
                binding.brightnessSeekBar.progress = viewModel.brightnessLevel.value ?: 50
            }
        }

        viewModel.brightnessLevel.observe(viewLifecycleOwner) { level ->
            binding.brightnessSeekBar.progress = level

            // Áp dụng độ sáng cho camera nếu hỗ trợ
            camera?.cameraControl?.let { cameraControl ->
                try {
                    // Kiểm tra xem camera có hỗ trợ điều chỉnh độ sáng không
                    val exposureState = camera?.cameraInfo?.exposureState
                    if (exposureState?.isExposureCompensationSupported == true) {
                        // Chuyển đổi từ thang đo 0-100 sang phạm vi của camera
                        val minValue = exposureState.exposureCompensationRange.lower
                        val maxValue = exposureState.exposureCompensationRange.upper

                        // Giới hạn phạm vi điều chỉnh để tránh quá sáng hoặc quá tối
                        // Sử dụng 60% phạm vi của camera, tập trung vào khoảng giữa
                        val effectiveRange = (maxValue - minValue) * 0.6f
                        val midPoint = (maxValue + minValue) / 2

                        // Map giá trị từ 0-100 sang phạm vi hiệu quả, với 50 là giá trị trung tâm
                        // Sử dụng hàm phi tuyến để có độ nhạy thấp ở giữa và cao ở hai đầu
                        val normalizedLevel = (level - 50) / 50f // -1.0 đến 1.0

                        // Điều chỉnh đường cong để tối hơn ở phía tối và sáng hơn ở phía sáng
                        val adjustedLevel = if (normalizedLevel < 0) {
                            // Phía tối: tăng cường độ tối bằng cách sử dụng mũ nhỏ hơn
                            -abs(normalizedLevel).toDouble().pow(0.8)
                        } else {
                            // Phía sáng: giữ nguyên đường cong
                            normalizedLevel.toDouble().pow(1.2)
                        }

                        val exposureValue = midPoint + (adjustedLevel * effectiveRange / 2).toInt()

                        // Đảm bảo giá trị nằm trong phạm vi cho phép
                        val clampedValue = exposureValue.toInt().coerceIn(minValue, maxValue)

                        Log.d(
                            "CameraFragment",
                            "Độ sáng: $level, ExposureValue: $clampedValue (phạm vi: $minValue đến $maxValue)"
                        )
                        cameraControl.setExposureCompensationIndex(clampedValue)
                    }
                } catch (e: Exception) {
                    Log.e("CameraFragment", "Không thể điều chỉnh độ sáng: ${e.message}")
                }
            }
        }
    }

    //@SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        // Set touch listener on the root container to handle camera interactions
        // Create a custom touch listener that properly handles performClick
        binding.previewContainer.apply {
            // Set click listener first to ensure performClick is implemented
            setOnClickListener {
                // Handle click events if needed
            }

            // Then set the touch listener
            setOnTouchListener { view, event ->
                viewModel.toggleTimerRatioContainerAndBrightnessControl()

                scaleGestureDetector!!.onTouchEvent(event)
                if (isZooming) return@setOnTouchListener true

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> true
                    MotionEvent.ACTION_UP -> {
                        val factory = binding.viewFinder.meteringPointFactory
                        val point = factory.createPoint(event.x, event.y)
                        val action = FocusMeteringAction.Builder(point)
                            .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                        camera?.cameraControl?.startFocusAndMetering(action)
                        showFocus(event.x, event.y)

                        // Call performClick for accessibility
                        view.performClick()
                        true
                    }

                    else -> false
                }
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
            viewModel.toggleTimerRatioContainer()
        }

        binding.imgBtnBrightness.setOnClickListener {
            viewModel.toggleBrightnessControl()
        }

        binding.imgBtnAlbum.setOnClickListener {
            if (PermissionUtils.hasStoragePermissions(requireContext())) {
                openImagePicker()
            } else {
                PermissionUtils.requestStoragePermissions(this, requestPermissionLauncher)
            }
        }

        // Observe timer and ratio visibility from ViewModel
        viewModel.isTimerRatioContainerVisible.observe(viewLifecycleOwner) { isVisible ->
            if (isVisible) {
                binding.timerRatioContainer.visibility = View.VISIBLE
                binding.timerRatioContainer.startAnimation(
                    AnimationUtils.loadAnimation(requireContext(), R.anim.slide_in_top)
                )
            } else {
                val animation = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_out_top)
                animation.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {}

                    override fun onAnimationEnd(animation: Animation?) {
                        binding.timerRatioContainer.visibility = View.GONE
                    }

                    override fun onAnimationRepeat(animation: Animation?) {}
                })
                binding.timerRatioContainer.startAnimation(animation)
            }
        }

        // Observe brightness control visibility
        viewModel.isBrightnessControlVisible.observe(viewLifecycleOwner) { isVisible ->
            binding.brightnessControlLayout.visibility = if (isVisible) View.VISIBLE else View.GONE
            // Khi hiển thị thanh điều chỉnh độ sáng, cập nhật giá trị hiện tại
            if (isVisible) {
                binding.brightnessSeekBar.progress = viewModel.brightnessLevel.value ?: 50
            }
        }
    }

    private fun setupCamera() {
        if (PermissionUtils.hasCameraPermissions(requireContext())) {
            startCamera()
        } else {
            PermissionUtils.requestCameraPermissions(this, requestCameraPermissionsLauncher)
        }
    }

    //@SuppressLint("ClickableViewAccessibility")
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

            binding.tabFilter.apply {
                // Set click listener first to ensure performClick is implemented
                setOnClickListener {
                    // The TabLayout already handles clicks internally
                }

                // Then set the touch listener
                setOnTouchListener { v, event ->
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

                            // Call performClick for accessibility when the touch is released
                            if (event.action == MotionEvent.ACTION_UP) {
                                v.performClick()
                            }
                        }
                    }
                    false // Don't consume the event
                }
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
        // Xóa tab cũ nếu có
        binding.tabRatio.removeAllTabs()

        // Thêm các tab mới với text căn giữa
        aspectRatios.forEach { ratio ->
            val tab = binding.tabRatio.newTab()
            tab.text = ratio
            binding.tabRatio.addTab(tab)
        }

        binding.tabRatio.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.setAspectRatio(aspectRatios[tab.position])

                // Cập nhật UI cho tab được chọn
                updateTabAppearance(tab, true)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // Cập nhật UI cho tab không được chọn
                tab?.let { updateTabAppearance(it, false) }
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                tab?.let { updateTabAppearance(it, true) }
            }
        })

        // Chọn tab mặc định (3:4)
        val defaultRatioIndex = aspectRatios.indexOf(viewModel.aspectRatio.value)
        if (defaultRatioIndex >= 0) {
            binding.tabRatio.getTabAt(defaultRatioIndex)?.let { tab ->
                tab.select()
                updateTabAppearance(tab, true)
            }
        }
    }

    private fun setupTimerTabs() {
        // Xóa tab cũ nếu có
        binding.tabTimer.removeAllTabs()

        // Thêm các tab mới với text căn giữa và định dạng
        timerOptions.forEach { time ->
            val tab = binding.tabTimer.newTab()
            if (time == "0") {
                tab.text = "Tắt"
            } else {
                tab.text = "${time}s"
            }
            binding.tabTimer.addTab(tab)
        }

        binding.tabTimer.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val seconds = timerOptions[tab.position].toIntOrNull() ?: 0
                viewModel.setTimerSeconds(seconds)

                // Cập nhật UI cho tab được chọn
                updateTabAppearance(tab, true)

                // Không tự động ẩn container sau khi chọn
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // Cập nhật UI cho tab không được chọn
                tab?.let { updateTabAppearance(it, false) }
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                tab?.let { updateTabAppearance(it, true) }
            }
        })

        // Chọn tab mặc định (0s)
        val defaultTimerIndex = timerOptions.indexOf(viewModel.timerSeconds.value.toString())
        if (defaultTimerIndex >= 0) {
            binding.tabTimer.getTabAt(defaultTimerIndex)?.let { tab ->
                tab.select()
                updateTabAppearance(tab, true)
            }
        }
    }

    // Hàm cập nhật giao diện cho tab
    private fun updateTabAppearance(tab: TabLayout.Tab, isSelected: Boolean) {
        try {
            val tabView = tab.view

            // Tìm TextView trong tab
            var textView: TextView? = null
            for (i in 0 until tabView.childCount) {
                val child = tabView.getChildAt(i)
                if (child is TextView) {
                    textView = child
                    break
                }
            }

            textView?.let { tv ->
                // Đặt gravity để text nằm chính giữa
                tv.gravity = android.view.Gravity.CENTER

                // Thiết lập background cho TextView dựa vào trạng thái
                if (isSelected) {
                    tv.setBackgroundResource(R.drawable.tab_selected_background)
                    tv.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                } else {
                    tv.setBackgroundResource(android.R.color.transparent)
                    tv.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                }

                // Đảm bảo padding đúng
                tv.setPadding(16, 8, 16, 8)
            }

            // Hiệu ứng khi chọn tab
            if (isSelected) {
                tabView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).withEndAction {
                    tabView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                }.start()
            }
        } catch (e: Exception) {
            Log.e("CameraFragment", "Lỗi khi cập nhật giao diện tab: ${e.message}")
        }
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

        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requireContext().display.rotation //?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION") requireActivity().windowManager.defaultDisplay.rotation
        }

        val preview = Preview.Builder().setTargetRotation(rotation).build()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(viewModel.lensFacing.value ?: CameraSelector.LENS_FACING_BACK)
            .build()

        imageAnalysis =
            ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(binding.viewFinder.display.rotation).build()

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

        imageCapture = ImageCapture.Builder().setTargetRotation(binding.viewFinder.display.rotation)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalysis, imageCapture
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
            outputOptions, cameraExecutor!!, object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraFragment", "Photo capture failed: ${exc.message}", exc)
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        Toast.makeText(
                            requireContext(),
                            "Chụp ảnh thất bại: ${exc.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)

                    // Xử lý bitmap trước khi áp dụng filter
                    val processedBitmap =
                        if (viewModel.lensFacing.value == CameraSelector.LENS_FACING_FRONT) {
                            // Nếu là camera trước, lật ảnh một lần để khắc phục lỗi lật ngược
                            val matrix = Matrix()
                            matrix.preScale(-1f, 1f)
                            Bitmap.createBitmap(
                                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                            )
                        } else {
                            bitmap
                        }

                    val filteredBitmap = filterManager.processImage(
                        processedBitmap, viewModel.currentFilter.value ?: CameraFilter.ORIGINAL
                    )

                    saveImageToGallery(filteredBitmap)
                }
            })
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
        focusView.animate().scaleX(1.5f).scaleY(1.5f).alpha(0f).setDuration(600)
            .withEndAction { binding.viewFinder.removeView(focusView) }.start()
    }

    private fun updatePreviewSize(ratio: String) {
        val screenWidth: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+: Sử dụng currentWindowMetrics
            val windowMetrics = requireActivity().windowManager.currentWindowMetrics
            val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.navigationBars() or WindowInsets.Type.statusBars()
            )
            val bounds = windowMetrics.bounds
            screenWidth = bounds.width() - insets.left - insets.right
        } else {
            // API < 30: Sử dụng DisplayMetrics
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION") requireActivity().windowManager.defaultDisplay.getMetrics(
                displayMetrics
            )
            screenWidth = displayMetrics.widthPixels
        }

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

            // Xử lý ảnh dựa trên hướng thiết bị và loại camera
            val processedBitmap = when {
                // Nếu đang dùng camera trước, cần xử lý đặc biệt để không bị lật ngược
                viewModel.lensFacing.value == CameraSelector.LENS_FACING_FRONT -> {
                    // Nếu thiết bị đang nằm ngang
                    if (deviceOrientation == 90 || deviceOrientation == 270) {
                        val matrix = Matrix()
                        // Xoay ảnh theo hướng ngang nhưng không lật ngang
                        matrix.postRotate(90f)
                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    } else {
                        // Nếu thiết bị đang đứng, giữ nguyên ảnh (không lật ngang)
                        bitmap
                    }
                }
                // Nếu đang dùng camera sau và thiết bị nằm ngang
                deviceOrientation == 90 || deviceOrientation == 270 -> {
                    val matrix = Matrix()
                    matrix.postRotate(90f)
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                }
                // Các trường hợp còn lại, giữ nguyên ảnh
                else -> bitmap
            }

            val croppedBitmap =
                cropToAspectRatio(processedBitmap, viewModel.aspectRatio.value ?: "3:4")

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val uri = requireContext().contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
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
                    // Update the album button with the newly saved photo
                    loadMostRecentPhoto()
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

    private fun openImagePicker() {
        pickImage.launch("image/*")
    }

    private fun loadMostRecentPhoto() {
        // Check if we have storage permissions first
        if (!PermissionUtils.hasStoragePermissions(requireContext())) {
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Query the MediaStore for the most recent photo
                val projection = arrayOf(
                    MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED
                )

                // Sort by date added, most recent first
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

                val cursor = requireContext().contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val id = it.getLong(idColumn)
                        val imageUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                        )

                        val thumbnail: Bitmap? =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                try {
                                    val thumbnailSize = Size(200, 200)
                                    requireContext().contentResolver.loadThumbnail(
                                        imageUri, thumbnailSize, null
                                    )
                                } catch (e: IOException) {
                                    e.printStackTrace()
                                    null
                                }
                            } else {
                                // Với Android < 29: mở InputStream và tự resize ảnh (cắt ảnh nhỏ)
                                try {
                                    val inputStream =
                                        requireContext().contentResolver.openInputStream(imageUri)
                                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                                    inputStream?.close()
                                    originalBitmap?.let {
                                        Bitmap.createScaledBitmap(it, 200, 200, true)
                                    }
                                } catch (e: IOException) {
                                    e.printStackTrace()
                                    null
                                }
                            }

                        // Cập nhật UI trên main thread
                        withContext(Dispatchers.Main) {
                            if (thumbnail != null) {
                                binding.imgBtnAlbum.setImageBitmap(thumbnail)
                            } else {
                                Toast.makeText(
                                    requireContext(),
                                    "Không thể tải ảnh thu nhỏ",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("CameraFragment", "Error loading most recent photo: ${e.message}")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        restartCamera()
        if (::orientationEventListener.isInitialized) {
            orientationEventListener.enable()
        }
        // Load the most recent photo when returning to the app
        loadMostRecentPhoto()
    }

    override fun onStop() {
        super.onStop()
        if (::orientationEventListener.isInitialized) {
            orientationEventListener.disable()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor?.shutdown()
        timer?.cancel()
        if (::orientationEventListener.isInitialized) {
            orientationEventListener.disable()
        }
    }
}
