/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProfileRegistrationStatus {
  UNKNOWN,
  CHECKING,
  REGISTRATION_REQUIRED,
  REGISTERED,
  ERROR,
}

data class RegisterProfileUiState(
    val status: ProfileRegistrationStatus = ProfileRegistrationStatus.UNKNOWN,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class RegisterProfileViewModel(
    private val repository: PublicProfileRepository,
) : ViewModel() {
  private var activeProfileJob: Job? = null
  private val _uiState = MutableStateFlow(RegisterProfileUiState())
  val uiState: StateFlow<RegisterProfileUiState> = _uiState

  fun checkCurrentProfile() {
    if (_uiState.value.isLoading || _uiState.value.status == ProfileRegistrationStatus.CHECKING) {
      return
    }

    activeProfileJob = viewModelScope.launch {
      _uiState.update {
        it.copy(status = ProfileRegistrationStatus.CHECKING, isLoading = true, errorMessage = null)
      }
      try {
        val profile = repository.getCurrentProfile()
        _uiState.update {
          it.copy(
              status =
                  if (profile == null) ProfileRegistrationStatus.REGISTRATION_REQUIRED
                  else ProfileRegistrationStatus.REGISTERED,
              isLoading = false,
          )
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update {
          it.copy(
              status = ProfileRegistrationStatus.ERROR,
              isLoading = false,
              errorMessage = exception.localizedMessage,
          )
        }
      }
    }
  }

  fun registerUsername(username: String) {
    if (
        _uiState.value.isLoading ||
            _uiState.value.status != ProfileRegistrationStatus.REGISTRATION_REQUIRED
    ) {
      return
    }

    activeProfileJob = viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      try {
        repository.registerUsername(username.trim())
        _uiState.update {
          it.copy(status = ProfileRegistrationStatus.REGISTERED, isLoading = false)
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(isLoading = false, errorMessage = exception.localizedMessage) }
      }
    }
  }

  fun reset() {
    activeProfileJob?.cancel()
    activeProfileJob = null
    _uiState.value = RegisterProfileUiState()
  }
}
