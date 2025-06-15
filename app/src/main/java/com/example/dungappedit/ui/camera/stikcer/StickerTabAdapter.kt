package com.example.dungappedit.ui.camera.stikcer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.example.dungappedit.R
import com.google.android.material.tabs.TabLayout
import kotlin.math.ceil

class StickerTabAdapter(
    private val context: Context,
    private val onStickerSelected: (Sticker) -> Unit
) {
    // Add reference variables like in FilterTabAdapter
    private var tabLayoutRef: TabLayout? = null
    private var currentSelectedTab: TabLayout.Tab? = null

    fun setupStickerTabs(tabLayout: TabLayout, stickers: List<Sticker>) {
        tabLayoutRef = tabLayout
        tabLayout.removeAllTabs()

        // Add the real sticker tabs
        stickers.forEach { sticker ->
            val tab = tabLayout.newTab()
            tab.customView = createTabView(sticker)
            tab.tag = sticker // Set the sticker to the tag for easy retrieval on selection
            tabLayout.addTab(tab)
        }

        // Add padding tabs to center align
        addPaddingTabs()
        val paddingTabsCount = getPaddingTabsCount()

        // Default select the first tab ("None" tab)
        if (tabLayout.tabCount > paddingTabsCount) {
            val noneTab = tabLayout.getTabAt(paddingTabsCount)
            currentSelectedTab = noneTab
            updateSelectedTab(currentSelectedTab)
            // Call the callback with the first sticker
            (noneTab?.tag as? Sticker)?.let { onStickerSelected(it) }
            // Center the selected tab in the view
            centerTabInView(noneTab)
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val totalTabs = tabLayout.tabCount

                // Handle when the user selects a padding tab -> auto-select the nearest real tab
                if (tab.position < paddingTabsCount || tab.position >= totalTabs - paddingTabsCount) {
                    val closestRealTab = if (tab.position < paddingTabsCount) {
                        tabLayout.getTabAt(paddingTabsCount)
                    } else {
                        tabLayout.getTabAt(totalTabs - paddingTabsCount - 1)
                    }
                    closestRealTab?.takeIf { it != tab }?.select()
                    return
                }

                // Update the UI and call the callback
                updateSelectedTab(tab)
                (tab.tag as? Sticker)?.let { onStickerSelected(it) }
                currentSelectedTab = tab
                centerTabInView(tab)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // Remove the border from the unselected tab
                tab?.customView?.findViewById<View>(R.id.filter_border)?.visibility = View.GONE
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                centerTabInView(tab)
            }
        })

        // Handle click events manually for real tabs only
        for (i in 0 until tabLayout.tabCount) {
            val tab = tabLayout.getTabAt(i) ?: continue
            if (i in getPaddingTabsCount() until (tabLayout.tabCount - getPaddingTabsCount())) {
                tab.view.setOnClickListener {
                    tab.select()
                }
            }
        }
    }

    // --- HELPER FUNCTIONS COPIED FROM FilterTabAdapter ---

    private fun getPaddingTabsCount(): Int {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val tabWidth = context.resources.displayMetrics.density * 80 // Assume tab width is 80dp
        return ceil((screenWidth / (2 * tabWidth)).toDouble()).toInt()
    }

    private fun addPaddingTabs() {
        val tabLayout = tabLayoutRef ?: return
        val paddingTabsNeeded = getPaddingTabsCount()

        // Add to the beginning
        repeat(paddingTabsNeeded) {
            val paddingTab = tabLayout.newTab()
            paddingTab.customView = createPaddingView()
            tabLayout.addTab(paddingTab, 0)
        }

        // Add to the end
        repeat(paddingTabsNeeded) {
            val paddingTab = tabLayout.newTab()
            paddingTab.customView = createPaddingView()
            tabLayout.addTab(paddingTab)
        }
    }

    private fun createPaddingView(): View {
        val view = LayoutInflater.from(context).inflate(R.layout.item_filter_tab, tabLayoutRef, false)
        view.visibility = View.INVISIBLE // Make it invisible but still take up space
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
        // Deselect the old tab
        currentSelectedTab?.customView?.findViewById<View>(R.id.filter_border)?.visibility = View.GONE
        // Show the border for the newly selected tab
        tab?.customView?.findViewById<View>(R.id.filter_border)?.visibility = View.VISIBLE
    }

    private fun createTabView(sticker: Sticker): View {
        val view = LayoutInflater.from(context).inflate(R.layout.item_filter_tab, tabLayoutRef, false)
        val imageView = view.findViewById<ImageView>(R.id.filter_preview)
        val textView = view.findViewById<TextView>(R.id.filter_name)

        if (sticker == Sticker.NONE) {
            imageView.setImageResource(R.drawable.ic_close) // "forbidden" or "off" icon
        } else {
            imageView.setImageResource(sticker.drawableId)
        }
        textView.text = sticker.getDisplayName()

        view.findViewById<View>(R.id.filter_border).visibility = View.GONE
        return view
    }
}
