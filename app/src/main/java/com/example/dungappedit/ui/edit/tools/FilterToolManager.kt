package com.example.dungappedit.ui.edit.tools

import androidx.recyclerview.widget.RecyclerView
import com.example.dungappedit.ui.edit.ui.adapter.FilterAdapter
import com.example.dungappedit.ui.edit.utils.ImageLayerController
import android.graphics.ColorMatrix
import android.view.View
import com.example.dungappedit.model.Filter

class FilterToolManager(
    private val filterRecyclerView: RecyclerView,
    private val imageLayerController: ImageLayerController
) : BaseToolManager {

    private val filterAdapter: FilterAdapter

    init {
        val filters = loadFilters()
        filterAdapter = FilterAdapter(filters) { filter ->
            onFilterSelected(filter)
        }
        filterRecyclerView.adapter = filterAdapter
    }

    override fun activate() {
        filterRecyclerView.visibility = RecyclerView.VISIBLE
    }

    override fun deactivate() {
        filterRecyclerView.visibility = RecyclerView.GONE
        imageLayerController.clearFilter()
    }

    override fun isToolActive(): Boolean {
        return filterRecyclerView.visibility == View.VISIBLE
    }

    override fun applyChanges() {
        // Filter is applied via ImageLayerController, no separate apply needed here
    }

    private fun onFilterSelected(filter: Filter) {
        imageLayerController.applyFilter(filter)
    }

    private fun loadFilters(): List<Filter> {
        return listOf(
            Filter("None", ColorMatrix()), // Identity matrix
            Filter("Sepia", ColorMatrix().apply {
                setSaturation(0f)
                postConcat(ColorMatrix().apply {
                    setScale(1f, 0.95f, 0.82f, 1f)
                })
            }),
            Filter("Grayscale", ColorMatrix().apply { setSaturation(0f) }),
            Filter("Invert", ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            ))
        )
    }
} 
