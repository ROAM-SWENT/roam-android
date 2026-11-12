/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 * Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
 */

package com.github.roamswent.roam.ui.explore

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.roamswent.roam.model.guide.SavedGuideRepository
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.model.location.LocationProvider
import com.github.roamswent.roam.model.permission.LocationPermissionDecision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ExploreUiState {
  data class PermissionRequired(val decision: LocationPermissionDecision) : ExploreUiState

  data object Loading : ExploreUiState

  data object LocationUnavailable : ExploreUiState

  data class LocationAvailable(val coordinates: Coordinates) : ExploreUiState
}

/**
 * Loads the user's current coordinates and exposes permission and retrieval state to the UI through
 * [uiState], and the saved monuments to pin on the map through [monuments].
 */
class ExploreViewModel(
    private val locationProvider: LocationProvider,
    savedGuideRepository: SavedGuideRepository,
) : ViewModel() {
  private val _uiState =
      MutableStateFlow<ExploreUiState>(
          ExploreUiState.PermissionRequired(LocationPermissionDecision.NOT_ASKED)
      )
  val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

  /**
   * Saved guides that have a location, kept up to date with the repository while observed. Starts
   * empty; if observation fails, the failure is logged and the last received monuments stay on the
   * map. Cancellation is rethrown so it is never swallowed.
   */
  val monuments: StateFlow<List<ExploreMonument>> =
      savedGuideRepository
          .observeSavedGuides()
          .map { guides -> guides.toExploreMonuments() }
          .distinctUntilChanged()
          .catch { exception ->
            if (exception is CancellationException) throw exception
            Log.e(TAG, "Failed to observe saved monuments", exception)
          }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

  private var permissionGranted = false
  private var locationRequest: Job? = null
  private var requestGeneration = 0

  /** Receives the latest permission snapshot from the UI permission controller. */
  fun onPermissionStateChanged(
      isGranted: Boolean,
      decision: LocationPermissionDecision,
  ) {
    if (!isGranted) {
      permissionGranted = false
      requestGeneration++
      locationRequest?.cancel()
      locationRequest = null
      _uiState.value = ExploreUiState.PermissionRequired(decision)
      return
    }

    if (!permissionGranted) {
      permissionGranted = true
      loadLocation()
    }
  }

  /** Retries the location lookup when permission is currently granted. */
  fun retryLocation() {
    if (permissionGranted) loadLocation()
  }

  private fun loadLocation() {
    val generation = ++requestGeneration
    locationRequest?.cancel()
    _uiState.value = ExploreUiState.Loading
    locationRequest = viewModelScope.launch {
      val coordinates = locationProvider.currentCoordinates()
      if (permissionGranted && generation == requestGeneration) {
        _uiState.value =
            coordinates?.let { ExploreUiState.LocationAvailable(it) }
                ?: ExploreUiState.LocationUnavailable
      }
    }
  }

  private companion object {
    const val TAG = "ExploreViewModel"

    /** Keeps observing across short interruptions such as configuration changes. */
    const val STOP_TIMEOUT_MILLIS = 5_000L
  }
}
