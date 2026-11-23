// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

package com.github.roamswent.roam.ui.loading

import android.net.Uri
import android.util.Log
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
import com.github.roamswent.roam.model.leaderboard.LeaderboardRepository
import com.github.roamswent.roam.model.leaderboard.MonumentKey
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.model.location.ProximityPolicy
import com.github.roamswent.roam.model.location.ProximityResult
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
 * Explore opt-in for the guide saved by the last discovery, offered only when the observed
 * [scanLocation] is near the monument. [savedLocation] is the location requested for the saved
 * guide (`null` keeps it off Explore); it is rolled back to the last confirmed value if the update
 * fails, with [error]. The pin is always the observed position, never the monument's own one.
 */
data class ExploreOptInUiState(
    val scanLocation: Coordinates,
    val savedLocation: Coordinates? = null,
    val error: Exception? = null,
) {
  val isShownInExplore: Boolean
    get() = savedLocation != null
}

/** Why the saved guide cannot be put on Explore, explained in place of the Explore switch. */
enum class ExploreUnavailableReason {
  /**
   * No observed position: for a camera scan, location permission denied, GPS off, or no fix in
   * time; for a gallery photo, no usable GPS in its metadata.
   */
  NO_OBSERVED_LOCATION,

  /** The AI gave no trustworthy position for the monument. */
  NO_MONUMENT_LOCATION,

  /** The observed position is farther than [ProximityPolicy.MAX_DISTANCE_METERS] from it. */
  TOO_FAR,
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
 * user opts in with [setShownInExplore]. It can also be added to favourites with [toggleFavorite].
 *
 * Once the save succeeds, a verified visit, one whose observed position is near the monument's own,
 * is recorded in [leaderboardRepository], which counts each monument only once.
 */
class LoadingViewModel(
    private val repository: MonumentGuideRepository,
    private val readImage: suspend (Uri) -> ImagePayload,
    private val imageUri: Uri,
    private val language: String,
    private val userInterestsRepository: UserInterestsRepository,
    private val savedGuideRepository: SavedGuideRepository,
    private val leaderboardRepository: LeaderboardRepository,
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

  private val _verifiedMonumentError = MutableStateFlow<Exception?>(null)
  /**
   * Failure from recording the verified visit of the saved guide on the leaderboard, or `null`. The
   * guide stays saved and the rest of the screen is unaffected; this is not shown in the UI yet.
   */
  val verifiedMonumentError: StateFlow<Exception?> = _verifiedMonumentError.asStateFlow()

  private val _isFavorite = MutableStateFlow(false)
  /** Whether the guide saved from this discovery is a favourite, updated optimistically. */
  val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

  private val _favoriteError = MutableStateFlow<Exception?>(null)
  /** Failure from the latest favourite change, cleared by [clearFavoriteError]. */
  val favoriteError: StateFlow<Exception?> = _favoriteError.asStateFlow()

  private val _exploreOptIn = MutableStateFlow<ExploreOptInUiState?>(null)
  /** Explore opt-in of the saved guide, or `null` when it is not offered. */
  val exploreOptIn: StateFlow<ExploreOptInUiState?> = _exploreOptIn.asStateFlow()

  private val _exploreUnavailableReason = MutableStateFlow<ExploreUnavailableReason?>(null)
  /**
   * Why Explore is not offered for the saved guide, or `null`. At most one of this and
   * [exploreOptIn] is set at any time.
   */
  val exploreUnavailableReason: StateFlow<ExploreUnavailableReason?> =
      _exploreUnavailableReason.asStateFlow()

  /** ID of the guide saved from this discovery, or null while there is none to update. */
  private var savedGuideId: String? = null
  /** Favourite value the repository last confirmed, restored when the latest write fails. */
  private var confirmedFavorite = false
  /** Increases with every favourite request so only the latest one may roll back or report. */
  private var lastFavoriteRequest = 0L
  private var confirmedLocation: Coordinates? = null
  private var lastLocationRequest = 0L
  /**
   * Request that set [confirmedLocation]; an older request finishing later must not override it.
   */
  private var confirmedLocationRequest = 0L
  /**
   * Whether the latest request failed, so the switch shows [confirmedLocation]. An older request
   * still in flight can then succeed and become the stored value, which the switch must follow.
   */
  private var latestLocationRequestFailed = false

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
   *
   * The id is generated here rather than by the repository, so the guide can be added to favourites
   * before the save is confirmed, which only happens once back online.
   *
   * A verified visit is recorded only after the save is confirmed, in the same coroutine, so a
   * failed save never counts. Offline, the save, and so the recording, waits for the connection; if
   * the user leaves the screen first, the guide is still saved but the visit is not counted.
   */
  private fun saveToCollection(guide: MonumentGuide) {
    val discoveryDate = clock()
    // The id is chosen here, not by the repository, so later favourite and location updates target
    // this guide without waiting for the save to be acknowledged, which never happens while
    // offline.
    val guideId = newGuideId()
    savedGuideId = guideId
    confirmedFavorite = false
    _isFavorite.value = false
    _favoriteError.value = null
    confirmedLocation = null
    // Results of requests made for a previous guide are now stale.
    confirmedLocationRequest = ++lastLocationRequest
    latestLocationRequestFailed = false
    // One proximity check decides both the Explore offer and whether the visit is verified.
    val proximity = ProximityPolicy.check(guide.location, guide.monumentLocation)
    offerExplore(guide, proximity)
    // The key comes from the monument's own position, never from where the user was. Near is only
    // returned for a known monument position.
    val verifiedMonument =
        if (proximity == ProximityResult.Near) MonumentKey.of(checkNotNull(guide.monumentLocation))
        else null
    viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
      val saved =
          try {
            _saveError.value = null
            _verifiedMonumentError.value = null
            val photo = checkNotNull(guide.photo) { "The discovery returned no photo to save" }
            savedGuideRepository.addGuide(
                guide.toSavedGuide(guideId, photo.bytes, discoveryDate, location = null)
            )
            true
          } catch (exception: CancellationException) {
            throw exception
          } catch (exception: Exception) {
            _saveError.value = exception
            if (savedGuideId == guideId) {
              // Without a saved guide there is nothing to favourite or put on Explore.
              savedGuideId = null
              _exploreOptIn.value = null
              _exploreUnavailableReason.value = null
            }
            false
          }
      if (saved && verifiedMonument != null) recordVerifiedMonument(verifiedMonument)
    }
  }

