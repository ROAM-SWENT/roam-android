// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

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
import com.github.roamswent.roam.model.guide.SavedGuideRepository
import com.github.roamswent.roam.model.guide.toSavedGuide
import com.github.roamswent.roam.model.interests.UserInterestsRepository
import com.github.roamswent.roam.model.location.Coordinates
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
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

/**
 * Explore opt-in for the guide saved by the last discovery, offered only when the scan has a
 * [scanLocation]. [savedLocation] is the location requested for the saved guide (`null` keeps it
 * off Explore); it is rolled back to the last confirmed value if the update fails, with [error].
 */
data class ExploreOptInUiState(
    val scanLocation: Coordinates,
    val savedLocation: Coordinates? = null,
    val error: Exception? = null,
) {
  val isShownInExplore: Boolean
    get() = savedLocation != null
}

sealed interface LoadingNavigationEvent {
  data class NavigateToMonumentDetail(val guide: MonumentGuide, val imageUri: String) :
      LoadingNavigationEvent

  data object NavigateHome : LoadingNavigationEvent
}

/**
 * Runs the discovery for [imageUri]. Every successful discovery is also saved to
 * [savedGuideRepository] under an id from [newGuideId], with the photo the pipeline sent to the AI
 * and [clock] as discovery date. It is saved without location, so it is not on Explore until the
 * user opts in with [setShownInExplore].
 */
