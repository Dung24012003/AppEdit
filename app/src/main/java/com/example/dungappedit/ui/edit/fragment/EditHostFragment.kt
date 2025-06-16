package com.example.dungappedit.ui.edit.fragment

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dungappedit.R
import com.example.dungappedit.canvas.DrawOnImageView
import com.example.dungappedit.databinding.FragmentEditHostBinding
import com.example.dungappedit.ui.edit.tools.BaseToolManager
import com.example.dungappedit.ui.edit.tools.CropToolManager
import com.example.dungappedit.ui.edit.tools.DrawToolManager
import com.example.dungappedit.ui.edit.tools.FilterToolManager
import com.example.dungappedit.ui.edit.tools.FrameToolManager
import com.example.dungappedit.ui.edit.tools.HueToolManager
import com.example.dungappedit.ui.edit.tools.StickerToolManager
import com.example.dungappedit.ui.edit.tools.TextToolManager
import com.example.dungappedit.ui.edit.utils.ImageLayerController
import com.example.dungappedit.ui.edit.utils.ImageOrientationUtil
import com.yalantis.ucrop.UCrop

class EditHostFragment : Fragment(), DrawOnImageView.OnImageDimensionsChangedListener {

    private var _binding: FragmentEditHostBinding? = null
    private val binding get() = _binding!!

    // Managers
    private lateinit var imageLayerController: ImageLayerController
    private lateinit var frameToolManager: FrameToolManager
    private lateinit var cropToolManager: CropToolManager
    private lateinit var filterToolManager: FilterToolManager
    private lateinit var stickerToolManager: StickerToolManager
    private lateinit var drawToolManager: DrawToolManager
    private lateinit var textToolManager: TextToolManager
    private lateinit var hueToolManager: HueToolManager

    private var activeTool: BaseToolManager? = null

    // Tool Menu
    private enum class ToolType { CROP, STICKER, DRAW, TEXT, HUE }
    private data class Tool(val name: String, val icon: Int, val type: ToolType)
    private val toolsAdapter by lazy {
        ToolsAdapter(listOf(
            Tool("Crop", R.drawable.crop, ToolType.CROP),
            Tool("Sticker", R.drawable.addstikcer, ToolType.STICKER),
            Tool("Draw", R.drawable.pen, ToolType.DRAW),
            Tool("Text", R.drawable.addtext, ToolType.TEXT),
            Tool("Hue", R.drawable.hue_icon, ToolType.HUE)
        )) { tool -> onToolSelected(tool.type) }
    }

    // State Machine
    private enum class State {
        NONE, FRAME, TOOL_MENU, TOOL_STICKER, TOOL_DRAW, FILTER, TOOL_HUE
    }
    private var currentState: State = State.NONE

