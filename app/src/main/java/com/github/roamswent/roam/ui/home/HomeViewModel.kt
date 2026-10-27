/*
 * Co-authored-by: Mistral Vibe <vibe@mistral.ai>
 */

package com.github.roamswent.roam.ui.home

import androidx.lifecycle.ViewModel
import com.github.roamswent.roam.model.authentication.AuthRepository
import com.github.roamswent.roam.model.authentication.AuthRepositoryFirebase
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for the Home screen.
 *
 * Exposes the display name of the currently signed-in user for the greeting header.
 *
 * @property repository The repository used to retrieve the currently signed-in user.
 */
class HomeViewModel(private val repository: AuthRepository = AuthRepositoryFirebase()) :
    ViewModel() {

  private val _userName = MutableStateFlow(DEFAULT_USER_NAME)
  val userName: StateFlow<String> = _userName

  init {
    _userName.value = resolveDisplayName(repository.currentUser())
  }

  /**
   * Resolves the display name of a [FirebaseUser].
   *
   * Uses the user's display name when available, otherwise the local part of the email, otherwise
   * the default user name.
   *
   * @param user The currently signed-in user, or null when signed out.
   * @return The resolved display name.
   */
  private fun resolveDisplayName(user: FirebaseUser?): String {
    user
        ?.displayName
        ?.takeIf { it.isNotBlank() }
        ?.let {
          return it
        }
    user
        ?.email
        ?.takeIf { it.isNotBlank() }
        ?.let {
          return it.substringBefore("@")
        }
    return DEFAULT_USER_NAME
  }

  companion object {
    const val DEFAULT_USER_NAME = "Victor"
  }
}
