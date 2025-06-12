package com.example.dungappedit.ui.edit.tools

import android.view.View
import android.widget.Button
import com.example.dungappedit.canvas.DrawOnImageView
import com.example.dungappedit.model.MovableItem
import com.example.dungappedit.ui.edit.text.TextEditor

class TextToolManager(
    private val drawView: DrawOnImageView,
    private val textControls: View, // A layout containing the "Add Text" button
) : BaseToolManager {

    private val textEditor: TextEditor = TextEditor(drawView.context)

    override fun activate() {
        textControls.visibility = View.VISIBLE
        // Deactivate drawing when text tool is active to prevent conflicts
        drawView.enableDrawing(false)
    }

    override fun deactivate() {
        textControls.visibility = View.GONE
    }

    override fun isToolActive(): Boolean {
        return textControls.visibility == View.VISIBLE
    }

    override fun applyChanges() {
        // Text changes are applied via the TextEditor dialog.
    }

    fun setupListeners(addTextButton: Button) {
        addTextButton.setOnClickListener {
            createText()
        }
    }

    private fun createText() {
        textEditor.showCreateDialog { textStyle ->
            drawView.addTextItem(textStyle.text, textStyle)
        }
    }

    fun editText(textItem: MovableItem.TextItem) {
        textEditor.showEditDialog(textItem) { newTextStyle ->
            drawView.updateTextItem(textItem, newTextStyle)
        }
    }
} 