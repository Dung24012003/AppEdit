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

class FilterTabAdapter(private val context: Context) {

    private val sampleBitmap: Bitmap by lazy {
        BitmapFactory.decodeResource(context.resources, R.drawable.itemfilter)
    }

    private val gpuImage: GPUImage by lazy {
        GPUImage(context)
    }

    private var currentSelectedTab: TabLayout.Tab? = null

    fun setupFilterTabs(tabLayout: TabLayout, onFilterSelected: (CameraFilter) -> Unit) {
        tabLayout.removeAllTabs()

        // Vô hiệu hóa chức năng kéo của TabLayout
        tabLayout.touchables.forEach { it.isClickable = true }
        tabLayout.setScrollPosition(0, 0f, false)
        tabLayout.isHorizontalScrollBarEnabled = false

        CameraFilter.values().forEach { filter ->
            val tab = tabLayout.newTab()
            val customView = createTabView(filter)
            tab.customView = customView
            tabLayout.addTab(tab)
        }

        // Thêm các tab đệm để căn giữa tab đầu tiên
        addPaddingTabs(tabLayout)
        val paddingTabsCount = Math.ceil(
            (context.resources.displayMetrics.widthPixels /
                    (2 * context.resources.displayMetrics.density * 80)).toDouble()
        ).toInt()

        // Mặc định chọn tab đầu tiên (Original/Gốc)
        if (tabLayout.tabCount > paddingTabsCount) {
            val originalTabPosition = paddingTabsCount
            currentSelectedTab = tabLayout.getTabAt(originalTabPosition)
            updateSelectedTab(currentSelectedTab)
            onFilterSelected(CameraFilter.ORIGINAL)
            centerTabInView(tabLayout, currentSelectedTab)
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val screenWidth = context.resources.displayMetrics.widthPixels
                val tabWidth = context.resources.displayMetrics.density * 80
                val paddingTabsCount = Math.ceil((screenWidth / (2 * tabWidth)).toDouble()).toInt()

                // Xử lý tab đệm
                if (tab.position < paddingTabsCount || tab.position >= tabLayout.tabCount - paddingTabsCount) {
                    val closestRealTab = if (tab.position < paddingTabsCount) {
                        tabLayout.getTabAt(paddingTabsCount)
                    } else {
                        tabLayout.getTabAt(tabLayout.tabCount - paddingTabsCount - 1)
                    }
                    closestRealTab?.let {
                        if (it != tab) {
                            it.select()
                        }
                    }
                    return
                }

                updateSelectedTab(tab)
                val filterPosition = tab.position - paddingTabsCount
                if (filterPosition >= 0 && filterPosition < CameraFilter.values().size) {
                    val filter = CameraFilter.values()[filterPosition]
                    onFilterSelected(filter)
                }
                currentSelectedTab = tab
                centerTabInView(tabLayout, tab)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                tab?.customView?.let { view ->
                    view.findViewById<View>(R.id.filter_border).visibility = View.GONE
                }
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                centerTabInView(tabLayout, tab)
            }
        })

        for (i in 0 until tabLayout.tabCount) {
            val tab = tabLayout.getTabAt(i) ?: continue
            val screenWidth = context.resources.displayMetrics.widthPixels
            val tabWidth = context.resources.displayMetrics.density * 80
            val paddingTabsCount = Math.ceil((screenWidth / (2 * tabWidth)).toDouble()).toInt()

            if (i >= paddingTabsCount && i < tabLayout.tabCount - paddingTabsCount) {
                tab.view.setOnClickListener {
                    tab.select()
                }
            }
        }
    }

    private fun centerTabInView(tabLayout: TabLayout, tab: TabLayout.Tab?) {
        tab?.let {
            val tabView = it.view
            val screenWidth = context.resources.displayMetrics.widthPixels
            val tabCenter = tabView.width / 2
            val targetScrollX = tabView.left - (screenWidth / 2) + tabCenter

            tabLayout.post {
                // Sử dụng smoothScrollTo thay vì scrollTo để có animation mượt mà
                tabLayout.smoothScrollTo(targetScrollX, 0)
            }
        }
    }

    private fun addPaddingTabs(tabLayout: TabLayout) {
        // Thêm tab đệm để các tab đầu/cuối có thể đạt đến trung tâm
        val screenWidth = context.resources.displayMetrics.widthPixels
        val tabWidth = context.resources.displayMetrics.density * 80
        val paddingTabsNeeded = Math.ceil((screenWidth / (2 * tabWidth)).toDouble()).toInt()

        // Thêm tab đệm ở đầu
        for (i in 0 until paddingTabsNeeded) {
            val paddingTab = tabLayout.newTab()
            paddingTab.customView = createPaddingView()
            tabLayout.addTab(paddingTab, 0)
        }

        // Thêm tab đệm ở cuối
        for (i in 0 until paddingTabsNeeded) {
            val paddingTab = tabLayout.newTab()
            paddingTab.customView = createPaddingView()
            tabLayout.addTab(paddingTab)
        }
    }

    private fun createPaddingView(): View {
        val view = LayoutInflater.from(context).inflate(R.layout.item_filter_tab, null)
        // Làm cho view vô hình nhưng giữ nguyên kích thước
        view.alpha = 0f
        return view
    }

    private fun updateSelectedTab(tab: TabLayout.Tab?) {
        currentSelectedTab?.customView?.let { view ->
            view.findViewById<View>(R.id.filter_border).visibility = View.GONE
        }

        tab?.customView?.let { view ->
            view.findViewById<View>(R.id.filter_border).visibility = View.VISIBLE
        }
    }

    private fun createTabView(filter: CameraFilter): View {
        val view = LayoutInflater.from(context).inflate(R.layout.item_filter_tab, null)
        val imageView = view.findViewById<ImageView>(R.id.filter_preview)
        val textView = view.findViewById<TextView>(R.id.filter_name)
        val borderView = view.findViewById<View>(R.id.filter_border)

        gpuImage.setImage(sampleBitmap)
        gpuImage.setFilter(filter.createFilter())
        val filteredBitmap = gpuImage.bitmapWithFilterApplied

        imageView.setImageBitmap(filteredBitmap)
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
            CameraFilter.VIGNETTE -> "Bo gốc"
        }
    }
} 
