package com.example.dungappedit.ui.edit.tools

interface BaseToolManager {
  fun activate()      // show UI controls, enable tool behavior
  fun deactivate()    // hide UI, disable tool
  fun isToolActive(): Boolean // check if the tool is currently active
  fun applyChanges()  // commit edits to underlying image/layers
} 