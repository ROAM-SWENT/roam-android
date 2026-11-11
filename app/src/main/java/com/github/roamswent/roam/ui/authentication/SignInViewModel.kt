/*
 * This code was adapted from the bootcamp solution provided for SWENT.
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.authentication

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.authentication.AuthRepository
import com.github.roamswent.roam.model.authentication.AuthRepositoryFirebase
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Represents the UI state for authentication.
 *
 * @property isLoading Whether an authentication operation is in progress.
 * @property isRestoringSession Whether the initial authentication state is still being resolved.
 * @property user The currently signed-in [FirebaseUser], or null if not signed in.
 * @property errorMsg An error message to display, or null if there is no error.
 * @property signOutCompleted True after the cached signed-in user is cleared by sign-out.
 */
data class AuthUIState(
    val isLoading: Boolean = false,
    val isRestoringSession: Boolean = false,
    val user: FirebaseUser? = null,
    val errorMsg: String? = null,
    val signOutCompleted: Boolean = false,
)

/**
 * ViewModel for the Sign-In view.
 *
 * @property repository The repository used to perform authentication operations.
 */
class SignInViewModel(private val repository: AuthRepository = AuthRepositoryFirebase()) :
    ViewModel() {

  // Firebase restores the cached user locally, including when the device is offline.
  private val _uiState =
      MutableStateFlow(AuthUIState(user = repository.currentUser(), isRestoringSession = true))
  val uiState: StateFlow<AuthUIState> = _uiState

  init {
    viewModelScope.launch {
      repository.currentUserFlow().collect { user ->
        _uiState.update { it.copy(user = user, isRestoringSession = false) }
      }
    }
  }

  /** Clears the error message in the UI state. */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMsg = null) }
  }

  /** Clears the cached user after a successful sign-out. */
  fun clearSignedInUser() {
    _uiState.update { it.copy(user = null, signOutCompleted = true) }
  }

  /** Signs out an incomplete account and clears its cached UI state on success. */
  fun signOut(): Result<Unit> {
    val result = repository.signOut()
    result.onSuccess { clearSignedInUser() }
    return result
  }

  private fun getSignInOptions(context: Context) =
      GetSignInWithGoogleOption.Builder(
              serverClientId = context.getString(R.string.default_web_client_id)
          )
          .build()

  private fun signInRequest(signInOptions: GetSignInWithGoogleOption) =
      GetCredentialRequest.Builder().addCredentialOption(signInOptions).build()

  private suspend fun getCredential(
      context: Context,
      request: GetCredentialRequest,
      credentialManager: CredentialManager,
  ) = credentialManager.getCredential(context, request).credential

  /** Initiates the Google sign-in flow and updates the UI state on success or failure. */
  fun signIn(context: Context, credentialManager: CredentialManager) {
    if (_uiState.value.isLoading) return

    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMsg = null, signOutCompleted = false) }

      val signInOptions = getSignInOptions(context)
      val signInRequest = signInRequest(signInOptions)

      try {
        // Launch Credential Manager UI safely
        val credential = getCredential(context, signInRequest, credentialManager)

        // Pass the credential to your repository
        repository.signInWithGoogle(credential).fold({ user ->
          _uiState.update {
            it.copy(isLoading = false, user = user, errorMsg = null, signOutCompleted = false)
          }
        }) { failure ->
          _uiState.update {
            it.copy(
                isLoading = false,
                errorMsg = failure.localizedMessage,
                user = null,
            )
          }
        }
      } catch (_: GetCredentialCancellationException) {
        // User cancelled the sign-in flow
        _uiState.update { it.copy(isLoading = false, errorMsg = "Sign-in cancelled", user = null) }
      } catch (e: androidx.credentials.exceptions.GetCredentialException) {
        // Other credential errors
        _uiState.update {
          it.copy(
              isLoading = false,
              errorMsg = "Failed to get credentials: ${e.localizedMessage}",
              user = null,
          )
        }
      } catch (e: Exception) {
        // Unexpected errors
        _uiState.update {
          it.copy(
              isLoading = false,
              errorMsg = "Unexpected error: ${e.localizedMessage}",
              user = null,
          )
        }
      }
    }
  }
}