    companion object {
        private const val ARG_IMAGE_URI = "image_uri"
        fun newInstance(imageUri: Uri): EditHostFragment {
            return EditHostFragment().apply {
                arguments = Bundle().apply { putParcelable(ARG_IMAGE_URI, imageUri) }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditHostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupManagersAndListeners()
        loadImageFromArgs()
        setState(State.FRAME)
    }

    private fun setupManagersAndListeners() {
        imageLayerController = ImageLayerController(binding.hostView)
        binding.drawView.setOnImageDimensionsChangedListener(this)

        frameToolManager = FrameToolManager(binding.frameToolRecycler, imageLayerController, viewLifecycleOwner.lifecycleScope).also {
            binding.frameToolRecycler.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        }

        // Initialize both managers
        hueToolManager = HueToolManager(binding.hueSaturationControls, binding.seekbarHue, binding.seekbarSaturation, binding.drawView)
        filterToolManager = FilterToolManager(binding.filterToolRecycler, binding.drawView).also {
            binding.filterToolRecycler.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        }

        // Connect the managers bidirectionally
        filterToolManager.setHueToolManager(hueToolManager)
        hueToolManager.setFilterToolManager(filterToolManager)

        stickerToolManager = StickerToolManager(requireContext(), binding.stickerToolRecycler, binding.drawView).also {
            binding.stickerToolRecycler.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        }
        drawToolManager = DrawToolManager(binding.drawView, binding.drawControls).apply {
            setupListeners(binding.btnDrawColor, binding.seekbarBrushSize, binding.btnEraser, binding.btnClearDrawing)
        }
        textToolManager = TextToolManager(binding.drawView).also {
            binding.drawView.setOnTextEditRequestListener(it::editText)
        }
        cropToolManager = CropToolManager(this, binding.drawView, filterToolManager)

        binding.toolsRecycler.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.toolsRecycler.adapter = toolsAdapter
    }

    private fun loadImageFromArgs() {
        val imageUri = arguments?.getParcelable<Uri>(ARG_IMAGE_URI)
        imageUri?.let { uri ->
            binding.drawView.post {
                try {
                    // Use our utility to load the bitmap with correct orientation
                    val bitmap = ImageOrientationUtil.loadBitmapWithCorrectOrientation(
                        requireContext().contentResolver, uri
                    )

                    if (bitmap != null) {
                        binding.drawView.setBackgroundBitmap(bitmap)
                        frameToolManager.preloadFrames()
                    } else {
                        // Fallback to old method if our utility fails
                        Log.e("EditHostFragment", "Failed to load image with orientation correction, trying fallback")
                        val inputStream = requireContext().contentResolver.openInputStream(uri)
                        val fallbackBitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream?.close()
                        binding.drawView.setBackgroundBitmap(fallbackBitmap)
                        frameToolManager.preloadFrames()
                    }
                } catch (e: Exception) {
                    Log.e("EditHostFragment", "Error loading image: ${e.message}")
                    Toast.makeText(requireContext(), "Error loading image", Toast.LENGTH_SHORT).show()
                }
            }
            cropToolManager.setSourceUri(uri)
        }
    }

    override fun onImageDimensionsChanged(left: Float, top: Float, right: Float, bottom: Float, width: Int, height: Int) {
        imageLayerController.updateFrameBounds()
    }

    // Handle events from Activity
    fun onTabSelected(position: Int) {
        val requestedState = stateFromPosition(position)
        if (stateFromPosition(position) != currentState) {
            setState(requestedState)
        }
    }

    fun onTabUnselected(position: Int) {
        if (currentState != State.NONE) {
            setState(State.NONE)
        }
    }

    fun onTabReselected(position: Int) {
        val requestedState = stateFromPosition(position)

        when {
            (currentState == State.TOOL_STICKER || currentState == State.TOOL_DRAW) && requestedState == State.TOOL_MENU -> {
                setState(State.TOOL_MENU)
            }
            currentState == requestedState -> {
                setState(State.NONE)
            }
            else -> {
                setState(requestedState)
            }
        }
    }

    private fun onToolSelected(toolType: ToolType) {
        when (toolType) {
            ToolType.CROP -> {
                cropToolManager.activate()
            }
            ToolType.TEXT -> {
                textToolManager.activate()
            }
            ToolType.STICKER -> setState(State.TOOL_STICKER)
            ToolType.DRAW -> setState(State.TOOL_DRAW)
            ToolType.HUE -> setState(State.TOOL_HUE)
        }
    }

    private fun setState(newState: State) {
        // Deactivate current tool
        activeTool?.deactivate()
        activeTool = null

        hideAllContainers()

        currentState = newState

        when (currentState) {
            State.NONE -> { /* All containers are hidden */ }
            State.FRAME -> {
                binding.frameToolRecycler.visibility = View.VISIBLE
                activeTool = frameToolManager
            }
            State.TOOL_MENU -> {
                binding.toolsRecycler.visibility = View.VISIBLE
            }
            State.TOOL_STICKER -> {
                binding.stickerToolRecycler.visibility = View.VISIBLE
                activeTool = stickerToolManager
            }
            State.TOOL_DRAW -> {
                binding.drawControls.visibility = View.VISIBLE
                activeTool = drawToolManager
            }
            State.FILTER -> {
                binding.filterToolRecycler.visibility = View.VISIBLE
                activeTool = filterToolManager
            }
            State.TOOL_HUE -> {
                binding.hueSaturationControls.visibility = View.VISIBLE
                activeTool = hueToolManager
            }
        }

        // Activate the new tool
        activeTool?.activate()

        // Note: We don't need special handling here anymore since both
        // FilterToolManager and HueToolManager now have references to each other
        // and will preserve each other's effects when activated
    }

    private fun hideAllContainers() {
        binding.frameToolRecycler.visibility = View.GONE
        binding.filterToolRecycler.visibility = View.GONE
        binding.stickerToolRecycler.visibility = View.GONE
        binding.drawControls.visibility = View.GONE
        binding.toolsRecycler.visibility = View.GONE
        binding.hueSaturationControls.visibility = View.GONE
    }

    private fun stateFromPosition(position: Int): State {
        return when (position) {
            0 -> State.FRAME
            1 -> State.TOOL_MENU
            2 -> State.FILTER
            else -> State.NONE
        }
    }

    // Handle events from Activity
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == UCrop.REQUEST_CROP) {
            // We need to reset the CropToolManager state whether the crop was successful, failed, or canceled
            if (resultCode == Activity.RESULT_OK && data != null) {
                val resultUri = UCrop.getOutput(data)
                if (resultUri != null) {
                    cropToolManager.handleCropResult(resultUri)
                    // After cropping, we can return to the main tool menu
                    setState(State.TOOL_MENU)
                } else {
                    Toast.makeText(requireContext(), "Failed to get crop result", Toast.LENGTH_SHORT).show()
                    cropToolManager.deactivate() // Reset state on error
                }
            } else if (resultCode == UCrop.RESULT_ERROR) {
                val cropError = data?.let { UCrop.getError(it) }
                Toast.makeText(requireContext(), "Crop error: ${cropError?.message}", Toast.LENGTH_SHORT).show()
                cropToolManager.deactivate() // Reset state on error
            } else {
                // User pressed "X" or the Back button to cancel
                cropToolManager.deactivate() // Important: Reset state on user cancellation
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activeTool?.deactivate()
        _binding = null
    }

    fun captureEdits(): Bitmap? {
        binding.drawView.clearSelection()
        val originalBitmap = binding.drawView.getOriginalBitmap() ?: return null
        val resultBitmap = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Get filter matrix if any
        val activeFilter = filterToolManager.getActiveFilter()
        val filterMatrix = activeFilter?.let { if (it.name != "None") it.matrix else null }

        // Combine filter with hue/saturation adjustments if any
        val combinedMatrix = hueToolManager.combineWithFilterMatrix(filterMatrix)

        // Apply the combined color filter
        if (combinedMatrix != null) {
            paint.colorFilter = ColorMatrixColorFilter(combinedMatrix)
        }

        // Draw the bitmap with the combined effects
        canvas.drawBitmap(originalBitmap, 0f, 0f, paint)

        val matrix = Matrix()
        val srcRect = RectF(binding.drawView.imageRectLeft, binding.drawView.imageRectTop, binding.drawView.imageRectRight, binding.drawView.imageRectBottom)
        val dstRect = RectF(0f, 0f, originalBitmap.width.toFloat(), originalBitmap.height.toFloat())
        if (srcRect.width() > 0 && srcRect.height() > 0) {
            matrix.setRectToRect(srcRect, dstRect, Matrix.ScaleToFit.CENTER)
        }

        binding.drawView.drawLayers(canvas, matrix)

        val activeFrame = frameToolManager.getActiveFrameBitmap()
        if (activeFrame != null) {
            val scaledFrame = Bitmap.createScaledBitmap(activeFrame, originalBitmap.width, originalBitmap.height, true)
            canvas.drawBitmap(scaledFrame, 0f, 0f, null)
            scaledFrame.recycle()
        }

        // The orientation is already corrected when loading the image,
        // so the resultBitmap will have the correct orientation
        return resultBitmap
    }

    fun resetToOriginal() {
        drawToolManager.clearDrawing()
        stickerToolManager.removeAllStickers()
        frameToolManager.removeFrame()
        filterToolManager.applyOriginalFilter()
        hueToolManager.resetHueAndSaturation()
    }

    fun updateImage(bitmap: Bitmap?) {
        bitmap?.let {
            resetToOriginal()
            binding.drawView.setBackgroundBitmap(it)
        }
    }

    private inner class ToolsAdapter(
        private val tools: List<Tool>,
        private val onToolClick: (Tool) -> Unit
    ) : RecyclerView.Adapter<ToolsAdapter.ToolViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tool, parent, false)
            return ToolViewHolder(view)
        }
        override fun onBindViewHolder(holder: ToolViewHolder, position: Int) = holder.bind(tools[position])
        override fun getItemCount() = tools.size
        inner class ToolViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val icon: ImageView = itemView.findViewById(R.id.tool_icon)
            private val name: TextView = itemView.findViewById(R.id.tool_name)
            fun bind(tool: Tool) {
                icon.setImageResource(tool.icon)
                name.text = tool.name
                itemView.setOnClickListener { onToolClick(tool) }
            }
        }
    }
}
