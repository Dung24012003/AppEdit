package com.example.dungappedit.ui.edit.text.tabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.example.dungappedit.databinding.FragmentTextFontBinding
import com.example.dungappedit.ui.edit.text.TextEditorDialogFragment

class TextFontFragment : Fragment() {

    private lateinit var binding: FragmentTextFontBinding
    private val textEditorDialog: TextEditorDialogFragment
        get() = parentFragment as TextEditorDialogFragment

    private val availableFonts = listOf(
        "sans-serif", "sans-serif-light", "sans-serif-medium", "sans-serif-black",
        "sans-serif-condensed", "serif", "monospace", "cursive"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTextFontBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fontAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, availableFonts)
        fontAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.fontSpinner.adapter = fontAdapter

        val fontIndex = availableFonts.indexOf(textEditorDialog.currentTextStyle.fontFamily)
        if (fontIndex >= 0) {
            binding.fontSpinner.setSelection(fontIndex)
        }

        binding.fontSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                textEditorDialog.currentTextStyle.fontFamily = availableFonts[position]
                textEditorDialog.updatePreview()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }
} 