package com.example.dungappedit.ui.edit.text

import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.dungappedit.R
import com.example.dungappedit.databinding.DialogEditTextBinding
import com.example.dungappedit.model.TextStyle
import com.google.android.material.tabs.TabLayoutMediator

class TextEditorDialogFragment : DialogFragment() {

    private lateinit var binding: DialogEditTextBinding
    internal var onApplyListener: ((TextStyle) -> Unit)? = null
    internal var currentTextStyle = TextStyle()

    companion object {
        private const val KEY_TEXT_STYLE = "text_style"

        fun newInstance(textStyle: TextStyle, onApply: (TextStyle) -> Unit): TextEditorDialogFragment {
            val fragment = TextEditorDialogFragment()
            fragment.onApplyListener = onApply
            val args = Bundle()
            args.putParcelable(KEY_TEXT_STYLE, textStyle)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            currentTextStyle = it.getParcelable<TextStyle>(KEY_TEXT_STYLE) ?: TextStyle()
        }
    }
    
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (Resources.getSystem().displayMetrics.heightPixels * 0.85).toInt())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogEditTextBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupViewPager()
        updatePreview()

        binding.fabDone.setOnClickListener {
            onApplyListener?.invoke(currentTextStyle)
            dismiss()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { dismiss() }
    }

    private fun setupViewPager() {
        val adapter = TextEditorPagerAdapter(this)
        binding.textEditViewpager.adapter = adapter

        TabLayoutMediator(binding.textEditTabs, binding.textEditViewpager) { tab, position ->
            tab.text = when (position) {
                0 -> "Text"
                1 -> "Color"
                2 -> "Stroke"
                3 -> "Font"
                4 -> "Size"
                5 -> "Shadow"
                else -> null
            }
        }.attach()
    }

    fun updatePreview() {
        binding.textPreview.apply {
            text = currentTextStyle.text
            textSize = currentTextStyle.textSize
            setTextColor(currentTextStyle.textColor)
            typeface = Typeface.create(currentTextStyle.fontFamily, Typeface.NORMAL)

            if (currentTextStyle.isStrokeEnabled) {
                strokeWidthValue = currentTextStyle.strokeWidth
                strokeColorValue = currentTextStyle.strokeColor
            } else {
                strokeWidthValue = 0f
            }

            if (currentTextStyle.isShadowEnabled) {
                setShadowLayer(
                    currentTextStyle.shadowRadius,
                    currentTextStyle.shadowDx,
                    currentTextStyle.shadowDy,
                    currentTextStyle.shadowColor
                )
            } else {
                setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            }
            
            invalidate() // Redraw the view
        }
    }
} 