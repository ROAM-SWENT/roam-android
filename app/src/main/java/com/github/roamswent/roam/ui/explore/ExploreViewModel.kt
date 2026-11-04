/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.model.location.LocationProvider
import com.github.roamswent.roam.model.permission.LocationPermissionDecision
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ExploreUiState {
  data class PermissionRequired(val decision: LocationPermissionDecision) : ExploreUiState

  data object Loading : ExploreUiState

  data object LocationUnavailable : ExploreUiState

  data class LocationAvailable(val coordinates: Coordinates) : ExploreUiState
}

/** Loads the user's current coordinates and exposes permission and retrieval state to the UI. */
class ExploreViewModel(private val locationProvider: LocationProvider) : ViewModel() {
  private val _uiState =
      MutableStateFlow<ExploreUiState>(
          ExploreUiState.PermissionRequired(LocationPermissionDecision.NOT_ASKED)
      )
  val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

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
}
