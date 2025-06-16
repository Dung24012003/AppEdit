package com.example.dungappedit.ui.edit.fragment

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
import com.example.dungappedit.databinding.FragmentEditHostBinding
import com.example.dungappedit.ui.edit.tools.BaseToolManager
import com.example.dungappedit.ui.edit.tools.CropToolManager
import com.example.dungappedit.ui.edit.tools.DrawToolManager
import com.example.dungappedit.ui.edit.tools.FilterToolManager
import com.example.dungappedit.ui.edit.tools.FrameToolManager
import com.example.dungappedit.ui.edit.tools.StickerToolManager
import com.example.dungappedit.ui.edit.tools.TextToolManager
import com.example.dungappedit.ui.edit.utils.ImageLayerController
import com.google.android.material.tabs.TabLayout
import com.yalantis.ucrop.UCrop

class EditHostFragment : Fragment() {

    private var _binding: FragmentEditHostBinding? = null
    private val binding get() = _binding!!

    private lateinit var imageLayerController: ImageLayerController
    private lateinit var frameToolManager: FrameToolManager
    private lateinit var cropToolManager: CropToolManager
    private lateinit var filterToolManager: FilterToolManager
    private lateinit var stickerToolManager: StickerToolManager
    private lateinit var drawToolManager: DrawToolManager
    private lateinit var textToolManager: TextToolManager

    private var activeTool: BaseToolManager? = null

    // Enum to identify which tool is selected from the tools recycler
    private enum class ToolType {
        CROP, STICKER, DRAW, TEXT
    }

    // A simple data class to represent a tool
    private data class Tool(val name: String, val icon: Int, val type: ToolType)

    private val toolsAdapter by lazy {
        ToolsAdapter(
            listOf(
                Tool("Crop", android.R.drawable.ic_menu_crop, ToolType.CROP),
                Tool("Sticker", android.R.drawable.ic_menu_add, ToolType.STICKER),
                Tool("Draw", android.R.drawable.ic_menu_edit, ToolType.DRAW),
                Tool("Text", android.R.drawable.ic_menu_agenda, ToolType.TEXT)
            )
        ) { tool -> onToolSelected(tool.type) }
    }

    companion object {
        private const val ARG_IMAGE_URI = "image_uri"

        fun newInstance(imageUri: Uri): EditHostFragment {
            val fragment = EditHostFragment()
            val args = Bundle()
            args.putParcelable(ARG_IMAGE_URI, imageUri)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentEditHostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageLayerController = ImageLayerController(binding.hostView)

        // Setup ToolManagers with views from the fragment's layout
        binding.frameToolRecycler.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        frameToolManager =
            FrameToolManager(binding.frameToolRecycler, imageLayerController, viewLifecycleOwner.lifecycleScope)

        binding.filterToolRecycler.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        filterToolManager = FilterToolManager(binding.filterToolRecycler, imageLayerController)

        binding.stickerToolRecycler.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        stickerToolManager =
            StickerToolManager(requireContext(), binding.stickerToolRecycler, imageLayerController)

        drawToolManager = DrawToolManager(binding.drawView, binding.drawControls)
        textToolManager = TextToolManager(binding.drawView)

        // Setup tools recycler
        binding.toolsRecycler.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.toolsRecycler.adapter = toolsAdapter

        // Setup listeners for tools
        drawToolManager.setupListeners(
            binding.btnDrawColor,
            binding.seekbarBrushSize,
            binding.btnEraser,
            binding.btnClearDrawing
        )

        cropToolManager = CropToolManager(
            this,
            binding.drawView,
            imageLayerController
        )

        // Default to showing frames
        showFrames()

        val imageUri = arguments?.getParcelable<Uri>(ARG_IMAGE_URI)
        Log.d("ImageURI", imageUri.toString())

        imageUri?.let { uri ->
            binding.drawView.post {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                binding.drawView.setBackgroundBitmap(bitmap)

                // Now that the image is set and the view is laid out, start preloading the frames
                frameToolManager.preloadFrames()
            }
            cropToolManager.setSourceUri(uri)
        }

        // Set listener for when a text item is tapped to be edited
        binding.drawView.setOnTextEditRequestListener { textItem ->
            textToolManager.editText(textItem)
        }
    }

    fun handleTabSelection(position: Int) {
        when (position) {
            0 -> showFrames()
            1 -> showTools()
            2 -> showFilters()
        }
    }

    private fun showFrames() {
        hideAllToolContainers()
        binding.frameToolRecycler.visibility = View.VISIBLE
        setActiveTool(frameToolManager)
    }

    private fun showTools() {
        hideAllToolContainers()
        // Here you would show a list of tools like Crop, Draw, Text, Sticker
        binding.toolsRecycler.visibility = View.VISIBLE
        setActiveTool(null) // No specific tool is active until one is chosen
    }

    private fun showFilters() {
        hideAllToolContainers()
        binding.filterToolRecycler.visibility = View.VISIBLE
        setActiveTool(filterToolManager)
    }

    private fun hideAllToolContainers() {
        binding.frameToolRecycler.visibility = View.GONE
        binding.filterToolRecycler.visibility = View.GONE
        binding.stickerToolRecycler.visibility = View.GONE
        binding.drawControls.visibility = View.GONE
        binding.toolsRecycler.visibility = View.GONE
    }

    private fun onToolSelected(toolType: ToolType) {
        hideAllToolContainers() // Hide everything first

        when (toolType) {
            ToolType.CROP -> {
                binding.toolsRecycler.visibility = View.VISIBLE // Reshow tool list for CROP
                cropToolManager.activate() // This will start the crop intent
                setActiveTool(cropToolManager)
            }

            ToolType.STICKER -> {
                binding.stickerToolRecycler.visibility = View.VISIBLE
                setActiveTool(stickerToolManager)

            }

            ToolType.DRAW -> {
                binding.drawControls.visibility = View.VISIBLE
                setActiveTool(drawToolManager)
            }

            ToolType.TEXT -> {
                setActiveTool(textToolManager)
            }
        }
    }

    private fun setActiveTool(tool: BaseToolManager?) {
        activeTool?.deactivate()
        activeTool = tool
        activeTool?.activate()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            val resultUri = data?.let { UCrop.getOutput(it) }
            if (resultUri != null) {
                cropToolManager.handleCropResult(resultUri)
            } else {
                Toast.makeText(requireContext(), "Failed to retrieve cropped image.", Toast.LENGTH_SHORT).show()
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            val cropError = data?.let { UCrop.getError(it) }
            Toast.makeText(requireContext(), "Crop error: ${cropError?.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        activeTool?.deactivate()
        activeTool = null
    }

    private fun saveBitmapToFile(bitmap: Bitmap) {
        val context = requireContext()
        val timestamp = System.currentTimeMillis()
        val fileName = "IMG_$timestamp.png"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            try {
                resolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(it, contentValues, null, null)
                }

                Toast.makeText(context, "Image saved to gallery", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Inner class for the tools adapter
    private inner class ToolsAdapter(
        private val tools: List<Tool>,
        private val onToolClick: (Tool) -> Unit
    ) : RecyclerView.Adapter<ToolsAdapter.ToolViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_tool, parent, false)
            return ToolViewHolder(view)
        }

        override fun onBindViewHolder(holder: ToolViewHolder, position: Int) {
            holder.bind(tools[position])
        }

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