  /**
   * Records [monument] on the leaderboard. A failure is kept in [verifiedMonumentError] and logged,
   * but never undoes the save nor changes the Explore or navigation state.
   */
  private suspend fun recordVerifiedMonument(monument: MonumentKey) {
    try {
      leaderboardRepository.recordVerifiedMonument(monument)
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      Log.w(TAG, "The verified monument could not be recorded on the leaderboard.", exception)
      _verifiedMonumentError.value = exception
    }
  }

  /**
   * Offers the Explore switch for [guide] only when its observed position is near the monument's
   * own position, as [proximity] says, otherwise exposes why not. It does not affect saving to the
   * collection. The state being replaced is cleared first, so the switch and the reason are never
   * both set.
   */
  private fun offerExplore(guide: MonumentGuide, proximity: ProximityResult) {
    val observed = guide.location
    when (proximity) {
      ProximityResult.Near -> {
        _exploreUnavailableReason.value = null
        // Near is only returned for a known observed position.
        _exploreOptIn.value = ExploreOptInUiState(scanLocation = checkNotNull(observed))
      }
      ProximityResult.ObservedLocationMissing ->
          showExploreUnavailable(ExploreUnavailableReason.NO_OBSERVED_LOCATION)
      ProximityResult.MonumentLocationMissing ->
          showExploreUnavailable(ExploreUnavailableReason.NO_MONUMENT_LOCATION)
      is ProximityResult.TooFar -> showExploreUnavailable(ExploreUnavailableReason.TOO_FAR)
    }
  }

  private fun showExploreUnavailable(reason: ExploreUnavailableReason) {
    _exploreOptIn.value = null
    _exploreUnavailableReason.value = reason
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
    latestLocationRequestFailed = false
    _exploreOptIn.value = current.copy(savedLocation = location, error = null)
    // Every update is issued at once rather than queued behind the previous one: a queued update
    // would be lost if the user left meanwhile, and offline the previous one never completes.
    viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
      try {
        savedGuideRepository.setLocation(guideId, location)
        if (request > confirmedLocationRequest) {
          confirmedLocationRequest = request
          confirmedLocation = location
          // The latest request failed, so this one is what stays stored: show it.
          if (latestLocationRequestFailed) {
            _exploreOptIn.value = _exploreOptIn.value?.copy(savedLocation = location)
          }
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        // A newer request decides the final value, so only the latest one reports an error.
        if (request == lastLocationRequest) {
          latestLocationRequestFailed = true
          _exploreOptIn.value =
              _exploreOptIn.value?.copy(savedLocation = confirmedLocation, error = exception)
        }
      }
    }
  }

  /**
   * Adds the saved guide to favourites, or removes it, immediately in the UI while the repository
   * synchronizes the change. Does nothing while no guide is saved. Like [saveToCollection], the
   * write starts undispatched so leaving the screen right after the tap does not drop it.
   */
  fun toggleFavorite() {
    val guideId = savedGuideId ?: return
    val isFavorite = !_isFavorite.value
    val request = ++lastFavoriteRequest
    _isFavorite.value = isFavorite
    _favoriteError.value = null
    viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
      try {
        savedGuideRepository.setFavorite(guideId, isFavorite)
        // Writes to the same guide are applied in order, so this value is now the stored one.
        if (savedGuideId == guideId) confirmedFavorite = isFavorite
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        // A newer request decides the final value, so only the latest one rolls back and reports.
        if (request == lastFavoriteRequest && savedGuideId == guideId) {
          _isFavorite.value = confirmedFavorite
          _favoriteError.value = exception
        }
      }
    }
  }

  /** Clears [favoriteError] once the UI has shown it. */
  fun clearFavoriteError() {
    _favoriteError.value = null
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
    const val TAG = "LoadingViewModel"
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
