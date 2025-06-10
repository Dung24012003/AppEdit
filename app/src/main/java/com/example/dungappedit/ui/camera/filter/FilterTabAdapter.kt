package com.example.dungappedit.ui.camera.filter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.example.dungappedit.R
import com.google.android.material.tabs.TabLayout
import jp.co.cyberagent.android.gpuimage.GPUImage
import kotlin.math.ceil

class FilterTabAdapter(private val context: Context) {

    private var tabLayoutRef: TabLayout? = null

    private val sampleBitmap: Bitmap by lazy {
        BitmapFactory.decodeResource(context.resources, R.drawable.itemfilter)
    }

    private val gpuImage: GPUImage by lazy {
        GPUImage(context)
    }

    private var currentSelectedTab: TabLayout.Tab? = null

    fun setupFilterTabs(tabLayout: TabLayout, onFilterSelected: (CameraFilter) -> Unit) {
        tabLayoutRef = tabLayout
        tabLayout.removeAllTabs()

        // Disable TabLayout dragging
        tabLayout.touchables.forEach { it.isClickable = true }
        tabLayout.setScrollPosition(0, 0f, false)
        tabLayout.isHorizontalScrollBarEnabled = false

        CameraFilter.entries.forEach { filter ->
            val tab = tabLayout.newTab()
            tab.customView = createTabView(filter)
            tabLayout.addTab(tab)
        }

        // Add padding tabs to center the first tab
        addPaddingTabs()
        val paddingTabsCount = getPaddingTabsCount()

        // Default select first tab (Original)
        if (tabLayout.tabCount > paddingTabsCount) {
            val originalTab = tabLayout.getTabAt(paddingTabsCount)
            currentSelectedTab = originalTab
            updateSelectedTab(currentSelectedTab)
            onFilterSelected(CameraFilter.ORIGINAL)
            centerTabInView(originalTab)
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val paddingTabsCount = getPaddingTabsCount()
                val totalTabs = tabLayout.tabCount

                // Handle padding tabs
                if (tab.position < paddingTabsCount || tab.position >= totalTabs - paddingTabsCount) {
                    val closestRealTab = if (tab.position < paddingTabsCount) {
                        tabLayout.getTabAt(paddingTabsCount)
                    } else {
                        tabLayout.getTabAt(totalTabs - paddingTabsCount - 1)
                    }
                    closestRealTab?.takeIf { it != tab }?.select()
                    return
                }

                updateSelectedTab(tab)
                val filterIndex = tab.position - paddingTabsCount
                if (filterIndex in CameraFilter.entries.indices) {
                    onFilterSelected(CameraFilter.entries[filterIndex])
                }
                currentSelectedTab = tab
                centerTabInView(tab)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                tab?.customView?.findViewById<View>(R.id.filter_border)?.visibility = View.GONE
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                centerTabInView(tab)
            }
        })

        // Handle click manually for real tabs only
        for (i in 0 until tabLayout.tabCount) {
            val tab = tabLayout.getTabAt(i) ?: continue
            if (i in getPaddingTabsCount() until (tabLayout.tabCount - getPaddingTabsCount())) {
                tab.view.setOnClickListener {
                    tab.select()
                }
            }
        }
    }

    private fun getPaddingTabsCount(): Int {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val tabWidth = context.resources.displayMetrics.density * 80
        return ceil((screenWidth / (2 * tabWidth)).toDouble()).toInt()
    }

    private fun addPaddingTabs() {
        val tabLayout = tabLayoutRef ?: return
        val paddingTabsNeeded = getPaddingTabsCount()

        // Add to start
        repeat(paddingTabsNeeded) {
            val paddingTab = tabLayout.newTab()
            paddingTab.customView = createPaddingView()
            tabLayout.addTab(paddingTab, 0)
        }

        // Add to end
        repeat(paddingTabsNeeded) {
            val paddingTab = tabLayout.newTab()
            paddingTab.customView = createPaddingView()
            tabLayout.addTab(paddingTab)
        }
    }

    private fun createPaddingView(): View {
        val view =
            LayoutInflater.from(context).inflate(R.layout.item_filter_tab, tabLayoutRef, false)
        view.alpha = 0f
        return view
    }

    private fun centerTabInView(tab: TabLayout.Tab?) {
        val tabLayout = tabLayoutRef ?: return
        tab?.let {
            val tabView = it.view
            val screenWidth = context.resources.displayMetrics.widthPixels
            val tabCenter = tabView.width / 2
            val targetScrollX = tabView.left - (screenWidth / 2) + tabCenter

            tabLayout.post {
                tabLayout.smoothScrollTo(targetScrollX, 0)
            }
        }
    }

    private fun updateSelectedTab(tab: TabLayout.Tab?) {
        currentSelectedTab?.customView?.findViewById<View>(R.id.filter_border)?.visibility =
            View.GONE
        tab?.customView?.findViewById<View>(R.id.filter_border)?.visibility = View.VISIBLE
    }

    private fun createTabView(filter: CameraFilter): View {
        val view =
            LayoutInflater.from(context).inflate(R.layout.item_filter_tab, tabLayoutRef, false)
        val imageView = view.findViewById<ImageView>(R.id.filter_preview)
        val textView = view.findViewById<TextView>(R.id.filter_name)
        val borderView = view.findViewById<View>(R.id.filter_border)

        gpuImage.setImage(sampleBitmap)
        gpuImage.setFilter(filter.createFilter())
        imageView.setImageBitmap(gpuImage.bitmapWithFilterApplied)
        textView.text = getFilterName(filter)
        borderView.visibility = View.GONE

        return view
    }

    private fun getFilterName(filter: CameraFilter): String {
        return when (filter) {
            CameraFilter.ORIGINAL -> "Gốc"
            CameraFilter.BRIGHT -> "Sáng"
            CameraFilter.DARK -> "Tối"
            CameraFilter.WARM -> "Vàng"
            CameraFilter.COOL -> "Lạnh"
            CameraFilter.COMIC -> "Truyện"
            CameraFilter.PENCIL -> "Vẽ"
            CameraFilter.VIGNETTE -> "Bo góc"
        }
    }
}
