/*
 * This code was adapted from the bootcamp solution provided for SWENT.
 */

package com.github.roamswent.roam.model.authentication

import androidx.credentials.Credential
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * A Firebase implementation of [AuthRepository].
 *
 * Retrieves a Google ID token via Credential Manager and authenticates the user with Firebase. Also
 * handles sign-out.
 *
 * @param auth The [FirebaseAuth] instance for Firebase authentication.
 * @param helper A [GoogleSignInHelper] to extract Google ID token credentials and convert them to
 *   Firebase credentials.
 */
class AuthRepositoryFirebase(
    private val auth: FirebaseAuth = Firebase.auth,
    private val helper: GoogleSignInHelper = DefaultGoogleSignInHelper(),
) : AuthRepository {

  override suspend fun signUpWithEmail(email: String, password: String): Result<FirebaseUser> =
      try {
        val user =
            auth.createUserWithEmailAndPassword(email, password).await().user
                ?: return Result.failure(
                    IllegalStateException("Sign-up failed: Could not retrieve user information")
                )
        Result.success(user)
      } catch (e: Exception) {
        Result.failure(mapEmailAuthError("Sign-up", e))
      }

  override suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> =
      try {
        val user =
            auth.signInWithEmailAndPassword(email, password).await().user
                ?: return Result.failure(
                    IllegalStateException("Sign-in failed: Could not retrieve user information")
                )
        Result.success(user)
      } catch (e: Exception) {
        Result.failure(mapEmailAuthError("Sign-in", e))
      }

  override suspend fun sendEmailVerification(): Result<Unit> =
      try {
        val user =
            auth.currentUser
                ?: return Result.failure(
                    IllegalStateException("Email verification failed: No user is signed in")
                )
        user.sendEmailVerification().await()
        Result.success(Unit)
      } catch (e: Exception) {
        Result.failure(mapEmailAuthError("Email verification", e))
      }

  override suspend fun resendEmailVerification(): Result<Unit> = sendEmailVerification()

  override suspend fun reloadEmailVerificationStatus(): Result<Boolean> =
      try {
        val user =
            auth.currentUser
                ?: return Result.failure(
                    IllegalStateException("Email verification check failed: No user is signed in")
                )
        user.reload().await()
        Result.success(user.isEmailVerified)
      } catch (e: Exception) {
        Result.failure(mapEmailAuthError("Email verification check", e))
      }

  override fun isEmailVerified(): Boolean = auth.currentUser?.isEmailVerified ?: false

  private fun mapEmailAuthError(operation: String, error: Exception): IllegalStateException {
    val message =
        if (error is FirebaseAuthException) {
          when (error.errorCode) {
            "ERROR_INVALID_EMAIL" -> "The email address is invalid"
            "ERROR_EMAIL_ALREADY_IN_USE" -> "An account already exists with this email"
            "ERROR_WEAK_PASSWORD" -> "The password is too weak"
            "ERROR_INVALID_CREDENTIAL",
            "ERROR_USER_NOT_FOUND",
            "ERROR_WRONG_PASSWORD" -> "The email or password is incorrect"
            "ERROR_USER_DISABLED" -> "This account has been disabled"
            "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts; try again later"
            "ERROR_OPERATION_NOT_ALLOWED" -> "Email/password authentication is not enabled"
            else -> error.localizedMessage ?: "Unexpected error"
          }
        } else {
          error.localizedMessage ?: "Unexpected error"
        }
    return IllegalStateException("$operation failed: $message")
  }

  override suspend fun signInWithGoogle(credential: Credential): Result<FirebaseUser> {
    return try {
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val idToken = helper.extractIdTokenCredential(credential.data).idToken
        val firebaseCred = helper.toFirebaseCredential(idToken)

        // Sign in with Firebase
        val user =
            auth.signInWithCredential(firebaseCred).await().user
                ?: return Result.failure(
                    IllegalStateException("Login failed : Could not retrieve user information")
                )

        return Result.success(user)
      } else {
        return Result.failure(
            IllegalStateException("Login failed: Credential is not of type Google ID")
        )
      }
    } catch (e: Exception) {
      Result.failure(
          IllegalStateException("Login failed: ${e.localizedMessage ?: "Unexpected error."}")
      )
    }
  }

  /** Returns the currently signed-in user, or null when signed out. */
  override fun currentUser(): FirebaseUser? {
    return auth.currentUser
  }

  /** Emits the currently signed-in user (or null when signed out) on every auth state change. */
  override fun currentUserFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
    auth.addAuthStateListener(listener)
    awaitClose { auth.removeAuthStateListener(listener) }
  }

  override fun signOut(): Result<Unit> {
    return try {
      // Firebase sign out
      auth.signOut()

      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(
          IllegalStateException("Logout failed: ${e.localizedMessage ?: "Unexpected error."}")
      )
    }
  }
}
