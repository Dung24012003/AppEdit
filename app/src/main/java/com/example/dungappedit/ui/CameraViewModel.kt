package com.example.dungappedit.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class CameraViewModel : ViewModel() {

    private val _aspectRatio = MutableLiveData("3:4")
    val aspectRatio: LiveData<String> = _aspectRatio

    fun setAspectRatio(ratio: String) {
        _aspectRatio.value = ratio
    }
}
