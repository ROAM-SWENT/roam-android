/*
 * Portions of this code were generated with the help of GitHub Copilot.
 */

package com.github.roamswent.roam.ui.settings

import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.github.roamswent.roam.model.authentication.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private lateinit var repository: AuthRepository
  private lateinit var credentialManager: CredentialManager

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    repository = mock()
    credentialManager = mock()
    runBlocking {
      doReturn(Unit)
          .whenever(credentialManager)
          .clearCredentialState(any<ClearCredentialStateRequest>())
    }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** Verifies that the ViewModel starts with the default settings UI state. */
  @Test
  fun initialState_isEmpty() {
    assertEquals(SettingsUIState(), SettingsViewModel(repository).uiState.value)
  }

  /** Verifies that camera and location permission updates support granting and revoking access. */
  @Test
  fun permissionUpdates_updateBothStatuses() {
    val viewModel = SettingsViewModel(repository)

    viewModel.setCameraPermissionGranted(true)
    viewModel.setLocationPermissionGranted(true)

    assertTrue(viewModel.uiState.value.cameraPermissionGranted)
    assertTrue(viewModel.uiState.value.locationPermissionGranted)
    assertFalse(viewModel.uiState.value.signedOut)
    assertNull(viewModel.uiState.value.errorMsg)

    viewModel.setCameraPermissionGranted(false)
    viewModel.setLocationPermissionGranted(false)

    assertFalse(viewModel.uiState.value.cameraPermissionGranted)
    assertFalse(viewModel.uiState.value.locationPermissionGranted)
  }

  /**
   * Verifies that successful sign-out marks the user signed out and clears the credential state.
   */
  @Test
  fun signOut_success_marksSignedOutAndClearsCredentials() =
      runTest(dispatcher) {
        whenever(repository.signOut()).thenReturn(Result.success(Unit))
        val viewModel = SettingsViewModel(repository)

        viewModel.signOut(credentialManager)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.signedOut)
        assertNull(viewModel.uiState.value.errorMsg)
        verify(repository).signOut()
        verify(credentialManager).clearCredentialState(any<ClearCredentialStateRequest>())
      }

  /** Verifies that failed sign-out exposes the error without clearing the credential state. */
  @Test
  fun signOut_failure_setsErrorWithoutClearingCredentials() =
      runTest(dispatcher) {
        whenever(repository.signOut())
            .thenReturn(Result.failure(IllegalStateException("Unable to log out")))
        val viewModel = SettingsViewModel(repository)

        viewModel.signOut(credentialManager)
        advanceUntilIdle()

        assertEquals("Unable to log out", viewModel.uiState.value.errorMsg)
        assertFalse(viewModel.uiState.value.signedOut)
        verify(repository).signOut()
        verifyNoInteractions(credentialManager)
      }
}
