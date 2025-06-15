package com.example.dungappedit.ui.edit.text.tabs

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.dungappedit.databinding.FragmentTextContentBinding
import com.example.dungappedit.ui.edit.text.TextEditorDialogFragment

class TextContentFragment : Fragment() {

    private lateinit var binding: FragmentTextContentBinding
    private val textEditorDialog: TextEditorDialogFragment
        get() = parentFragment as TextEditorDialogFragment

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTextContentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.editTextInput.setText(textEditorDialog.currentTextStyle.text)
        binding.editTextInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                textEditorDialog.currentTextStyle.text = s.toString()
                textEditorDialog.updatePreview()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }
} 