class LoadingViewModel(
    private val repository: MonumentGuideRepository,
    private val readImage: suspend (Uri) -> ImagePayload,
    private val imageUri: Uri,
    private val language: String,
    private val userInterestsRepository: UserInterestsRepository,
    private val savedGuideRepository: SavedGuideRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newGuideId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
  private val _showLoading = MutableStateFlow(false)
  val showLoading: StateFlow<Boolean> = _showLoading.asStateFlow()

  private val _showInterestsPrompt = MutableStateFlow(false)
  val showInterestsPrompt: StateFlow<Boolean> = _showInterestsPrompt.asStateFlow()

  private val _discoveredGuide = MutableStateFlow<MonumentGuide?>(null)
  val discoveredGuide: StateFlow<MonumentGuide?> = _discoveredGuide.asStateFlow()

  private val _saveError = MutableStateFlow<Exception?>(null)
  /** Failure from the most recent attempt to save a discovered guide to the collection. */
  val saveError: StateFlow<Exception?> = _saveError.asStateFlow()

  private val _exploreOptIn = MutableStateFlow<ExploreOptInUiState?>(null)
  /** Explore opt-in of the saved guide, or `null` when it is not offered. */
  val exploreOptIn: StateFlow<ExploreOptInUiState?> = _exploreOptIn.asStateFlow()
  private var savedGuideId: String? = null
  private var confirmedLocation: Coordinates? = null
  private var lastLocationRequest = 0L
  /**
   * Request that set [confirmedLocation]; an older request finishing later must not override it.
   */
  private var confirmedLocationRequest = 0L

  private val _uiState = MutableStateFlow<LoadingUiState>(LoadingUiState.Loading)
  val uiState: StateFlow<LoadingUiState> = _uiState.asStateFlow()

  private val _navigationEvents = Channel<LoadingNavigationEvent>(Channel.BUFFERED)
  val navigationEvents: Flow<LoadingNavigationEvent> = _navigationEvents.receiveAsFlow()
  private val navigationClaimed = AtomicBoolean(false)
  private var discoveryStarted = false
  private var navigationFinished = false
  private var discoveryJob: Job? = null

  fun beginLoading() {
    if (_showInterestsPrompt.value) {
      return
    }
    if (userInterestsRepository.getUserInterests().hasBeenAsked) {
      continueLoading()
    } else {
      _showInterestsPrompt.value = true
    }
  }

  fun onSaveInterests(interests: List<String>) {
    if (!_showInterestsPrompt.value) {
      return
    }
    userInterestsRepository.saveInterests(interests)
    _showInterestsPrompt.value = false
    continueLoading()
  }

  fun onSkipInterests() {
    if (!_showInterestsPrompt.value) {
      return
    }
    userInterestsRepository.skipInterests()
    _showInterestsPrompt.value = false
    continueLoading()
  }

  private fun continueLoading() {
    _showLoading.value = true
  }

  fun onDiscoverySuccess(guide: MonumentGuide) {
    _discoveredGuide.value = guide
    _showLoading.value = false
    navigationFinished = true
    resetDiscovery()
  }

  fun startDiscovery() {
    discoveryJob?.cancel()
    resetDiscovery()
    navigationFinished = false
    discoveryStarted = true
    discoveryJob = viewModelScope.launch {
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
          saveToCollection(guide)
          _navigationEvents.trySend(
              LoadingNavigationEvent.NavigateToMonumentDetail(guide, imageUri.toString())
          )
          navigationFinished = true
          resetDiscovery()
        }
      } catch (exception: TimeoutCancellationException) {
        _uiState.value = LoadingUiState.Error(R.string.loading_error_timeout)
        resetDiscovery()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.value = LoadingUiState.Error(userMessageResId(exception))
        resetDiscovery()
      }
    }
  }

  /**
   * Saves [guide] in its own coroutine: [discoveryJob] is cancelled as soon as the guide is shown,
   * and a failed save must not prevent showing it. The coroutine starts undispatched, so the write
   * is issued before the guide is shown: leaving the screen then only cancels waiting for the
   * repository's confirmation, not the write itself.
   */
  private fun saveToCollection(guide: MonumentGuide) {
    val discoveryDate = clock()
    // The id is chosen here, not by the repository, so later location updates target this guide
    // without waiting for the save to be acknowledged, which never happens while offline.
    val guideId = newGuideId()
    savedGuideId = guideId
    confirmedLocation = null
    // Results of requests made for a previous guide are now stale.
    confirmedLocationRequest = ++lastLocationRequest
    _exploreOptIn.value = guide.location?.let { ExploreOptInUiState(scanLocation = it) }
    viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
      try {
        _saveError.value = null
        val photo = checkNotNull(guide.photo) { "The discovery returned no photo to save" }
        savedGuideRepository.addGuide(
            guide.toSavedGuide(guideId, photo.bytes, discoveryDate, location = null)
        )
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _saveError.value = exception
        // Without a saved guide there is nothing to put on Explore.
        if (savedGuideId == guideId) _exploreOptIn.value = null
      }
    }
  }

  /**
   * Puts the saved guide on Explore at the scan location, or takes it off, by updating only its
   * location. The switch reflects the request at once; a failure restores the last confirmed value.
   * Like the save, the update starts undispatched so it is issued even if the user leaves at once.
   */
  fun setShownInExplore(shown: Boolean) {
    val guideId = savedGuideId ?: return
    val current = _exploreOptIn.value ?: return
    val location = if (shown) current.scanLocation else null
    if (current.savedLocation == location) return
    val request = ++lastLocationRequest
    _exploreOptIn.value = current.copy(savedLocation = location, error = null)
    viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
      try {
        savedGuideRepository.setLocation(guideId, location)
        if (request > confirmedLocationRequest) {
          confirmedLocationRequest = request
          confirmedLocation = location
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        // A newer request decides the final value, so only the latest one reports an error.
        if (request == lastLocationRequest) {
          _exploreOptIn.value =
              _exploreOptIn.value?.copy(savedLocation = confirmedLocation, error = exception)
        }
      }
    }
  }

  fun onCancel() {
    if (navigationFinished || !navigationClaimed.compareAndSet(false, true)) {
      return
    }
    navigationFinished = true
    _showLoading.value = false
    resetDiscovery()
    _navigationEvents.trySend(LoadingNavigationEvent.NavigateHome)
  }

  private fun resetDiscovery() {
    discoveryStarted = false
    navigationClaimed.set(false)
    discoveryJob?.cancel()
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
