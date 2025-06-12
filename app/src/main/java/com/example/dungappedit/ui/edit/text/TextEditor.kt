package com.example.dungappedit.ui.edit.text

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import com.example.dungappedit.R
import com.example.dungappedit.model.TextStyle
import com.google.android.material.textfield.TextInputEditText

/**
 * TextEditor handles editing of text items with various styling options
 */
class TextEditor(private val context: Context) {

    private val availableFonts = listOf(
        "sans-serif",
        "sans-serif-light",
        "sans-serif-medium",
        "sans-serif-black",
        "sans-serif-condensed",
        "serif",
        "monospace",
        "cursive"
    )

    private var currentTextStyle = TextStyle()
    private var dialog: Dialog? = null
    private var previewText: TextView? = null
    private var onApplyListener: ((TextStyle) -> Unit)? = null

    /**
     * Show the text editor dialog to create a new text
     */
    fun showCreateDialog(initialText: String = "Sample Text", listener: (TextStyle) -> Unit) {
        currentTextStyle = TextStyle(text = initialText)
        onApplyListener = listener
        showDialog()
    }

    /**
     * Show the text editor dialog to edit an existing text item
     */
    fun showEditDialog(textItem: com.example.dungappedit.model.MovableItem.TextItem, listener: (TextStyle) -> Unit) {
        // Get existing text style or create a new one
        currentTextStyle = textItem.textStyle?.copy() ?: TextStyle(
            text = textItem.text,
            textColor = textItem.paint.color,
            textSize = textItem.paint.textSize
        )

        onApplyListener = listener
        showDialog()
    }

    private fun showDialog() {
        // Create the dialog
        dialog = Dialog(context, com.google.android.material.R.style.Theme_Material3_Light_Dialog)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_edit_text, null)
        dialog?.setContentView(view)
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // Initialize UI components
        previewText = view.findViewById(R.id.textPreview)
        val textInput = view.findViewById<TextInputEditText>(R.id.editTextInput)
        val textSizeSeekBar = view.findViewById<SeekBar>(R.id.textSizeSeekBar)
        val fontSpinner = view.findViewById<Spinner>(R.id.fontSpinner)
        val strokeSwitch = view.findViewById<Switch>(R.id.strokeSwitch)
        val strokeSettingsContainer = view.findViewById<View>(R.id.strokeSettingsContainer)
        val strokeWidthSeekBar = view.findViewById<SeekBar>(R.id.strokeWidthSeekBar)
        val shadowSwitch = view.findViewById<Switch>(R.id.shadowSwitch)
        val shadowSettingsContainer = view.findViewById<View>(R.id.shadowSettingsContainer)
        val shadowRadiusSeekBar = view.findViewById<SeekBar>(R.id.shadowRadiusSeekBar)
        val shadowDxSeekBar = view.findViewById<SeekBar>(R.id.shadowDxSeekBar)
        val shadowDySeekBar = view.findViewById<SeekBar>(R.id.shadowDySeekBar)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnApply = view.findViewById<Button>(R.id.btnApply)

        // Set up text input
        textInput.setText(currentTextStyle.text)
        textInput.setOnEditorActionListener { _, _, _ ->
            currentTextStyle.text = textInput.text.toString()
            updatePreview()
            false
        }

        // Text color buttons
        setupColorButtons(view, R.id.colorContainer) { color ->
            currentTextStyle.textColor = color
            updatePreview()
        }

        // Stroke color buttons
        setupColorButtons(view, R.id.strokeColorContainer) { color ->
            currentTextStyle.strokeColor = color
            updatePreview()
        }

        // Shadow color buttons
        setupColorButtons(view, R.id.shadowColorContainer) { color ->
            currentTextStyle.shadowColor = color
            updatePreview()
        }

        // Set up stroke switch
        strokeSwitch.isChecked = currentTextStyle.isStrokeEnabled
        strokeSettingsContainer.visibility =
            if (currentTextStyle.isStrokeEnabled) View.VISIBLE else View.GONE
        strokeSwitch.setOnCheckedChangeListener { _, isChecked ->
            currentTextStyle.isStrokeEnabled = isChecked
            strokeSettingsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            updatePreview()
        }

