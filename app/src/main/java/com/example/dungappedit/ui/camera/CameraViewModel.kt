package com.example.dungappedit.ui.camera

import androidx.camera.core.CameraSelector
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.dungappedit.ui.camera.filter.CameraFilter

class CameraViewModel : ViewModel() {
    private val _aspectRatio = MutableLiveData("3:4")
    val aspectRatio: LiveData<String> = _aspectRatio

    private val _isFlashEnabled = MutableLiveData(false)
    val isFlashEnabled: LiveData<Boolean> = _isFlashEnabled

    private val _currentFilter = MutableLiveData(CameraFilter.ORIGINAL)
    val currentFilter: LiveData<CameraFilter> = _currentFilter

    private val _lensFacing = MutableLiveData(CameraSelector.LENS_FACING_BACK)
    val lensFacing: LiveData<Int> = _lensFacing

    private val _isGridVisible = MutableLiveData(false)
    val isGridVisible: LiveData<Boolean> = _isGridVisible

    private val _isTimerAndRatioVisible = MutableLiveData(false)
    val isTimerAndRatioVisible: LiveData<Boolean> = _isTimerAndRatioVisible

    private val _timerSeconds = MutableLiveData(0)
    val timerSeconds: LiveData<Int> = _timerSeconds

    fun setAspectRatio(ratio: String) {
        _aspectRatio.value = ratio
    }

    fun toggleFlash() {
        _isFlashEnabled.value = _isFlashEnabled.value != true
    }

    fun setFilter(filter: CameraFilter) {
        _currentFilter.value = filter
    }

    fun switchCamera() {
        _lensFacing.value = if (_lensFacing.value == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
    }

    fun toggleGrid() {
        _isGridVisible.value = _isGridVisible.value != true
    }

    fun toggleTimerAndRatio() {
        _isTimerAndRatioVisible.value = _isTimerAndRatioVisible.value != true
    }

    fun setTimerSeconds(seconds: Int) {
        _timerSeconds.value = seconds
    }
}
