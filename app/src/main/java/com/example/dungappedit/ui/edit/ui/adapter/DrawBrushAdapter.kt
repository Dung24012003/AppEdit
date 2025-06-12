package com.example.dungappedit.ui.edit.ui.adapter

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.dungappedit.R

data class Brush(val color: Int, val size: Float)

class DrawBrushAdapter(
    private val brushes: List<Brush>,
    private val onBrushSelected: (Brush) -> Unit
) : RecyclerView.Adapter<DrawBrushAdapter.BrushViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BrushViewHolder {
        // This layout needs to be created, e.g., res/layout/item_brush.xml
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_brush, parent, false)
        return BrushViewHolder(view)
    }

    override fun onBindViewHolder(holder: BrushViewHolder, position: Int) {
        val brush = brushes[position]
        holder.brushPreview.background.setColorFilter(brush.color, PorterDuff.Mode.SRC_IN)
        holder.itemView.setOnClickListener { onBrushSelected(brush) }
    }

    override fun getItemCount(): Int = brushes.size

    class BrushViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val brushPreview: View = view.findViewById(R.id.brush_preview)
    }
} 