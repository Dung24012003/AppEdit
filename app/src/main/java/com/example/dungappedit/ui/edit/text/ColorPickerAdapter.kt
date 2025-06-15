package com.example.dungappedit.ui.edit.text

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.dungappedit.databinding.ItemColorButtonBinding

class ColorPickerAdapter(
    private val colors: List<Int>,
    private val onColorSelected: (Int) -> Unit
) : RecyclerView.Adapter<ColorPickerAdapter.ColorViewHolder>() {

    private var selectedPosition = -1

    fun setSelectedColor(color: Int) {
        val index = colors.indexOf(color)
        if (index != -1) {
            selectedPosition = index
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
        val binding = ItemColorButtonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ColorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
        holder.bind(colors[position], position == selectedPosition)
    }

    override fun getItemCount(): Int = colors.size

    inner class ColorViewHolder(private val binding: ItemColorButtonBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(color: Int, isSelected: Boolean) {
            binding.colorView.setBackgroundColor(color)
            binding.selectionIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.root.setOnClickListener {
                onColorSelected(color)
                val previousSelectedPosition = selectedPosition
                selectedPosition = adapterPosition
                notifyItemChanged(previousSelectedPosition)
                notifyItemChanged(selectedPosition)
            }
        }
    }
} 