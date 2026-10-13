package com.github.roamswent.roam.ui.scan

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.roamswent.roam.model.ai.BlockedException
import com.github.roamswent.roam.model.ai.ConfigurationException
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.ai.MonumentNotIdentifiedException
import com.github.roamswent.roam.model.ai.NetworkUnavailableException
import com.github.roamswent.roam.model.ai.ParseException
import com.github.roamswent.roam.model.ai.RejectedImageException
import com.github.roamswent.roam.model.ai.TransientException
import com.github.roamswent.roam.repository.CaptureSender
import com.github.roamswent.roam.repository.CapturedImageManager
import kotlinx.coroutines.CancellationException
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

  data class DeleteFailed(val uri: Uri) : ScanReviewUiState

  data class Sending(val uri: Uri) : ScanReviewUiState

  data class Sent(val uri: Uri, val guide: MonumentGuide) : ScanReviewUiState

  data class SendFailed(val uri: Uri, val message: String) : ScanReviewUiState
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
    deleteCapture(currentState.uri)
  }

  private fun deleteCapture(uri: Uri) {
    _state.value = ScanReviewUiState.Deleting(uri)
    viewModelScope.launch {
      val deleted =
          try {
            withContext(dispatcher) { capturedImageManager.deleteCapture(uri) }
          } catch (e: CancellationException) {
            throw e
          } catch (exception: Exception) {
            Log.e(TAG, "Failed to delete captured image", exception)
            false
          }
      if (deleted) {
        _navigationEvents.emit(ScanReviewNavigationEvent.BackToCapture)
      } else {
        _state.value = ScanReviewUiState.DeleteFailed(uri)
      }
    }
  }

  fun onRetryDelete() {
    val currentState = _state.value
    if (currentState is ScanReviewUiState.DeleteFailed) {
      deleteCapture(currentState.uri)
    }
  }

  fun onDismissDeleteError() {
    val currentState = _state.value
    if (currentState is ScanReviewUiState.DeleteFailed) {
      _state.value = ScanReviewUiState.Reviewing(currentState.uri)
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
    sendCapture(currentState.uri)
  }

  private fun sendCapture(uri: Uri) {
    _state.value = ScanReviewUiState.Sending(uri)
    viewModelScope.launch {
      val result =
          try {
            withContext(dispatcher) { captureSender.send(uri) }
          } catch (e: CancellationException) {
            throw e
          } catch (exception: Exception) {
            Result.failure(exception)
          }
      result.fold(
          onSuccess = { guide -> _state.value = ScanReviewUiState.Sent(uri, guide) },
          onFailure = { exception ->
            Log.e(TAG, "Failed to send captured image", exception)
            _state.value = ScanReviewUiState.SendFailed(uri, userMessage(exception))
          },
      )
    }
  }

  fun onRetrySend() {
    val currentState = _state.value
    if (currentState is ScanReviewUiState.SendFailed) {
      sendCapture(currentState.uri)
    }
  }

  fun onDismissSendError() {
    val currentState = _state.value
    if (currentState is ScanReviewUiState.SendFailed) {
      _state.value = ScanReviewUiState.Reviewing(currentState.uri)
    }
  }

  private fun userMessage(error: Throwable): String =
      when (error) {
        is MonumentNotIdentifiedException -> "No monument was recognized in this photo."
        is RejectedImageException -> "This photo couldn't be used. Try retaking it."
        is BlockedException -> "This request was blocked by the safety filter."
        is NetworkUnavailableException -> "No internet connection. Check your connection and retry."
        is TransientException -> "The service is busy right now. Please try again."
        is ParseException -> "The guide couldn't be generated. Please try again."
        is ConfigurationException -> "The service isn't configured correctly."
        else -> "Something went wrong. Please try again."
      }

  private companion object {
    const val TAG = "ScanReviewViewModel"
  }
}
