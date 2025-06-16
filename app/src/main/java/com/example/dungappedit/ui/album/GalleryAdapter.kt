package com.example.dungappedit.ui.album

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.dungappedit.R

class GalleryAdapter(
    private val imageUris: List<Uri>,
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

    inner class ViewHolder(private val imageView: ImageView) : RecyclerView.ViewHolder(imageView) {
        fun bind(uri: Uri) {
            Glide.with(imageView.context)
                .load(uri)
                .centerCrop()
                .into(imageView)

            imageView.setOnClickListener {
                onImageClick(uri)
            }
        }
    }
}
