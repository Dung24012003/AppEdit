package com.example.dungappedit.ui.edit.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.dungappedit.R

// Placeholder for a sticker model
data class Sticker(val imageResource: Int)

class StickerAdapter(
    private val stickers: List<Sticker>,
    private val onStickerClick: (Sticker) -> Unit
) : RecyclerView.Adapter<StickerAdapter.StickerViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StickerViewHolder {
        // This layout needs to be created, e.g., res/layout/item_sticker.xml
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sticker, parent, false)
        return StickerViewHolder(view)
    }

    override fun onBindViewHolder(holder: StickerViewHolder, position: Int) {
        val sticker = stickers[position]
        holder.stickerImage.setImageResource(sticker.imageResource)
        holder.itemView.setOnClickListener { onStickerClick(sticker) }
    }

    override fun getItemCount(): Int = stickers.size

    class StickerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val stickerImage: ImageView = view.findViewById(R.id.sticker_image)
    }
} 