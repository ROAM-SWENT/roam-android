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
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
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
}
