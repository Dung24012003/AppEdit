package com.example.dungappedit.ui.album

import android.content.Intent
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.dungappedit.ui.album.preview.ImagePreviewActivity
import com.example.dungappedit.R
import com.example.dungappedit.common.Constans
import java.io.File

class AdapterImage(private val imageFiles: List<File>) :
    RecyclerView.Adapter<AdapterImage.ImageViewHolder>() {

    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imgItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun getItemCount(): Int = imageFiles.size

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val file = imageFiles[position]
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        holder.imageView.setImageBitmap(bitmap)

        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, ImagePreviewActivity::class.java)
            intent.putExtra(Constans.KEY_DATA_IMG, imageFiles[position].absolutePath)
            context.startActivity(intent)
        }
    }
}
