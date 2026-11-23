/*
 * Portions of this code were generated with the help of GitHub Copilot.
 */

package com.github.roamswent.roam.model.profile

import com.github.roamswent.roam.model.firestore.RoamFirestore
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Firebase implementation of [ProfileRepository].
 *
 * Reads profile information from the currently authenticated [FirebaseUser].
 *
 * @param auth The [FirebaseAuth] instance used to retrieve the current user.
 */
class ProfileRepositoryFirebase(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = RoamFirestore.instance,
) : ProfileRepository {

  override suspend fun initializePrivateProfile(): Result<Unit> =
      try {
        val user = requireCurrentUser()
        val email =
            user.email?.takeIf(String::isNotBlank)
                ?: return Result.failure(
                    IllegalStateException("The signed-in user does not have an email.")
                )
        val reference = firestore.collection(PRIVATE_USERS_COLLECTION).document(user.uid)
        val snapshot = reference.get().await()
        if (!snapshot.exists()) {
          reference
              .set(
                  mapOf(
                      EMAIL_FIELD to email,
                      REGISTRATION_TIMESTAMP_FIELD to FieldValue.serverTimestamp(),
                  )
              )
              .await()
        }
        Result.success(Unit)
      } catch (exception: Exception) {
        Result.failure(exception)
      }

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
        when {
          user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } ->
              PROVIDER_GOOGLE
          user.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID } -> PROVIDER_EMAIL
          else -> PROVIDER_ACCOUNT
        }
    val email = user.email.orEmpty()
    val displayName =
        user.displayName?.takeIf(String::isNotBlank)
            ?: email.substringBefore("@").takeIf(String::isNotBlank)
            ?: ""
    return UserProfile(
        displayName = displayName,
        email = email,
        provider = provider,
    )
  }

  companion object {
    const val PROVIDER_GOOGLE = "Google"
    const val PROVIDER_EMAIL = "Email"
    const val PROVIDER_ACCOUNT = "Account"
    const val PRIVATE_USERS_COLLECTION = "users"
    const val EMAIL_FIELD = "email"
    const val REGISTRATION_TIMESTAMP_FIELD = "registrationTimestamp"
  }
}
