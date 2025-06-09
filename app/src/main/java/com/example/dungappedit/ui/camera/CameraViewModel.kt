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

    private val _isTimerRatioContainerVisible = MutableLiveData(false)
    val isTimerRatioContainerVisible: LiveData<Boolean> = _isTimerRatioContainerVisible

    private val _timerSeconds = MutableLiveData(0)
    val timerSeconds: LiveData<Int> = _timerSeconds

    private val _isBrightnessControlVisible = MutableLiveData(false)
    val isBrightnessControlVisible: LiveData<Boolean> = _isBrightnessControlVisible

    private val _brightnessLevel = MutableLiveData(50)
    val brightnessLevel: LiveData<Int> = _brightnessLevel

    fun setAspectRatio(ratio: String) {
        _aspectRatio.value = ratio
    }

    fun toggleFlash() {
        _isFlashEnabled.value = !(_isFlashEnabled.value ?: false)
    }

    fun setFilter(filter: CameraFilter) {
        _currentFilter.value = filter
    }

    fun switchCamera() {
        _lensFacing.value = if (_lensFacing.value == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            _isFlashEnabled.value = false
            CameraSelector.LENS_FACING_BACK
        }
    }

    fun toggleGrid() {
        _isGridVisible.value = !(_isGridVisible.value ?: false)
    }

    fun toggleTimerRatioContainer() {
        _isTimerRatioContainerVisible.value = !(_isTimerRatioContainerVisible.value ?: false)

        // Khi hiển thị container, ẩn các control khác
        if (_isTimerRatioContainerVisible.value == true) {
            _isBrightnessControlVisible.value = false
        }
    }

    fun setTimerSeconds(seconds: Int) {
        _timerSeconds.value = seconds
    }

    fun toggleBrightnessControl() {
        _isBrightnessControlVisible.value = !(_isBrightnessControlVisible.value ?: false)

        // Khi hiển thị brightness control, ẩn các control khác
        if (_isBrightnessControlVisible.value == true && _isTimerRatioContainerVisible.value == true) {
            _isTimerRatioContainerVisible.value = false
        }
    }

    fun setBrightnessLevel(level: Int) {
        _brightnessLevel.value = level.coerceIn(0, 100)
    }

    fun resetBrightnessLevel() {
        _brightnessLevel.value = 50
    }

    fun toggleTimerRatioContainerAndBrightnessControl() {
        when{
            _isBrightnessControlVisible.value == true -> _isBrightnessControlVisible.value = false
            _isTimerRatioContainerVisible.value == true -> _isTimerRatioContainerVisible.value = false
        }
    }
}
