package com.example.dungappedit.ui.camera

import androidx.camera.core.CameraSelector
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.dungappedit.ui.camera.filter.CameraFilter
import com.example.dungappedit.ui.camera.stikcer.Sticker

class CameraViewModel : ViewModel() {

    // Sticker selection state
    private val _selectedSticker = MutableLiveData(Sticker.NONE)
    val selectedSticker: LiveData<Sticker> = _selectedSticker

    val stickerOptions = Sticker.entries.toList() // Get the list of all stickers from the Enum

    fun setSelectedSticker(sticker: Sticker) {
        _selectedSticker.value = sticker
    }

    // List of choices
    val aspectRatios = listOf("3:4", "9:16", "1:1", "Full") // Add "Full" back as it will be used by ViewPort
    val timerOptions = listOf(0, 3, 5, 10) // Use Int for easier handling

    // Camera and UI state
    private val _aspectRatio = MutableLiveData(aspectRatios.first()) // "3:4"
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

    private val _timerSeconds = MutableLiveData(timerOptions.first()) // 0
    val timerSeconds: LiveData<Int> = _timerSeconds

    private val _isBrightnessControlVisible = MutableLiveData(false)
    val isBrightnessControlVisible: LiveData<Boolean> = _isBrightnessControlVisible

    private val _brightnessLevel = MutableLiveData(50)
    val brightnessLevel: LiveData<Int> = _brightnessLevel

    // Functions to change state
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
            // Auto turn off flash when switching to back camera
            _isFlashEnabled.value = false
            CameraSelector.LENS_FACING_BACK
        }
    }

    fun toggleGrid() {
        _isGridVisible.value = _isGridVisible.value != true
    }

    fun toggleTimerRatioContainer() {
        _isTimerRatioContainerVisible.value = !_isTimerRatioContainerVisible.value!!
        if (_isTimerRatioContainerVisible.value == true) {
            _isBrightnessControlVisible.value = false
        }
    }

    fun setTimerSeconds(seconds: Int) {
        _timerSeconds.value = seconds
    }

    fun toggleBrightnessControl() {
        _isBrightnessControlVisible.value = !_isBrightnessControlVisible.value!!
        if (_isBrightnessControlVisible.value == true) {
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
        if (_isBrightnessControlVisible.value == true) {
            _isBrightnessControlVisible.value = false
        }
        if (_isTimerRatioContainerVisible.value == true) {
            _isTimerRatioContainerVisible.value = false
        }
    }
}
