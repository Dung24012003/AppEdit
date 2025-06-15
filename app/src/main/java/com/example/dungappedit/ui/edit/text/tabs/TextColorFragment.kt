package com.example.dungappedit.ui.edit.text.tabs

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.dungappedit.databinding.FragmentTextColorBinding
import com.example.dungappedit.ui.edit.text.ColorPickerAdapter
import com.example.dungappedit.ui.edit.text.TextEditorDialogFragment

class TextColorFragment : Fragment() {

    private lateinit var binding: FragmentTextColorBinding
    private val textEditorDialog: TextEditorDialogFragment
        get() = parentFragment as TextEditorDialogFragment

    private val colors = listOf(
        Color.BLACK, Color.WHITE, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW,
        Color.CYAN, Color.MAGENTA, Color.GRAY, Color.DKGRAY, Color.LTGRAY
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTextColorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ColorPickerAdapter(colors) { color ->
            textEditorDialog.currentTextStyle.textColor = color
            textEditorDialog.updatePreview()
        }
        binding.colorRecyclerView.layoutManager = GridLayoutManager(context, 6)
        binding.colorRecyclerView.adapter = adapter
        adapter.setSelectedColor(textEditorDialog.currentTextStyle.textColor)
    }
} 