        // Set up shadow switch
        shadowSwitch.isChecked = currentTextStyle.isShadowEnabled
        shadowSettingsContainer.visibility =
            if (currentTextStyle.isShadowEnabled) View.VISIBLE else View.GONE
        shadowSwitch.setOnCheckedChangeListener { _, isChecked ->
            currentTextStyle.isShadowEnabled = isChecked
            shadowSettingsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            updatePreview()
        }

        // Set up text size seek bar
        textSizeSeekBar.progress = (currentTextStyle.textSize / 2).toInt()
        textSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentTextStyle.textSize = progress.toFloat() * 2 // Scale to 0-200
                updatePreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Set up stroke width seek bar
        strokeWidthSeekBar.progress = currentTextStyle.strokeWidth.toInt()
        strokeWidthSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentTextStyle.strokeWidth = progress.toFloat()
                updatePreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Set up shadow radius seek bar
        shadowRadiusSeekBar.progress = currentTextStyle.shadowRadius.toInt()
        shadowRadiusSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentTextStyle.shadowRadius = progress.toFloat()
                updatePreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Set up shadow dx seek bar
        shadowDxSeekBar.progress = currentTextStyle.shadowDx.toInt()
        shadowDxSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentTextStyle.shadowDx = progress.toFloat()
                updatePreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Set up shadow dy seek bar
        shadowDySeekBar.progress = currentTextStyle.shadowDy.toInt()
        shadowDySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentTextStyle.shadowDy = progress.toFloat()
                updatePreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Set up font spinner
        val fontAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, availableFonts)
        fontAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fontSpinner.adapter = fontAdapter

        // Set selected font
        val fontIndex = availableFonts.indexOf(currentTextStyle.fontFamily)
        if (fontIndex >= 0) {
            fontSpinner.setSelection(fontIndex)
        }

        fontSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                currentTextStyle.fontFamily = availableFonts[position]
                updatePreview()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Set up buttons
        btnCancel.setOnClickListener {
            dialog?.dismiss()
        }

        btnApply.setOnClickListener {
            // Update text in case it wasn't already updated
            currentTextStyle.text = textInput.text.toString()

            onApplyListener?.invoke(currentTextStyle)
            dialog?.dismiss()
        }

        // Initial preview update
        updatePreview()

        // Show the dialog
        dialog?.show()
    }

    private fun setupColorButtons(view: View, containerId: Int, onColorSelected: (Int) -> Unit) {
        val container = view.findViewById<View>(containerId)
        if (container is ViewGroup) {
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child is Button) {
                    val colorTag = child.tag?.toString()
                    if (colorTag != null) {
                        try {
                            val color = Color.parseColor(colorTag)
                            child.setOnClickListener {
                                onColorSelected(color)
                            }
                        } catch (e: Exception) {
                            // Invalid color format, ignore
                        }
                    }
                }
            }
        }
    }

    private fun updatePreview() {
        previewText?.let { preview ->
            // Apply text
            preview.text = currentTextStyle.text.ifEmpty { "Sample Text" }

            // Apply color
            preview.setTextColor(currentTextStyle.textColor)

            // Apply text size
            preview.textSize = currentTextStyle.textSize / 3 // Scale down for preview

            // Apply font
            val typeface = Typeface.create(currentTextStyle.fontFamily, currentTextStyle.typeface)
            preview.typeface = typeface

            // Apply shadow if enabled
            if (currentTextStyle.isShadowEnabled) {
                preview.setShadowLayer(
                    currentTextStyle.shadowRadius / 2, // Scale down for preview
                    currentTextStyle.shadowDx / 2,     // Scale down for preview
                    currentTextStyle.shadowDy / 2,     // Scale down for preview
                    currentTextStyle.shadowColor
                )
            } else {
                preview.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            }

            // Apply stroke (we can't directly preview stroke on TextView)
            // Instead, we'll indicate it with a border
            if (currentTextStyle.isStrokeEnabled) {
                preview.setBackgroundResource(R.drawable.text_preview_border)
            } else {
                preview.setBackgroundColor(Color.parseColor("#F5F5F5"))
            }
        }
    }
} 
