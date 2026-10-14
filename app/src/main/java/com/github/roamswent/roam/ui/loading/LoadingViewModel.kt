// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.ui.loading

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.roamswent.roam.model.ai.DiscoveryRequest
import com.github.roamswent.roam.model.ai.ImagePayload
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.ai.MonumentGuideRepository
import com.github.roamswent.roam.model.ai.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface LoadingUiState {
  data object Loading : LoadingUiState

  data class Error(val message: String) : LoadingUiState
}

sealed interface LoadingNavigationEvent {
  data class NavigateToMonumentDetail(val guide: MonumentGuide, val imageUri: String) :
      LoadingNavigationEvent

  data class NavigateHomeWithError(val message: String) : LoadingNavigationEvent

  data object NavigateHome : LoadingNavigationEvent
}

class LoadingViewModel(
    private val repository: MonumentGuideRepository,
    private val readImage: suspend (Uri) -> ImagePayload,
    private val imageUri: Uri,
    private val language: String,
) : ViewModel() {
  private val _uiState = MutableStateFlow<LoadingUiState>(LoadingUiState.Loading)
  val uiState: StateFlow<LoadingUiState> = _uiState.asStateFlow()

  private val _navigationEvents = Channel<LoadingNavigationEvent>(Channel.BUFFERED)
  val navigationEvents: Flow<LoadingNavigationEvent> = _navigationEvents.receiveAsFlow()

  private val discoveryJob = viewModelScope.launch {
    try {
      val image = readImage(imageUri)
      val guide =
          repository.guideFor(
              DiscoveryRequest(
                  image = image,
                  location = null,
                  preferences = UserPreferences(language),
              )
          )
      _navigationEvents.send(
          LoadingNavigationEvent.NavigateToMonumentDetail(guide, imageUri.toString())
      )
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      val message = exception.message ?: "Something went wrong."
      _uiState.value = LoadingUiState.Error(message)
      _navigationEvents.send(LoadingNavigationEvent.NavigateHomeWithError(message))
    }
  }

  fun onCancel() {
    discoveryJob.cancel()
    _navigationEvents.trySend(LoadingNavigationEvent.NavigateHome).getOrThrow()
  }
}
