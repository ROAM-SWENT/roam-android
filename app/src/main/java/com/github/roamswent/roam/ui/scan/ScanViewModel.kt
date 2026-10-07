package com.github.roamswent.roam.ui.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.roamswent.roam.repository.CapturedImageManager
import com.github.roamswent.roam.ui.camera.CameraController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ScanUiState {
  data object Streaming : ScanUiState

  data object Capturing : ScanUiState

  data object Captured : ScanUiState

  data object Revoked : ScanUiState
}

sealed interface ScanNavigationEvent {
  data class NavigateToReview(val uri: Uri) : ScanNavigationEvent
}

class ScanViewModel(
    private val capturedImageManager: CapturedImageManager,
    private val cameraController: CameraController,
    private val dispatcher: CoroutineDispatcher,
) : ViewModel() {
  private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Streaming)
  val state: StateFlow<ScanUiState> = _state.asStateFlow()

  private val _navigationEvents = MutableSharedFlow<ScanNavigationEvent>(extraBufferCapacity = 1)
  val navigationEvents: SharedFlow<ScanNavigationEvent> = _navigationEvents.asSharedFlow()

  fun onCapture() {
    if (_state.value != ScanUiState.Streaming) {
      return
    }
    _state.value = ScanUiState.Capturing
    viewModelScope.launch {
      val uri =
          try {
            withContext(dispatcher) { capturedImageManager.newCaptureUri() }
          } catch (_: Throwable) {
            _state.value = ScanUiState.Streaming
            return@launch
          }
      cameraController.captureTo(
          uri = uri,
          onSaved = {
            _state.value = ScanUiState.Captured
            viewModelScope.launch {
              _navigationEvents.emit(ScanNavigationEvent.NavigateToReview(uri))
            }
          },
          onError = { viewModelScope.launch { _state.value = ScanUiState.Streaming } },
      )
    }
  }

  fun onPermissionRevoked() {
    if (_state.value != ScanUiState.Revoked) {
      cameraController.stop()
      _state.value = ScanUiState.Revoked
    }
  }
}
