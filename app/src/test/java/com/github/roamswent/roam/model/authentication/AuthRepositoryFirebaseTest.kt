/*
 * Portions of this code were generated and/or refined with the help of ChatGPT.
 * Portions of this code were generated with the help of GitHub Copilot.
 */

package com.github.roamswent.roam.model.authentication

import android.os.Bundle
import androidx.credentials.Credential
import androidx.credentials.CustomCredential
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AuthRepositoryFirebaseTest {

  private lateinit var auth: FirebaseAuth
  private lateinit var helper: GoogleSignInHelper
  private lateinit var repository: AuthRepositoryFirebase
  private lateinit var credential: CustomCredential
  private lateinit var user: FirebaseUser

  @Before
  fun setUp() {
    auth = mock()
    helper = mock()
    repository = AuthRepositoryFirebase(auth, helper)
    credential = CustomCredential(TYPE_GOOGLE_ID_TOKEN_CREDENTIAL, Bundle())
    user = mock()
  }

  /**
   * Verifies that a valid Google credential is successfully used to sign in through Firebase and
   * that the authenticated user is returned.
   */
  @Test
  fun signInWithGoogle_returnsUserAndUsesFirebaseCredential() = runTest {
    val tokenCredential = mock<GoogleIdTokenCredential>()
    val firebaseCredential = mock<com.google.firebase.auth.AuthCredential>()
    val authResult = mock<AuthResult>()
    whenever(tokenCredential.idToken).thenReturn("token")
    whenever(helper.extractIdTokenCredential(any())).thenReturn(tokenCredential)
    whenever(helper.toFirebaseCredential("token")).thenReturn(firebaseCredential)
    whenever(authResult.user).thenReturn(user)
    whenever(auth.signInWithCredential(firebaseCredential)).thenReturn(Tasks.forResult(authResult))

    val result = repository.signInWithGoogle(credential)

    assertTrue(result.isSuccess)
    assertEquals(user, result.getOrNull())
    verify(helper).extractIdTokenCredential(credential.data)
    verify(helper).toFirebaseCredential("token")
    verify(auth).signInWithCredential(firebaseCredential)
  }

  /**
   * Verifies that sign-in fails when the provided credential is not a Google custom credential and
   * that Firebase authentication is not attempted.
   */
  @Test
  fun signInWithGoogle_failsWhenCredentialIsNotGoogleCustomCredential() = runTest {
    val result = repository.signInWithGoogle(mock<Credential>())

    assertFalse(result.isSuccess)
    assertEquals(
        "Login failed: Credential is not of type Google ID",
        result.exceptionOrNull()?.message,
    )
    verify(auth, never()).signInWithCredential(any())
  }

  /**
   * Verifies that sign-in fails when a custom credential has an unsupported credential type and
   * that Firebase authentication is not attempted.
   */
  @Test
  fun signInWithGoogle_failsWhenCustomCredentialHasWrongType() = runTest {
    val wrongCredential = CustomCredential("wrong.type", Bundle())

    val result = repository.signInWithGoogle(wrongCredential)

    assertFalse(result.isSuccess)
    assertEquals(
        "Login failed: Credential is not of type Google ID",
        result.exceptionOrNull()?.message,
    )
    verify(auth, never()).signInWithCredential(any())
  }

  /**
   * Verifies that sign-in fails when Firebase successfully authenticates but does not return a
   * user.
   */
  @Test
  fun signInWithGoogle_failsWhenFirebaseReturnsNoUser() = runTest {
    val tokenCredential = mock<GoogleIdTokenCredential>()
    val firebaseCredential = mock<com.google.firebase.auth.AuthCredential>()
    val authResult = mock<AuthResult>()
    whenever(tokenCredential.idToken).thenReturn("token")
    whenever(helper.extractIdTokenCredential(any())).thenReturn(tokenCredential)
    whenever(helper.toFirebaseCredential("token")).thenReturn(firebaseCredential)
    whenever(authResult.user).thenReturn(null)
    whenever(auth.signInWithCredential(firebaseCredential)).thenReturn(Tasks.forResult(authResult))

    val result = repository.signInWithGoogle(credential)

    assertFalse(result.isSuccess)
    assertEquals(
        "Login failed : Could not retrieve user information",
        result.exceptionOrNull()?.message,
    )
  }

  /**
   * Verifies that an exception thrown while processing the Google credential is converted into a
   * failed sign-in result with the original error message.
   */
  @Test
  fun signInWithGoogle_mapsDependencyExceptionToFailure() = runTest {
    whenever(helper.extractIdTokenCredential(any()))
        .thenThrow(IllegalArgumentException("bad token"))

    val result = repository.signInWithGoogle(credential)

    assertFalse(result.isSuccess)
    assertEquals("Login failed: bad token", result.exceptionOrNull()?.message)
  }

  /**
   * Verifies that a Firebase authentication failure is converted into a failed sign-in result
   * containing the Firebase error message.
   */
  @Test
  fun signInWithGoogle_mapsFirebaseFailureToFailure() = runTest {
    val tokenCredential = mock<GoogleIdTokenCredential>()
    val firebaseCredential = mock<com.google.firebase.auth.AuthCredential>()
    whenever(tokenCredential.idToken).thenReturn("token")
    whenever(helper.extractIdTokenCredential(any())).thenReturn(tokenCredential)
    whenever(helper.toFirebaseCredential("token")).thenReturn(firebaseCredential)
    whenever(auth.signInWithCredential(firebaseCredential))
        .thenReturn(Tasks.forException(IllegalStateException("firebase unavailable")))

    val result = repository.signInWithGoogle(credential)

    assertFalse(result.isSuccess)
    assertEquals("Login failed: firebase unavailable", result.exceptionOrNull()?.message)
  }

  @Test
  fun signUpWithEmail_returnsUser() = runTest {
    val authResult = mock<AuthResult>()
    whenever(authResult.user).thenReturn(user)
    whenever(auth.createUserWithEmailAndPassword("roam@example.com", "password"))
        .thenReturn(Tasks.forResult(authResult))

    val result = repository.signUpWithEmail("roam@example.com", "password")

    assertTrue(result.isSuccess)
    assertEquals(user, result.getOrNull())
  }

  @Test
  fun signUpWithEmail_failsWhenFirebaseReturnsNoUser() = runTest {
    val authResult = mock<AuthResult>()
    whenever(authResult.user).thenReturn(null)
    whenever(auth.createUserWithEmailAndPassword("roam@example.com", "password"))
        .thenReturn(Tasks.forResult(authResult))

    val result = repository.signUpWithEmail("roam@example.com", "password")

    assertFalse(result.isSuccess)
    assertEquals(
        "Sign-up failed: Could not retrieve user information",
        result.exceptionOrNull()?.message,
    )
  }

  @Test
  fun signUpWithEmail_mapsFirebaseFailure() = runTest {
    whenever(auth.createUserWithEmailAndPassword("roam@example.com", "password"))
        .thenReturn(Tasks.forException(IllegalStateException("email already in use")))

    val result = repository.signUpWithEmail("roam@example.com", "password")

    assertFalse(result.isSuccess)
    assertEquals("Sign-up failed: email already in use", result.exceptionOrNull()?.message)
  }

  @Test
  fun signInWithEmail_returnsUser() = runTest {
    val authResult = mock<AuthResult>()
    whenever(authResult.user).thenReturn(user)
    whenever(auth.signInWithEmailAndPassword("roam@example.com", "password"))
        .thenReturn(Tasks.forResult(authResult))

    val result = repository.signInWithEmail("roam@example.com", "password")

    assertTrue(result.isSuccess)
    assertEquals(user, result.getOrNull())
  }

  @Test
  fun signInWithEmail_failsWhenFirebaseReturnsNoUser() = runTest {
    val authResult = mock<AuthResult>()
    whenever(authResult.user).thenReturn(null)
    whenever(auth.signInWithEmailAndPassword("roam@example.com", "password"))
        .thenReturn(Tasks.forResult(authResult))

    val result = repository.signInWithEmail("roam@example.com", "password")

    assertFalse(result.isSuccess)
    assertEquals(
        "Sign-in failed: Could not retrieve user information",
        result.exceptionOrNull()?.message,
    )
  }

  @Test
  fun signInWithEmail_mapsFirebaseFailure() = runTest {
    whenever(auth.signInWithEmailAndPassword("roam@example.com", "wrong"))
        .thenReturn(Tasks.forException(IllegalStateException("invalid credential")))

    val result = repository.signInWithEmail("roam@example.com", "wrong")

    assertFalse(result.isSuccess)
    assertEquals("Sign-in failed: invalid credential", result.exceptionOrNull()?.message)
  }

  @Test
  fun sendEmailVerification_sendsToCurrentUser() = runTest {
    whenever(auth.currentUser).thenReturn(user)
    whenever(user.sendEmailVerification()).thenReturn(Tasks.forResult(null))

    val result = repository.sendEmailVerification()

    assertTrue(result.isSuccess)
    verify(user).sendEmailVerification()
  }

  @Test
  fun sendEmailVerification_failsWhenNoUserIsSignedIn() = runTest {
    whenever(auth.currentUser).thenReturn(null)

    val result = repository.sendEmailVerification()

    assertFalse(result.isSuccess)
    assertEquals(
        "Email verification failed: No user is signed in",
        result.exceptionOrNull()?.message,
    )
  }

  @Test
  fun sendEmailVerification_mapsFirebaseFailure() = runTest {
    whenever(auth.currentUser).thenReturn(user)
    whenever(user.sendEmailVerification())
        .thenReturn(Tasks.forException(IllegalStateException("too many requests")))

    val result = repository.sendEmailVerification()

    assertFalse(result.isSuccess)
    assertEquals(
        "Email verification failed: too many requests",
        result.exceptionOrNull()?.message,
    )
  }

  @Test
  fun resendEmailVerification_failsWhenNoUserIsSignedIn() = runTest {
    whenever(auth.currentUser).thenReturn(null)

    val result = repository.resendEmailVerification()

    assertFalse(result.isSuccess)
    assertEquals(
        "Email verification failed: No user is signed in",
        result.exceptionOrNull()?.message,
    )
  }

  @Test
  fun reloadEmailVerificationStatus_reloadsAndReturnsStatus() = runTest {
    whenever(auth.currentUser).thenReturn(user)
    whenever(user.reload()).thenReturn(Tasks.forResult(null))
    whenever(user.isEmailVerified).thenReturn(true)

    val result = repository.reloadEmailVerificationStatus()

    assertTrue(result.isSuccess)
    assertEquals(true, result.getOrNull())
    verify(user).reload()
  }

  @Test
  fun reloadEmailVerificationStatus_failsWhenNoUserIsSignedIn() = runTest {
    whenever(auth.currentUser).thenReturn(null)

    val result = repository.reloadEmailVerificationStatus()

    assertFalse(result.isSuccess)
    assertEquals(
        "Email verification check failed: No user is signed in",
        result.exceptionOrNull()?.message,
    )
  }

  @Test
  fun reloadEmailVerificationStatus_mapsFirebaseFailure() = runTest {
    whenever(auth.currentUser).thenReturn(user)
    whenever(user.reload())
        .thenReturn(Tasks.forException(IllegalStateException("network unavailable")))

    val result = repository.reloadEmailVerificationStatus()

    assertFalse(result.isSuccess)
    assertEquals(
        "Email verification check failed: network unavailable",
        result.exceptionOrNull()?.message,
    )
  }

  @Test
  fun isEmailVerified_returnsCachedStatus() {
    whenever(auth.currentUser).thenReturn(user)
    whenever(user.isEmailVerified).thenReturn(true)

    assertTrue(repository.isEmailVerified())
  }

  @Test
  fun isEmailVerified_returnsFalseWithoutUser() {
    whenever(auth.currentUser).thenReturn(null)

    assertFalse(repository.isEmailVerified())
  }

  /**
   * Verifies that signing out successfully calls Firebase's sign-out method and returns a
   * successful result.
   */
  @Test
  fun signOut_returnsSuccessAndSignsOut() {
    val result = repository.signOut()

    assertTrue(result.isSuccess)
    verify(auth).signOut()
  }

  /**
   * Verifies that an exception thrown by Firebase during sign-out is converted into a failed result
   * containing the original error message.
   */
  @Test
  fun signOut_mapsExceptionToFailure() {
    doThrow(IllegalStateException("cannot logout")).whenever(auth).signOut()

    val result = repository.signOut()

    assertFalse(result.isSuccess)
    assertEquals("Logout failed: cannot logout", result.exceptionOrNull()?.message)
  }

  /**
   * Verifies that the currently signed-in Firebase user is returned by
   * [AuthRepositoryFirebase.currentUser].
   */
  @Test
  fun currentUserReturnsSignedInUser() {
    whenever(auth.currentUser).thenReturn(user)

    assertEquals(user, repository.currentUser())
  }

  /** Verifies that null is returned when no user is currently signed in with Firebase. */
  @Test
  fun currentUserReturnsNullWhenSignedOut() {
    whenever(auth.currentUser).thenReturn(null)

    assertNull(repository.currentUser())
  }

  /**
   * Verifies that the auth-state listener registered when collecting the flow emits the currently
   * signed-in user as soon as it is fired.
   */
  @Test
  fun currentUserFlowEmitsOnListenerRegistration() = runTest {
    whenever(auth.currentUser).thenReturn(user)
    val listenerCaptor = argumentCaptor<FirebaseAuth.AuthStateListener>()
    val emissions = mutableListOf<FirebaseUser?>()
    val collector = launch { repository.currentUserFlow().collect { emissions.add(it) } }

    runCurrent()
    verify(auth).addAuthStateListener(listenerCaptor.capture())

    listenerCaptor.firstValue.onAuthStateChanged(auth)
    runCurrent()

    assertEquals(listOf(user), emissions)
    collector.cancel()
    advanceUntilIdle()
  }

  /**
   * Verifies that cancelling the flow collection removes the registered auth-state listener from
   * Firebase.
   */
  @Test
  fun currentUserFlowRemovesListenerOnCancel() = runTest {
    val listenerCaptor = argumentCaptor<FirebaseAuth.AuthStateListener>()
    val collector = launch { repository.currentUserFlow().collect {} }

    runCurrent()
    verify(auth).addAuthStateListener(listenerCaptor.capture())

    collector.cancel()
    advanceUntilIdle()

    verify(auth).removeAuthStateListener(listenerCaptor.firstValue)
  }

  private fun mapError(
      operation: String,
      error: Exception,
  ): IllegalStateException {
    val repository = mock(AuthRepositoryFirebase::class.java, CALLS_REAL_METHODS)
    val method =
        AuthRepositoryFirebase::class
            .java
            .getDeclaredMethod(
                "mapEmailAuthError",
                String::class.java,
                Exception::class.java,
            )
    method.isAccessible = true
    return method.invoke(repository, operation, error) as IllegalStateException
  }

  @Test
  fun mapEmailAuthError_mapsKnownFirebaseErrors() {
    val cases =
        mapOf(
            "ERROR_INVALID_EMAIL" to "The email address is invalid",
            "ERROR_EMAIL_ALREADY_IN_USE" to "An account already exists with this email",
            "ERROR_WEAK_PASSWORD" to "The password is too weak",
            "ERROR_INVALID_CREDENTIAL" to "The email or password is incorrect",
            "ERROR_USER_NOT_FOUND" to "The email or password is incorrect",
            "ERROR_WRONG_PASSWORD" to "The email or password is incorrect",
            "ERROR_USER_DISABLED" to "This account has been disabled",
            "ERROR_TOO_MANY_REQUESTS" to "Too many attempts; try again later",
            "ERROR_OPERATION_NOT_ALLOWED" to "Email/password authentication is not enabled",
        )

    cases.forEach { (code, expectedMessage) ->
      val error = FirebaseAuthException(code, "Firebase error")

      val result = mapError("Sign-in", error)

      assertEquals("Sign-in failed: $expectedMessage", result.message)
    }
  }

  @Test
  fun mapEmailAuthError_unknownFirebaseCodeUsesOriginalMessage() {
    val error = FirebaseAuthException("ERROR_UNKNOWN", "Unknown Firebase error")

    val result = mapError("Sign-in", error)

    assertEquals("Sign-in failed: Unknown Firebase error", result.message)
  }

  @Test
  fun mapEmailAuthError_nonFirebaseExceptionUsesOriginalMessage() {
    val result = mapError("Sign-up", IllegalArgumentException("Network error"))

    assertEquals("Sign-up failed: Network error", result.message)
  }

  @Test
  fun mapEmailAuthError_nullMessageUsesFallback() {
    val result = mapError("Sign-in", Exception())

    assertEquals("Sign-in failed: Unexpected error", result.message)
  }
}
