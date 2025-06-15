package com.example.dungappedit.ui.edit.tools

import android.view.View
import androidx.fragment.app.FragmentActivity
import com.example.dungappedit.canvas.DrawOnImageView
import com.example.dungappedit.model.MovableItem
import com.example.dungappedit.model.TextStyle
import com.example.dungappedit.ui.edit.text.TextEditorDialogFragment

class TextToolManager(
    private val drawView: DrawOnImageView
) : BaseToolManager {

    override fun activate() {
        // Deactivate drawing mode to prevent conflicts with text interaction.
        drawView.enableDrawing(false)

        // Immediately show the text creation dialog.
        val context = drawView.context
        if (context is FragmentActivity) {
            val dialog = TextEditorDialogFragment.newInstance(TextStyle()) { textStyle ->
                drawView.addTextItem(textStyle.text, textStyle)
            }
            dialog.show(context.supportFragmentManager, "TextEditorDialog")
        }
    }

    override fun deactivate() {
        // No specific deactivation needed, as the dialog is modal.
    }

    override fun isToolActive(): Boolean {
        // This tool is active only momentarily when the dialog is open.
        return false
    }

    override fun applyChanges() {
        // Text changes are applied directly through the TextEditorDialogFragment.
    }

    fun editText(textItem: MovableItem.TextItem) {
        val context = drawView.context
        if (context is FragmentActivity) {
            val dialog = TextEditorDialogFragment.newInstance(textItem.textStyle ?: TextStyle()) { newTextStyle ->
                drawView.updateTextItem(textItem, newTextStyle)
            }
            dialog.show(context.supportFragmentManager, "TextEditorDialog")
        }
    }
} 
