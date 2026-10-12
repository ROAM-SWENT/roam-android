package com.github.roamswent.roam.ui.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.roamswent.roam.repository.CapturedImageManager
import com.github.roamswent.roam.ui.camera.CameraController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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
    cameraController: CameraController,
    private val dispatcher: CoroutineDispatcher,
) : ViewModel() {
  private var cameraController = cameraController

  private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Streaming)
  val state: StateFlow<ScanUiState> = _state.asStateFlow()

  private val _navigationEvents = Channel<ScanNavigationEvent>(Channel.BUFFERED)
  val navigationEvents: Flow<ScanNavigationEvent> = _navigationEvents.receiveAsFlow()

  private var captureGeneration = 0

  fun onCapture() {
    if (_state.value != ScanUiState.Streaming) {
      return
    }
    _state.value = ScanUiState.Capturing
    val generation = ++captureGeneration
    viewModelScope.launch {
      val uri =
          try {
            withContext(dispatcher) { capturedImageManager.newCaptureUri() }
          } catch (_: Throwable) {
            if (generation == captureGeneration) {
              _state.value = ScanUiState.Streaming
            }
            return@launch
          }
      if (generation != captureGeneration) {
        return@launch
      }
      cameraController.captureTo(
          uri = uri,
          onSaved = {
            if (generation == captureGeneration) {
              _state.value = ScanUiState.Captured
              viewModelScope.launch {
                _navigationEvents.send(ScanNavigationEvent.NavigateToReview(uri))
              }
            }
          },
          onError = {
            if (generation == captureGeneration) {
              viewModelScope.launch { _state.value = ScanUiState.Streaming }
            }
          },
      )
    }
  }

  fun onPermissionRevoked() {
    captureGeneration++
    if (_state.value != ScanUiState.Revoked) {
      cameraController.stop()
      _state.value = ScanUiState.Revoked
    }
  }

  fun onCameraActive(cameraController: CameraController) {
    this.cameraController = cameraController
    if (_state.value == ScanUiState.Captured) {
      _state.value = ScanUiState.Streaming
    }
  }
}
