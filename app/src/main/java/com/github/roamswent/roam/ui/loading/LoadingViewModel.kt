// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.ui.loading

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.ai.BlockedException
import com.github.roamswent.roam.model.ai.ConfigurationException
import com.github.roamswent.roam.model.ai.DiscoveryRequest
import com.github.roamswent.roam.model.ai.ImagePayload
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.ai.MonumentGuideRepository
import com.github.roamswent.roam.model.ai.MonumentNotIdentifiedException
import com.github.roamswent.roam.model.ai.NetworkUnavailableException
import com.github.roamswent.roam.model.ai.ParseException
import com.github.roamswent.roam.model.ai.RejectedImageException
import com.github.roamswent.roam.model.ai.TransientException
import com.github.roamswent.roam.model.ai.UserPreferences
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

sealed interface LoadingUiState {
  data object Loading : LoadingUiState

  data class Error(@StringRes val messageResId: Int) : LoadingUiState
}

sealed interface LoadingNavigationEvent {
  data class NavigateToMonumentDetail(val guide: MonumentGuide, val imageUri: String) :
      LoadingNavigationEvent

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
  private val navigationClaimed = AtomicBoolean(false)

  private val discoveryJob = viewModelScope.launch {
    try {
      val image = readImage(imageUri)
      val guide =
          withTimeout(DISCOVERY_TIMEOUT_MILLIS) {
            repository.guideFor(
                DiscoveryRequest(
                    image = image,
                    location = null,
                    preferences = UserPreferences(language),
                )
            )
          }
      if (navigationClaimed.compareAndSet(false, true)) {
        _navigationEvents.trySend(
            LoadingNavigationEvent.NavigateToMonumentDetail(guide, imageUri.toString())
        )
      }
    } catch (exception: TimeoutCancellationException) {
      _uiState.value = LoadingUiState.Error(R.string.loading_error_timeout)
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      _uiState.value = LoadingUiState.Error(userMessageResId(exception))
    }
  }

  fun onCancel() {
    if (navigationClaimed.compareAndSet(false, true)) {
      discoveryJob.cancel()
      _navigationEvents.trySend(LoadingNavigationEvent.NavigateHome)
    }
  }

  private companion object {
    const val DISCOVERY_TIMEOUT_MILLIS = 60_000L
  }

  private fun userMessageResId(exception: Exception): Int =
      when (exception) {
        is NetworkUnavailableException -> R.string.loading_error_network
        is RejectedImageException -> R.string.loading_error_rejected_image
        is MonumentNotIdentifiedException -> R.string.loading_error_not_identified
        is BlockedException -> R.string.loading_error_blocked
        is ParseException -> R.string.loading_error_parse
        is TransientException -> R.string.loading_error_transient
        is ConfigurationException -> R.string.loading_error_configuration
        is IOException -> R.string.loading_error_read_image
        else -> R.string.loading_error_generic
      }
}
