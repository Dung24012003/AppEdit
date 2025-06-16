package com.example.dungappedit.ui.main

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.dungappedit.R

class GalleryAdapter(
    private var imageUris: List<Uri>, // Cho phép danh sách có thể thay đổi
    private val onImageClick: (Uri) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val imageView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery_image, parent, false) as ImageView
        return ViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val uri = imageUris[position]
        holder.bind(uri)
    }

    override fun getItemCount(): Int = imageUris.size

    /**
     * Cập nhật danh sách ảnh và thông báo cho RecyclerView để vẽ lại.
     */
    fun updateData(newUris: List<Uri>) {
        this.imageUris = newUris
        notifyDataSetChanged() // Đơn giản nhất, có thể thay bằng DiffUtil để tối ưu hơn
    }

    inner class ViewHolder(private val imageView: ImageView) : RecyclerView.ViewHolder(imageView) {
        fun bind(uri: Uri) {
            Glide.with(imageView.context)
                .load(uri)
                .centerCrop()
                .placeholder(R.color.dark_gray) // Thêm ảnh chờ trong khi tải
                .into(imageView)

            imageView.setOnClickListener {
                onImageClick(uri)
            }
        }
    }
}
