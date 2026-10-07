package com.github.roamswent.roam.ui.scan

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.roamswent.roam.repository.CaptureSender
import com.github.roamswent.roam.repository.CapturedImageManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ScanReviewUiState {
  data class Reviewing(val uri: Uri) : ScanReviewUiState

  data class ConfirmRetake(val uri: Uri) : ScanReviewUiState

  data class Deleting(val uri: Uri) : ScanReviewUiState

  data class Sending(val uri: Uri) : ScanReviewUiState
}

sealed interface ScanReviewNavigationEvent {
  data object BackToCapture : ScanReviewNavigationEvent
}

class ScanReviewViewModel(
    private val capturedImageManager: CapturedImageManager,
    private val captureSender: CaptureSender,
    private val dispatcher: CoroutineDispatcher,
) : ViewModel() {
  private val _state = MutableStateFlow<ScanReviewUiState?>(null)
  val state: StateFlow<ScanReviewUiState?> = _state.asStateFlow()

  private val _navigationEvents =
      MutableSharedFlow<ScanReviewNavigationEvent>(extraBufferCapacity = 1)
  val navigationEvents: SharedFlow<ScanReviewNavigationEvent> = _navigationEvents.asSharedFlow()

  fun setUri(uri: Uri) {
    if (_state.value == null) {
      _state.value = ScanReviewUiState.Reviewing(uri)
    }
  }

  fun onRetake() {
    val currentState = _state.value
    if (currentState is ScanReviewUiState.Reviewing) {
      _state.value = ScanReviewUiState.ConfirmRetake(currentState.uri)
    }
  }

  fun confirmRetake() {
    val currentState = _state.value
    if (currentState !is ScanReviewUiState.ConfirmRetake) {
      return
    }
    _state.value = ScanReviewUiState.Deleting(currentState.uri)
    viewModelScope.launch {
      try {
        withContext(dispatcher) { capturedImageManager.deleteCapture(currentState.uri) }
      } catch (exception: Throwable) {
        Log.e(TAG, "Failed to delete captured image", exception)
      }
      _navigationEvents.emit(ScanReviewNavigationEvent.BackToCapture)
    }
  }

  fun cancelRetake() {
    val currentState = _state.value
    if (currentState is ScanReviewUiState.ConfirmRetake) {
      _state.value = ScanReviewUiState.Reviewing(currentState.uri)
    }
  }

  fun onSend() {
    val currentState = _state.value
    if (currentState !is ScanReviewUiState.Reviewing) {
      return
    }
    _state.value = ScanReviewUiState.Sending(currentState.uri)
    viewModelScope.launch {
      val result =
          try {
            withContext(dispatcher) { captureSender.send(currentState.uri) }
          } catch (exception: Throwable) {
            Result.failure(exception)
          }
      result.onFailure { exception ->
        Log.e(TAG, "Failed to send captured image", exception)
        _state.value = ScanReviewUiState.Reviewing(currentState.uri)
      }
    }
  }

  private companion object {
    const val TAG = "ScanReviewViewModel"
  }
}
