/*
 * This code was adapted from the bootcamp solution provided for SWENT.
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.authentication

import android.content.Context
import androidx.annotation.StringRes
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.authentication.AuthRepository
import com.github.roamswent.roam.model.authentication.AuthRepositoryFirebase
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthError(
    @param:StringRes val messageRes: Int,
    val formatArg: String? = null,
)

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
    val errorMsg: AuthError? = null,
    val signOutCompleted: Boolean = false,
    val verificationEmailSent: Boolean = false,
)

fun isUnverifiedEmailPasswordUser(user: FirebaseUser?): Boolean =
    user != null &&
        !user.isEmailVerified &&
        user.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID }

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

  fun signInWithEmail(email: String, password: String) {
    if (_uiState.value.isLoading) return
    val validationError = validateEmailPassword(email, password)
    if (validationError != null) {
      _uiState.update { it.copy(errorMsg = validationError, isLoading = false) }
      return
    }
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMsg = null, verificationEmailSent = false) }
      repository.signInWithEmail(email.trim(), password).fold({ user ->
        _uiState.update {
          it.copy(isLoading = false, user = user, errorMsg = null, signOutCompleted = false)
        }
      }) { failure ->
        _uiState.update {
          it.copy(
              isLoading = false,
              errorMsg = AuthError(R.string.sign_in_error_unexpected, failure.localizedMessage),
              user = null,
          )
        }
      }
    }
  }

  fun signUpWithEmail(email: String, password: String, confirmation: String) {
    if (_uiState.value.isLoading) return
    val validationError =
        validateEmailPassword(email, password)
            ?: if (password != confirmation)
                AuthError(R.string.auth_validation_password_confirmation)
            else null
    if (validationError != null) {
      _uiState.update { it.copy(errorMsg = validationError, isLoading = false) }
      return
    }
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMsg = null, verificationEmailSent = false) }
      repository.signUpWithEmail(email.trim(), password).fold({ user ->
        repository.sendEmailVerification().fold({
          _uiState.update {
            it.copy(
                isLoading = false,
                user = user,
                errorMsg = null,
                verificationEmailSent = true,
                signOutCompleted = false,
            )
          }
        }) { failure ->
          _uiState.update {
            it.copy(
                isLoading = false,
                user = user,
                errorMsg = AuthError(R.string.sign_in_error_unexpected, failure.localizedMessage),
                signOutCompleted = false,
            )
          }
        }
      }) { failure ->
        _uiState.update {
          it.copy(
              isLoading = false,
              errorMsg = AuthError(R.string.sign_in_error_unexpected, failure.localizedMessage),
              user = null,
          )
        }
      }
    }
  }

  fun resendEmailVerification() {
    if (_uiState.value.isLoading) return
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMsg = null) }
      repository.resendEmailVerification().fold({
        _uiState.update {
          it.copy(isLoading = false, verificationEmailSent = true, errorMsg = null)
        }
      }) { failure ->
        _uiState.update {
          it.copy(
              isLoading = false,
              errorMsg = AuthError(R.string.sign_in_error_unexpected, failure.localizedMessage),
          )
        }
      }
    }
  }

  fun checkEmailVerification(onVerified: () -> Unit) {
    if (_uiState.value.isLoading) return
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMsg = null) }
      repository.reloadEmailVerificationStatus().fold({ verified ->
        if (verified) {
          _uiState.update { it.copy(isLoading = false, verificationEmailSent = false) }
          onVerified()
        } else {
          _uiState.update {
            it.copy(
                isLoading = false,
                errorMsg = AuthError(R.string.email_verification_still_pending),
            )
          }
        }
      }) { failure ->
        _uiState.update {
          it.copy(
              isLoading = false,
              errorMsg = AuthError(R.string.sign_in_error_unexpected, failure.localizedMessage),
          )
        }
      }
    }
  }

  private fun validateEmailPassword(email: String, password: String): AuthError? {
    val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    if (!emailPattern.matches(email.trim())) {
      return AuthError(R.string.auth_validation_email)
    }
    if (password.length < 6) return AuthError(R.string.auth_validation_password)
    return null
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
                errorMsg =
                    AuthError(
                        R.string.sign_in_error_unexpected,
                        failure.localizedMessage.orEmpty(),
                    ),
                user = null,
            )
          }
        }
      } catch (_: GetCredentialCancellationException) {
        // User cancelled the sign-in flow
        _uiState.update {
          it.copy(
              isLoading = false,
              errorMsg = AuthError(R.string.sign_in_error_cancelled),
              user = null,
          )
        }
      } catch (e: androidx.credentials.exceptions.GetCredentialException) {
        // Other credential errors
        _uiState.update {
          it.copy(
              isLoading = false,
              errorMsg =
                  AuthError(
                      R.string.sign_in_error_credentials,
                      e.localizedMessage.orEmpty(),
                  ),
              user = null,
          )
        }
      } catch (e: Exception) {
        // Unexpected errors
        _uiState.update {
          it.copy(
              isLoading = false,
              errorMsg =
                  AuthError(
                      R.string.sign_in_error_unexpected,
                      e.localizedMessage.orEmpty(),
                  ),
              user = null,
          )
        }
      }
    }
  }
}
