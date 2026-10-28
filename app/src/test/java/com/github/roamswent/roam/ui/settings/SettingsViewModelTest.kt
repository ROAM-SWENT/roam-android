/*
 * Portions of this code were generated with the help of GitHub Copilot.
 */

package com.github.roamswent.roam.ui.settings

import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.github.roamswent.roam.model.authentication.AuthRepository
import com.github.roamswent.roam.model.profile.ProfileRepository
import com.github.roamswent.roam.model.profile.UserProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private lateinit var repository: AuthRepository
  private lateinit var profileRepository: ProfileRepository
  private lateinit var credentialManager: CredentialManager

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    repository = mock()
    profileRepository = mock()
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
    assertEquals(SettingsUIState(), SettingsViewModel(repository, profileRepository).uiState.value)
  }

  /** Verifies that clearing an error removes it from the observable UI state. */
  @Test
  fun clearErrorMsg_removesCurrentError() =
      runTest(dispatcher) {
        whenever(profileRepository.loadProfile())
            .thenReturn(Result.failure(IllegalStateException("Profile unavailable")))
        val viewModel = SettingsViewModel(repository, profileRepository)

        viewModel.loadProfile()
        advanceUntilIdle()

        assertEquals("Profile unavailable", viewModel.uiState.value.errorMsg)

        viewModel.clearErrorMsg()

        assertNull(viewModel.uiState.value.errorMsg)
      }

  /** Verifies that camera and location permission updates support granting and revoking access. */
  @Test
  fun permissionUpdates_updateBothStatuses() {
    val viewModel = SettingsViewModel(repository, profileRepository)

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
        val viewModel = SettingsViewModel(repository, profileRepository)

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
        val viewModel = SettingsViewModel(repository, profileRepository)

        viewModel.signOut(credentialManager)
        advanceUntilIdle()

        assertEquals("Unable to log out", viewModel.uiState.value.errorMsg)
        assertFalse(viewModel.uiState.value.signedOut)
        verify(repository).signOut()
        verifyNoInteractions(credentialManager)
      }

  /** Verifies that a successful profile load is exposed as a single profile value. */
  @Test
  fun loadProfile_success_updatesProfileAndStopsLoading() =
      runTest(dispatcher) {
        val profile = UserProfile("Victor", "victor@epfl.ch", "Google")
        whenever(profileRepository.loadProfile()).thenReturn(Result.success(profile))
        val viewModel = SettingsViewModel(repository, profileRepository)

        viewModel.loadProfile()
        assertTrue(viewModel.uiState.value.profileLoading.not())
        advanceUntilIdle()

        assertEquals(profile, viewModel.uiState.value.profile)
        assertFalse(viewModel.uiState.value.profileLoading)
        assertNull(viewModel.uiState.value.errorMsg)
        verify(profileRepository).loadProfile()
      }

  /** Verifies that a failed profile load exposes the error and clears the loading state. */
  @Test
  fun loadProfile_failure_setsErrorAndStopsLoading() =
      runTest(dispatcher) {
        whenever(profileRepository.loadProfile())
            .thenReturn(Result.failure(IllegalStateException("Profile unavailable")))
        val viewModel = SettingsViewModel(repository, profileRepository)

        viewModel.loadProfile()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.profile)
        assertFalse(viewModel.uiState.value.profileLoading)
        assertEquals("Profile unavailable", viewModel.uiState.value.errorMsg)
        verify(profileRepository).loadProfile()
      }

  /** Verifies that a profile load already in progress is not started a second time. */
  @Test
  fun loadProfile_whileAlreadyLoading_ignoresSecondRequest() =
      runTest(dispatcher) {
        val result = CompletableDeferred<Result<UserProfile>>()
        var loadCalls = 0
        val blockingProfileRepository = object : ProfileRepository {
          override suspend fun loadProfile(): Result<UserProfile> {
            loadCalls++
            return result.await()
          }
        }
        val viewModel = SettingsViewModel(repository, blockingProfileRepository)

        viewModel.loadProfile()
        runCurrent()
        viewModel.loadProfile()

        assertEquals(1, loadCalls)
        result.complete(Result.success(UserProfile("Victor", "victor@epfl.ch", "Google")))
        advanceUntilIdle()
      }
}
