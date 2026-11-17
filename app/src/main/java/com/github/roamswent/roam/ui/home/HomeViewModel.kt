/*
 * Co-authored-by: Mistral Vibe <vibe@mistral.ai>
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.roamswent.roam.model.authentication.AuthRepository
import com.github.roamswent.roam.model.authentication.AuthRepositoryFirebase
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Home screen.
 *
 * Exposes the registered username of the signed-in user, falling back to their authentication
 * display name when no public profile is available.
 *
 * @property repository The repository used to observe the currently signed-in user.
 */
class HomeViewModel(
    private val repository: AuthRepository = AuthRepositoryFirebase(),
    private val publicProfileRepository: PublicProfileRepository? = null,
) : ViewModel() {

  private val _userName = MutableStateFlow(DEFAULT_USER_NAME)
  val userName: StateFlow<String> = _userName
  private var currentUser: FirebaseUser? = repository.currentUser()
  private var profileJob: Job? = null

  init {
    refreshUserName(currentUser)
    viewModelScope.launch {
      repository.currentUserFlow().collect { user ->
        val userChanged = currentUser?.uid != user?.uid
        currentUser = user
        if (userChanged && publicProfileRepository != null) {
          _userName.value = DEFAULT_USER_NAME
        }
        refreshUserName(user)
      }
    }
  }

  /** Refreshes the username when returning to the home screen from settings. */
  fun refreshUserName() {
    refreshUserName(currentUser)
  }

  private fun refreshUserName(user: FirebaseUser?) {
    val fallbackName = resolveDisplayName(user)
    profileJob?.cancel()
    if (publicProfileRepository == null || user == null) {
      _userName.value = fallbackName
      return
    }

    profileJob = viewModelScope.launch {
      try {
        _userName.value = publicProfileRepository.getCurrentProfile()?.username ?: fallbackName
      } catch (exception: CancellationException) {
        throw exception
      } catch (_: Exception) {
        _userName.value = fallbackName
      }
    }
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
    const val DEFAULT_USER_NAME = ""
  }
}
