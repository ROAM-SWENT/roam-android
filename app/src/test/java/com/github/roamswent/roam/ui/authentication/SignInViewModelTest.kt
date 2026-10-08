/*
 * Portions of this code were generated and/or refined with the help of ChatGPT.
 * Portions of this code were generated with the help of GitHub Copilot.
 */

package com.github.roamswent.roam.ui.authentication

import android.content.Context
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.authentication.AuthRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private lateinit var repository: AuthRepository
  private lateinit var context: Context
  private lateinit var credentialManager: CredentialManager
  private lateinit var credential: Credential
  private lateinit var response: GetCredentialResponse
  private lateinit var user: FirebaseUser

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    repository = mock()
    context = mock()
    credentialManager = mock()
    credential = mock()
    response = GetCredentialResponse(credential)
    user = mock()
    whenever(context.getString(R.string.default_web_client_id)).thenReturn("client-id")
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** Verifies that the ViewModel starts with the default authentication UI state. */
  @Test
  fun initialState_isEmpty() {
    val viewModel = SignInViewModel(repository)

    assertEquals(AuthUIState(), viewModel.uiState.value)
  }

  /**
   * Verifies that clearing the error message removes the current error without affecting the other
   * authentication state values.
   */
  @Test
  fun clearErrorMsg_removesErrorOnly() {
    val viewModel = SignInViewModel(repository)
    viewModel.clearErrorMsg()

    assertNull(viewModel.uiState.value.errorMsg)
  }

  /**
   * Verifies that a successful sign-in updates the authenticated user, stops the loading state, and
   * leaves the UI without an error or signed-out status.
   */
  @Test
  fun signIn_success_updatesUserAndStopsLoading() =
      runTest(dispatcher) {
        org.mockito.kotlin
            .doReturn(response)
            .whenever(credentialManager)
            .getCredential(any<Context>(), any<GetCredentialRequest>())
        whenever(repository.signInWithGoogle(credential)).thenReturn(Result.success(user))
        val viewModel = SignInViewModel(repository)

        viewModel.signIn(context, credentialManager)
        advanceUntilIdle()

        assertEquals(user, viewModel.uiState.value.user)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMsg)
        assertFalse(viewModel.uiState.value.signedOut)
        verify(repository).signInWithGoogle(credential)
      }

  /**
   * Verifies that a repository sign-in failure updates the UI with the failure message, marks the
   * user as signed out, and stops loading.
   */
  @Test
  fun signIn_repositoryFailure_updatesErrorAndSignedOut() =
      runTest(dispatcher) {
        org.mockito.kotlin
            .doReturn(response)
            .whenever(credentialManager)
            .getCredential(any<Context>(), any<GetCredentialRequest>())
        whenever(repository.signInWithGoogle(credential))
            .thenReturn(Result.failure(IllegalStateException("invalid account")))
        val viewModel = SignInViewModel(repository)

        viewModel.signIn(context, credentialManager)
        advanceUntilIdle()

        assertEquals("invalid account", viewModel.uiState.value.errorMsg)
        assertTrue(viewModel.uiState.value.signedOut)
        assertNull(viewModel.uiState.value.user)
        assertFalse(viewModel.uiState.value.isLoading)
      }

  /**
   * Verifies that cancelling the credential selection updates the UI with a cancellation error,
   * marks the user as signed out, and does not attempt repository authentication.
   */
  @Test
  fun signIn_cancellation_updatesCancellationError() =
      runTest(dispatcher) {
        org.mockito.kotlin
            .doAnswer { throw GetCredentialCancellationException("cancelled") }
            .whenever(credentialManager)
            .getCredential(any<Context>(), any<GetCredentialRequest>())
        val viewModel = SignInViewModel(repository)

        viewModel.signIn(context, credentialManager)
        advanceUntilIdle()

        assertEquals("Sign-in cancelled", viewModel.uiState.value.errorMsg)
        assertTrue(viewModel.uiState.value.signedOut)
        verify(repository, never()).signInWithGoogle(any())
      }

  /**
   * Verifies that a credential retrieval failure updates the UI with the corresponding credential
   * error message and marks the user as signed out.
   */
  @Test
  fun signIn_credentialFailure_updatesCredentialError() =
      runTest(dispatcher) {
        val exception = mock<GetCredentialException>()
        whenever(exception.localizedMessage).thenReturn("credential failure")
        org.mockito.kotlin
            .doAnswer { throw exception }
            .whenever(credentialManager)
            .getCredential(any<Context>(), any<GetCredentialRequest>())
        val viewModel = SignInViewModel(repository)

        viewModel.signIn(context, credentialManager)
        advanceUntilIdle()

        assertEquals(
            "Failed to get credentials: credential failure",
            viewModel.uiState.value.errorMsg,
        )
        assertTrue(viewModel.uiState.value.signedOut)
      }

  /**
   * Verifies that an unexpected exception during credential retrieval is converted into an
   * appropriate error message and marks the user as signed out.
   */
  @Test
  fun signIn_unexpectedFailure_updatesUnexpectedError() =
      runTest(dispatcher) {
        org.mockito.kotlin
            .doAnswer { throw IllegalStateException("boom") }
            .whenever(credentialManager)
            .getCredential(any<Context>(), any<GetCredentialRequest>())
        val viewModel = SignInViewModel(repository)

        viewModel.signIn(context, credentialManager)
        advanceUntilIdle()

        assertEquals("Unexpected error: boom", viewModel.uiState.value.errorMsg)
        assertTrue(viewModel.uiState.value.signedOut)
      }

  /**
   * Verifies that a second sign-in attempt made while an existing sign-in operation is still
   * loading is ignored.
   */
  @Test
  fun signIn_whileLoading_isIgnored() =
      runTest(dispatcher) {
        val result = CompletableDeferred<Result<FirebaseUser>>()
        repository =
            object : AuthRepository {
              override suspend fun signInWithGoogle(credential: Credential): Result<FirebaseUser> =
                  result.await()

              override fun signOut(): Result<Unit> = Result.success(Unit)
            }
        org.mockito.kotlin
            .doReturn(response)
            .whenever(credentialManager)
            .getCredential(any<Context>(), any<GetCredentialRequest>())
        val viewModel = SignInViewModel(repository)

        viewModel.signIn(context, credentialManager)
        runCurrent()
        viewModel.signIn(context, credentialManager)
        result.complete(Result.success(user))
        advanceUntilIdle()

        verify(credentialManager, org.mockito.kotlin.times(1))
            .getCredential(any<Context>(), any<GetCredentialRequest>())
      }
}
