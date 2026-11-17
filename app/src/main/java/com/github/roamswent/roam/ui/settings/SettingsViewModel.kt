/*
 * Portions of this code were generated with the help of GitHub Copilot.
 * Portions of this code were adapted from the bootcamp solution provided for SWENT.
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.settings

import android.util.Log
import androidx.annotation.StringRes
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.authentication.AuthRepository
import com.github.roamswent.roam.model.authentication.AuthRepositoryFirebase
import com.github.roamswent.roam.model.interests.UserInterestsRepository
import com.github.roamswent.roam.model.profile.ProfileRepository
import com.github.roamswent.roam.model.profile.ProfileRepositoryFirebase
import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.model.profile.PublicProfileException
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import com.github.roamswent.roam.model.profile.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUIState(
    @param:StringRes val errorMsg: Int? = null,
    val signedOut: Boolean = false,
    val cameraPermissionGranted: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val profileLoading: Boolean = false,
    val profile: UserProfile? = null,
    val publicProfile: PublicProfile? = null,
    val isUsernameSaving: Boolean = false,
    @param:StringRes val usernameErrorMsg: Int? = null,
    val interests: List<String> = emptyList(),
)

class SettingsViewModel(
    private val authRepository: AuthRepository = AuthRepositoryFirebase(),
    private val profileRepository: ProfileRepository = ProfileRepositoryFirebase(),
    private val userInterestsRepository: UserInterestsRepository,
    private val publicProfileRepository: PublicProfileRepository? = null,
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
    loadPublicProfile()
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
              onFailure = { _ ->
                _uiState.update {
                  it.copy(
                      profileLoading = false,
                      errorMsg = R.string.settings_error_profile_load,
                  )
                }
              },
          )
    }
  }

  private fun loadPublicProfile() {
    val repository = publicProfileRepository ?: return
    viewModelScope.launch {
      try {
        _uiState.update { it.copy(usernameErrorMsg = null) }
        val publicProfile = repository.getCurrentProfile()
        _uiState.update { it.copy(publicProfile = publicProfile) }
      } catch (exception: CancellationException) {
        throw exception
      } catch (_: Exception) {
        _uiState.update { it.copy(usernameErrorMsg = R.string.settings_username_error_load) }
      }
    }
  }

  /**
   * Changes the public username using the repository's existing validation and uniqueness checks.
   */
  fun renameUsername(username: String) {
    val repository =
        publicProfileRepository
            ?: run {
              _uiState.update {
                it.copy(usernameErrorMsg = R.string.settings_username_error_update)
              }
              return
            }
    if (_uiState.value.isUsernameSaving) return

    viewModelScope.launch {
      _uiState.update { it.copy(isUsernameSaving = true, usernameErrorMsg = null) }
      try {
        val updatedProfile = repository.renameUsername(username.trim())
        _uiState.update {
          it.copy(publicProfile = updatedProfile, isUsernameSaving = false, usernameErrorMsg = null)
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        val errorMessage =
            when (exception) {
              is PublicProfileException.InvalidUsername -> R.string.settings_username_error_invalid
              is PublicProfileException.UsernameTaken -> R.string.settings_username_error_taken
              else -> R.string.settings_username_error_update
            }
        _uiState.update { it.copy(isUsernameSaving = false, usernameErrorMsg = errorMessage) }
      }
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
              onFailure = { _ ->
                _uiState.update { it.copy(errorMsg = R.string.settings_error_sign_out) }
              },
          )
    }
  }
}
