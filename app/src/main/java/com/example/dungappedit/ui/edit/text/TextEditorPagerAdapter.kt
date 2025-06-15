package com.example.dungappedit.ui.edit.text

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.dungappedit.ui.edit.text.tabs.TextColorFragment
import com.example.dungappedit.ui.edit.text.tabs.TextContentFragment
import com.example.dungappedit.ui.edit.text.tabs.TextFontFragment
import com.example.dungappedit.ui.edit.text.tabs.TextShadowFragment
import com.example.dungappedit.ui.edit.text.tabs.TextSizeFragment
import com.example.dungappedit.ui.edit.text.tabs.TextStrokeFragment

class TextEditorPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 6

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> TextContentFragment()
            1 -> TextColorFragment()
            2 -> TextStrokeFragment()
            3 -> TextFontFragment()
            4 -> TextSizeFragment()
            5 -> TextShadowFragment()
            else -> throw IllegalStateException("Invalid position: $position")
        }
    }
} 