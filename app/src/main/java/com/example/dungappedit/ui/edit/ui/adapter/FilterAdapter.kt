package com.example.dungappedit.ui.edit.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.dungappedit.R
import com.example.dungappedit.model.Filter
import android.graphics.ColorMatrixColorFilter

class FilterAdapter(
    private val filters: List<Filter>,
    private val onFilterClick: (Filter) -> Unit
) : RecyclerView.Adapter<FilterAdapter.FilterViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterViewHolder {
        // Inflate the item layout for the filter preview.
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_filter, parent, false)
        return FilterViewHolder(view)
    }

    override fun onBindViewHolder(holder: FilterViewHolder, position: Int) {
        val filter = filters[position]
        holder.filterName.text = filter.name
        // Apply the filter's color matrix to the preview image.
        holder.filterPreview.colorFilter = ColorMatrixColorFilter(filter.matrix)
        holder.itemView.setOnClickListener { onFilterClick(filter) }
    }

    override fun getItemCount(): Int = filters.size

    class FilterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val filterPreview: ImageView = view.findViewById(R.id.filter_preview)
        val filterName: TextView = view.findViewById(R.id.filter_name)
    }
} 