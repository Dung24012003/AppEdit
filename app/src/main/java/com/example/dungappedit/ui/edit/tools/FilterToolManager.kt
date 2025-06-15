package com.example.dungappedit.ui.edit.tools

import androidx.recyclerview.widget.RecyclerView
import com.example.dungappedit.ui.edit.ui.adapter.FilterAdapter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.View
import com.example.dungappedit.canvas.DrawOnImageView
import com.example.dungappedit.model.Filter

class FilterToolManager(
    private val filterRecyclerView: RecyclerView,
    private val drawView: DrawOnImageView
) : BaseToolManager {

    private val filterAdapter: FilterAdapter
    private var activeFilter: Filter? = null
    
    // Reference to HueToolManager, set by EditHostFragment to combine effects.
    private var hueToolManager: HueToolManager? = null

    // Name of the original filter, used for comparison.
    private val originalFilterName = "None"

    init {
        val filters = loadFilters()
        // Set the first filter as the default active filter.
        activeFilter = filters.firstOrNull()

        filterAdapter = FilterAdapter(filters) { filter ->
            onFilterSelected(filter)
        }
        filterRecyclerView.adapter = filterAdapter
    }
    
    /**
     * Sets the HueToolManager reference to allow combining color effects.
     */
    fun setHueToolManager(hueManager: HueToolManager) {
        this.hueToolManager = hueManager
    }

    override fun activate() {
        filterRecyclerView.visibility = View.VISIBLE
        
        // Reapply current filter with any hue adjustments
        activeFilter?.let { onFilterSelected(it) }
    }

    override fun deactivate() {
        filterRecyclerView.visibility = View.GONE
        // No action needed here; the selected filter persists when switching tabs.
    }

    override fun isToolActive(): Boolean {
        return filterRecyclerView.visibility == View.VISIBLE
    }

    override fun applyChanges() {
        // Not needed, as filters are applied in real-time.
    }

    /**
     * Reverts to the original state with no filter applied, while preserving hue adjustments.
     */
    fun applyOriginalFilter() {
        // Find the original filter in the list.
        activeFilter = loadFilters().find { it.name == originalFilterName }
        
        // Apply the original (None) filter but preserve any hue adjustments.
        applyFilterWithHue(null)
    }

    /**
     * Called when a user selects a filter from the RecyclerView.
     */
    private fun onFilterSelected(filter: Filter) {
        // Update the currently selected filter.
        activeFilter = filter

        // Check if the selected filter is the original one.
        val isOriginal = (filter.name == originalFilterName)
        
        // Get the filter matrix, or null if it's the original "None" filter.
        val filterMatrix = if (isOriginal) null else filter.matrix
        
        // Apply the selected filter, combined with any active hue adjustments.
        applyFilterWithHue(filterMatrix)
    }
    
    /**
     * Applies a filter matrix combined with any active hue adjustments
     */
    private fun applyFilterWithHue(filterMatrix: ColorMatrix?) {
        // If we have a hue tool manager, combine the effects
        val combinedMatrix = hueToolManager?.combineWithFilterMatrix(filterMatrix) ?: filterMatrix
        
        // Apply the combined filter (or just the filter if no hue tool manager)
        val colorFilter = combinedMatrix?.let { ColorMatrixColorFilter(it) }
        drawView.setBackgroundImageFilter(colorFilter)
    }

    fun getActiveFilter(): Filter? {
        return activeFilter
    }

    private fun loadFilters(): List<Filter> {
        return listOf(
            // The "None" filter serves as the baseline (identity matrix).
            Filter(originalFilterName, ColorMatrix()), // Identity matrix
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
