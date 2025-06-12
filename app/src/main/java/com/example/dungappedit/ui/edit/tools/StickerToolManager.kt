package com.example.dungappedit.ui.edit.tools

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import com.example.dungappedit.R
import com.example.dungappedit.ui.edit.ui.adapter.Sticker
import com.example.dungappedit.ui.edit.ui.adapter.StickerAdapter
import com.example.dungappedit.ui.edit.utils.ImageLayerController
import android.graphics.BitmapFactory
import android.view.View

class StickerToolManager(
    private val context: Context,
    private val stickerRecyclerView: RecyclerView,
    private val imageLayerController: ImageLayerController
) : BaseToolManager {

    private val stickerAdapter: StickerAdapter

    init {
        val stickers = loadStickers()
        stickerAdapter = StickerAdapter(stickers) { sticker ->
            onStickerSelected(sticker)
        }
        stickerRecyclerView.adapter = stickerAdapter
    }

    override fun activate() {
        stickerRecyclerView.visibility = RecyclerView.VISIBLE
    }

    override fun deactivate() {
        stickerRecyclerView.visibility = RecyclerView.GONE
    }

    override fun isToolActive(): Boolean {
        return stickerRecyclerView.visibility == View.VISIBLE
    }

    override fun applyChanges() {
        // Stickers are added directly.
    }

    private fun onStickerSelected(sticker: Sticker) {
        val bitmap = BitmapFactory.decodeResource(context.resources, sticker.imageResource)
        // The current implementation of ImageLayerController doesn't use StickerView.
        // It uses DrawOnImageView directly.
        // Let's add the sticker to the DrawOnImageView.
        (imageLayerController.hostView.findViewById(R.id.draw_view) as com.example.dungappedit.canvas.DrawOnImageView)
            .addStickerItem(bitmap)
    }

    private fun loadStickers(): List<Sticker> {
        return listOf(
            Sticker(R.drawable.stikcer),
            Sticker(R.drawable.stikcer1),
            Sticker(R.drawable.stikcer3),
            Sticker(R.drawable.stikcer4),
            Sticker(R.drawable.stikcer5),
            Sticker(R.drawable.stikcer6)
        )
    }
} 
