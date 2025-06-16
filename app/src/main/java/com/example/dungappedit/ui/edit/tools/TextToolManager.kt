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
        // Deactivate drawing to prevent conflicts
        drawView.enableDrawing(false)

        // Show the text creation dialog immediately
        val context = drawView.context
        if (context is FragmentActivity) {
            val dialog = TextEditorDialogFragment.newInstance(TextStyle()) { textStyle ->
                drawView.addTextItem(textStyle.text, textStyle)
            }
            dialog.show(context.supportFragmentManager, "TextEditorDialog")
        }
    }

    override fun deactivate() {
        // No specific deactivation needed for controls as they are removed.
    }

    override fun isToolActive(): Boolean {
        // The tool is active transiently, so we can return false.
        return false
    }

    override fun applyChanges() {
        // Text changes are applied via the TextEditor dialog.
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
