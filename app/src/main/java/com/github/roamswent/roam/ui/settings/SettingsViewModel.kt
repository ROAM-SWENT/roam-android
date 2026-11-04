/*
 * Portions of this code were generated with the help of GitHub Copilot.
 * Portions of this code were adapted from the bootcamp solution provided for SWENT.
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.settings

import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.roamswent.roam.model.authentication.AuthRepository
import com.github.roamswent.roam.model.authentication.AuthRepositoryFirebase
import com.github.roamswent.roam.model.interests.UserInterestsRepository
import com.github.roamswent.roam.model.profile.ProfileRepository
import com.github.roamswent.roam.model.profile.ProfileRepositoryFirebase
import com.github.roamswent.roam.model.profile.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUIState(
    val errorMsg: String? = null,
    val signedOut: Boolean = false,
    val cameraPermissionGranted: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val profileLoading: Boolean = false,
    val profile: UserProfile? = null,
    val interests: List<String> = emptyList(),
)

class SettingsViewModel(
    private val authRepository: AuthRepository = AuthRepositoryFirebase(),
    private val profileRepository: ProfileRepository = ProfileRepositoryFirebase(),
    private val userInterestsRepository: UserInterestsRepository,
) : ViewModel() {

  private val _uiState =
      MutableStateFlow(
          SettingsUIState(interests = userInterestsRepository.getUserInterests().interests)
      )
  val uiState: StateFlow<SettingsUIState> = _uiState.asStateFlow()

  /** Clears the error message in the UI state. */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMsg = null) }
  }

  /** Updates the camera permission status reported by the UI permission controller. */
  fun setCameraPermissionGranted(granted: Boolean) {
    _uiState.update { it.copy(cameraPermissionGranted = granted) }
  }

  /** Updates the location permission status reported by the UI permission controller. */
  fun setLocationPermissionGranted(granted: Boolean) {
    _uiState.update { it.copy(locationPermissionGranted = granted) }
  }

  /** Adds an interest and persists the updated list without changing first-scan prompt state. */
  fun addInterest(interest: String) {
    val interests = _uiState.value.interests
    if (interest in interests) return
    val updatedInterests = interests + interest
    userInterestsRepository.updateInterests(updatedInterests)
    _uiState.update { it.copy(interests = updatedInterests) }
  }

  /** Removes an interest and persists the updated list without changing first-scan prompt state. */
  fun removeInterest(interest: String) {
    val interests = _uiState.value.interests
    if (interest !in interests) return
    val updatedInterests = interests - interest
    userInterestsRepository.updateInterests(updatedInterests)
    _uiState.update { it.copy(interests = updatedInterests) }
  }

  /** Loads the authenticated user's profile and preferences from the backend. */
  fun loadProfile() {
    if (_uiState.value.profileLoading) return
    viewModelScope.launch {
      _uiState.update { it.copy(profileLoading = true, errorMsg = null) }
      profileRepository
          .loadProfile()
          .fold(
              onSuccess = { profile ->
                _uiState.update {
                  it.copy(
                      profileLoading = false,
                      profile = profile,
                  )
                }
              },
              onFailure = { throwable ->
                _uiState.update {
                  it.copy(
                      profileLoading = false,
                      errorMsg = throwable.localizedMessage ?: "Unable to load your profile.",
                  )
                }
              },
          )
    }
  }

  /** Initiates sign-out and updates the UI state on success or failure. */
  fun signOut(credentialManager: CredentialManager) {
    viewModelScope.launch {
      authRepository
          .signOut()
          .fold(
              onSuccess = {
                try {
                  credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: ClearCredentialException) {
                  Log.w("SettingsViewModel", "Failed to clear credentials", e)
                }
                _uiState.update { it.copy(signedOut = true) }
              },
              onFailure = { throwable ->
                _uiState.update { it.copy(errorMsg = throwable.localizedMessage) }
              },
          )
    }
  }
}
