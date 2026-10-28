/*
 * Portions of this code were generated with the help of GitHub Copilot.
 */

package com.github.roamswent.roam.model.profile

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

/**
 * Firebase implementation of [ProfileRepository].
 *
 * Reads profile information from the currently authenticated [FirebaseUser].
 *
 * @param auth The [FirebaseAuth] instance used to retrieve the current user.
 */
class ProfileRepositoryFirebase(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : ProfileRepository {

  override suspend fun loadProfile(): Result<UserProfile> = runCatching {
    val user = requireCurrentUser()
    profileFrom(user)
  }

  /** Returns the authenticated user or fails when no user is signed in. */
  private fun requireCurrentUser(): FirebaseUser =
      checkNotNull(auth.currentUser) { "You must be signed in to view your profile." }

  /** Maps Firebase authentication data to the app's profile model. */
  private fun profileFrom(user: FirebaseUser): UserProfile {
    val provider =
        if (user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }) "Google"
        else "Account"
    return UserProfile(
        displayName = user.displayName.orEmpty(),
        email = user.email.orEmpty(),
        provider = provider,
    )
  }
}
