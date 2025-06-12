package com.example.dungappedit.ui.edit.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.dungappedit.R
import com.example.dungappedit.model.Frame

class FrameAdapter(
    private val frames: List<Frame>,
    private val onFrameClick: (Frame) -> Unit
) : RecyclerView.Adapter<FrameAdapter.FrameViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FrameViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_frame, parent, false)
        return FrameViewHolder(view)
    }

    override fun onBindViewHolder(holder: FrameViewHolder, position: Int) {
        val frame = frames[position]
        holder.frameName.text = frame.name
        holder.framePreview.setImageResource(frame.imageResource)
        holder.itemView.setOnClickListener { onFrameClick(frame) }
    }

    override fun getItemCount(): Int = frames.size

    class FrameViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val framePreview: ImageView = view.findViewById(R.id.frame_preview)
        val frameName: TextView = view.findViewById(R.id.frame_name)
    }
} 