package com.example.dungappedit.ui.edit.tools

import android.content.Context
import android.graphics.BitmapFactory
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.example.dungappedit.R
import com.example.dungappedit.canvas.DrawOnImageView
import com.example.dungappedit.ui.edit.ui.adapter.Sticker
import com.example.dungappedit.ui.edit.ui.adapter.StickerAdapter

class StickerToolManager(
    private val context: Context,
    private val stickerRecyclerView: RecyclerView,
    private val drawView: DrawOnImageView
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
        // Stickers are added directly to the canvas, so no final apply step is needed.
    }

    fun removeAllStickers() {
        // TODO: This currently clears all movable items (text, stickers).
        // A more specific method in DrawOnImageView is needed to only remove stickers.
        drawView.clearAll()
    }

    private fun onStickerSelected(sticker: Sticker) {
        val bitmap = BitmapFactory.decodeResource(context.resources, sticker.imageResource)
        drawView.addStickerItem(bitmap)
    }

    private fun loadStickers(): List<Sticker> {
        // Ensure that the Sticker model class is correctly defined.
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
