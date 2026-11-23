/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.model.authentication

import androidx.credentials.Credential
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class AuthRepositoryDefaultTest {

  private class UnsupportedAuthRepository : AuthRepository {
    override suspend fun signInWithGoogle(credential: Credential): Result<FirebaseUser> =
        Result.success(mock())

    override fun currentUser(): FirebaseUser? = null

    override fun currentUserFlow(): Flow<FirebaseUser?> = emptyFlow()

    override fun signOut(): Result<Unit> = Result.success(Unit)
  }

  @Test
  fun emailAuth_defaultsReportUnsupportedOperations() = runTest {
    val repository = UnsupportedAuthRepository()

    assertTrue(repository.signUpWithEmail("user@example.com", "password").isFailure)
    assertTrue(repository.signInWithEmail("user@example.com", "password").isFailure)
    assertTrue(repository.sendEmailVerification().isFailure)
    assertTrue(repository.resendEmailVerification().isFailure)
    assertTrue(repository.reloadEmailVerificationStatus().isFailure)
    assertFalse(repository.isEmailVerified())
  }

  @Test
  fun emailAuth_aliasesDelegateToPrimaryMethods() = runTest {
    var signedUp = false
    var signedIn = false
    val repository =
        object : AuthRepository {
          override suspend fun signInWithGoogle(credential: Credential): Result<FirebaseUser> =
              Result.success(mock())

          override fun currentUser(): FirebaseUser? = null

          override fun currentUserFlow(): Flow<FirebaseUser?> = emptyFlow()

          override fun signOut(): Result<Unit> = Result.success(Unit)

          override suspend fun signUpWithEmail(
              email: String,
              password: String,
          ): Result<FirebaseUser> {
            signedUp = true
            return Result.success(mock())
          }

          override suspend fun signInWithEmail(
              email: String,
              password: String,
          ): Result<FirebaseUser> {
            signedIn = true
            return Result.success(mock())
          }
        }

    assertTrue(repository.signUpWithEmailAndPassword("user@example.com", "password").isSuccess)
    assertTrue(repository.signInWithEmailAndPassword("user@example.com", "password").isSuccess)
    assertTrue(signedUp)
    assertTrue(signedIn)
  }

  @Test
  fun emailAuth_aliasesReportUnsupportedByDefault() = runTest {
    val repository: AuthRepository = UnsupportedAuthRepository()

    val signUp = repository.signUpWithEmailAndPassword("user@example.com", "password")
    val signIn = repository.signInWithEmailAndPassword("user@example.com", "password")

    assertTrue(signUp.exceptionOrNull() is UnsupportedOperationException)
    assertTrue(signIn.exceptionOrNull() is UnsupportedOperationException)

    assertEquals(
        "Email/password sign-up is not supported",
        signUp.exceptionOrNull()?.message,
    )
    assertEquals(
        "Email/password sign-in is not supported",
        signIn.exceptionOrNull()?.message,
    )
  }

  @Test
  fun emailAuth_defaultFailuresContainExpectedMessages() = runTest {
    val repository: AuthRepository = UnsupportedAuthRepository()

    assertEquals(
        "Email/password sign-up is not supported",
        repository.signUpWithEmail("user@example.com", "password").exceptionOrNull()?.message,
    )
    assertEquals(
        "Email/password sign-in is not supported",
        repository.signInWithEmail("user@example.com", "password").exceptionOrNull()?.message,
    )
    assertEquals(
        "Email verification is not supported",
        repository.sendEmailVerification().exceptionOrNull()?.message,
    )
    assertEquals(
        "Email verification status is not supported",
        repository.reloadEmailVerificationStatus().exceptionOrNull()?.message,
    )
  }

  @Test
  fun resendEmailVerification_delegatesToSendEmailVerification() = runTest {
    var sendCalls = 0

    val repository =
        object : AuthRepository {
          override suspend fun signInWithGoogle(
              credential: Credential,
          ): Result<FirebaseUser> = Result.success(mock())

          override fun currentUser(): FirebaseUser? = null

          override fun currentUserFlow(): Flow<FirebaseUser?> = emptyFlow()

          override fun signOut(): Result<Unit> = Result.success(Unit)

          override suspend fun sendEmailVerification(): Result<Unit> {
            sendCalls++
            return Result.success(Unit)
          }
        }

    val result = repository.resendEmailVerification()

    assertTrue(result.isSuccess)
    assertEquals(1, sendCalls)
  }
}
