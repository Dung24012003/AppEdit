package com.example.dungappedit.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.dungappedit.databinding.ItemFrameBinding
import com.example.dungappedit.model.Frame

class FrameAdapter(
    private val frameItems: List<Frame>,
    private val onFrameSelected: (Frame) -> Unit
) : RecyclerView.Adapter<FrameAdapter.FrameViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FrameViewHolder {
        val binding = ItemFrameBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FrameViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FrameViewHolder, position: Int) {
        holder.bind(frameItems[position])
    }

    override fun getItemCount(): Int = frameItems.size

    inner class FrameViewHolder(private val binding: ItemFrameBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(frameItem: Frame) {
            binding.framePreview.setImageResource(frameItem.frameResourceId)
            binding.frameName.text = frameItem.name

            binding.root.setOnClickListener {
                onFrameSelected(frameItem)
            }
        }
    }
}
