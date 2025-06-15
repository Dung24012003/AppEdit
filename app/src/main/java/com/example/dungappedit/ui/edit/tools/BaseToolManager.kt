package com.example.dungappedit.ui.edit.tools

interface BaseToolManager {
  fun activate()      // Activates the tool, showing its UI and enabling its behavior.
  fun deactivate()    // Deactivates the tool, hiding its UI.
  fun isToolActive(): Boolean // Returns true if the tool is currently active.
  fun applyChanges()  // Applies any pending changes to the image.
}