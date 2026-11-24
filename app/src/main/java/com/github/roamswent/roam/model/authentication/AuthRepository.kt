/*
 * This code was adapted from the bootcamp solution provided for SWENT.
 */

package com.github.roamswent.roam.model.authentication

import androidx.credentials.Credential
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow

/** Handles authentication operations such as signing in with Google and signing out. */
interface AuthRepository {

  /**
   * Signs in the user using a Google account through the Credential Manager API.
   *
   * @return A [Result] containing a [FirebaseUser] on success, or an exception on failure.
   */
  suspend fun signInWithGoogle(credential: Credential): Result<FirebaseUser>

  /** Creates a Firebase user with an email and password. */
  suspend fun signUpWithEmail(email: String, password: String): Result<FirebaseUser> =
      Result.failure(UnsupportedOperationException("Email/password sign-up is not supported"))

  /** Signs in a Firebase user with an email and password. */
  suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> =
      Result.failure(UnsupportedOperationException("Email/password sign-in is not supported"))

  /** Sends a verification email to the currently signed-in user. */
  suspend fun sendEmailVerification(): Result<Unit> =
      Result.failure(UnsupportedOperationException("Email verification is not supported"))

  /** Resends a verification email to the currently signed-in user. */
  suspend fun resendEmailVerification(): Result<Unit> = sendEmailVerification()

  /** Reloads the current user and reports whether their email has been verified. */
  suspend fun reloadEmailVerificationStatus(): Result<Boolean> =
      Result.failure(UnsupportedOperationException("Email verification status is not supported"))

  /** Returns the locally cached email verification status. */
  fun isEmailVerified(): Boolean = false

  /** Firebase-named aliases for email/password authentication. */
  suspend fun signUpWithEmailAndPassword(
      email: String,
      password: String,
  ): Result<FirebaseUser> = signUpWithEmail(email, password)

  suspend fun signInWithEmailAndPassword(
      email: String,
      password: String,
  ): Result<FirebaseUser> = signInWithEmail(email, password)

  /** Returns the currently signed-in user, or null when signed out. */
  fun currentUser(): FirebaseUser?

  /** Emits the currently signed-in user (or null when signed out) on every auth state change. */
  fun currentUserFlow(): Flow<FirebaseUser?>

  /**
   * Signs out the currently authenticated user and clears the credential state.
   *
   * @return A [Result] indicating success or failure.
   */
  fun signOut(): Result<Unit>
}
