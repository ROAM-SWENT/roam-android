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
import kotlinx.coroutines.Job
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

  data object NavigateHome : LoadingNavigationEvent

  data class NavigateHomeWithError(val message: String) : LoadingNavigationEvent
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

  private var discoveryJob: Job? = null
  private var hasNavigated = false

  init {
    discoverMonument()
  }

  private fun discoverMonument() {
    discoveryJob = viewModelScope.launch {
      try {
        val image = readImage(imageUri)
        val request =
            DiscoveryRequest(
                image = image,
                location = null,
                preferences = UserPreferences(language),
            )
        val guide = repository.guideFor(request)
        if (!hasNavigated) {
          hasNavigated = true
          _navigationEvents.send(
              LoadingNavigationEvent.NavigateToMonumentDetail(guide, imageUri.toString())
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        if (!hasNavigated) {
          val message = e.message ?: "Unable to identify the monument"
          hasNavigated = true
          _uiState.value = LoadingUiState.Error(message)
          _navigationEvents.send(LoadingNavigationEvent.NavigateHomeWithError(message))
        }
      }
    }
  }

  fun onCancel() {
    if (hasNavigated) return
    hasNavigated = true
    discoveryJob?.cancel()
    _navigationEvents.trySend(LoadingNavigationEvent.NavigateHome)
  }
}
