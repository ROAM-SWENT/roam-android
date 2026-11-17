/*
 * Portions of this code were generated with the help of GitHub Copilot.
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.settings

import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.authentication.AuthRepository
import com.github.roamswent.roam.model.interests.UserInterestsRepository
import com.github.roamswent.roam.model.interests.UserInterestsState
import com.github.roamswent.roam.model.profile.ProfileRepository
import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.model.profile.PublicProfileException
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import com.github.roamswent.roam.model.profile.UserProfile
import kotlinx.coroutines.CancellationException
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
  private lateinit var publicProfileRepository: PublicProfileRepository
  private lateinit var credentialManager: CredentialManager
  private lateinit var userInterestsRepository: FakeUserInterestsRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    repository = mock()
    profileRepository = mock()
    publicProfileRepository = mock()
    userInterestsRepository = FakeUserInterestsRepository()
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
    assertEquals(
        SettingsUIState(),
        SettingsViewModel(
                repository,
                profileRepository,
                userInterestsRepository,
                publicProfileRepository,
            )
            .uiState
            .value,
    )
  }

  /** Verifies that persisted interests are loaded into the initial UI state. */
  @Test
  fun initialState_loadsPersistedInterests() {
    userInterestsRepository.updateInterests(listOf("Architecture", "History"))

    val viewModel = newViewModel()

    assertEquals(listOf("Architecture", "History"), viewModel.uiState.value.interests)
  }

  /** Verifies that adding an interest updates state without answering the first-scan prompt. */
  @Test
  fun addInterest_persistsAndPreservesFirstScanPromptState() {
    val viewModel = newViewModel()

    viewModel.addInterest("Architecture")

    assertEquals(listOf("Architecture"), userInterestsRepository.getUserInterests().interests)
    assertEquals(listOf("Architecture"), viewModel.uiState.value.interests)
    assertEquals(false, userInterestsRepository.getUserInterests().hasBeenAsked)
  }

  /** Verifies that removing an interest updates both persisted data and UI state. */
  @Test
  fun removeInterest_persistsAndUpdatesState() {
    userInterestsRepository.updateInterests(listOf("Architecture", "History"))
    val viewModel = newViewModel()

    viewModel.removeInterest("Architecture")

    assertEquals(listOf("History"), userInterestsRepository.getUserInterests().interests)
    assertEquals(listOf("History"), viewModel.uiState.value.interests)
  }

  /** Verifies that clearing an error removes it from the observable UI state. */
  @Test
  fun clearErrorMsg_removesCurrentError() =
      runTest(dispatcher) {
        whenever(profileRepository.loadProfile())
            .thenReturn(Result.failure(IllegalStateException("Profile unavailable")))
        val viewModel = newViewModel()

        viewModel.loadProfile()
        advanceUntilIdle()

        assertEquals(R.string.settings_error_profile_load, viewModel.uiState.value.errorMsg)

        viewModel.clearErrorMsg()

        assertNull(viewModel.uiState.value.errorMsg)
      }

  /** Verifies that camera and location permission updates support granting and revoking access. */
  @Test
  fun permissionUpdates_updateBothStatuses() {
    val viewModel = newViewModel()

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
        val viewModel = newViewModel()

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
        val viewModel = newViewModel()

        viewModel.signOut(credentialManager)
        advanceUntilIdle()

        assertEquals(R.string.settings_error_sign_out, viewModel.uiState.value.errorMsg)
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
        val viewModel = newViewModel()

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
        val viewModel = newViewModel()

        viewModel.loadProfile()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.profile)
        assertFalse(viewModel.uiState.value.profileLoading)
        assertEquals(R.string.settings_error_profile_load, viewModel.uiState.value.errorMsg)
        verify(profileRepository).loadProfile()
      }

  @Test
  fun loadPublicProfile_failureSetsUsernameLoadError() =
      runTest(dispatcher) {
        whenever(profileRepository.loadProfile())
            .thenReturn(Result.success(UserProfile("Name", "user@example.com", "Google")))
        whenever(publicProfileRepository.getCurrentProfile()).thenAnswer {
          throw IllegalStateException("Public profile unavailable")
        }
        val viewModel = newViewModel()

        viewModel.loadProfile()
        advanceUntilIdle()

        assertEquals(
            R.string.settings_username_error_load,
            viewModel.uiState.value.usernameErrorMsg,
        )
      }

  @Test
  fun loadPublicProfile_cancellationIsRethrownWithoutSettingAnError() =
      runTest(dispatcher) {
        val currentProfile = PublicProfile("uid", "traveler")
        whenever(profileRepository.loadProfile())
            .thenReturn(Result.success(UserProfile("Name", "user@example.com", "Google")))
        whenever(publicProfileRepository.getCurrentProfile())
            .thenReturn(currentProfile)
            .thenAnswer { throw CancellationException("Profile lookup cancelled") }
        val viewModel = newViewModel()

        viewModel.loadProfile()
        advanceUntilIdle()
        viewModel.loadProfile()
        advanceUntilIdle()

        assertEquals(currentProfile, viewModel.uiState.value.publicProfile)
        assertNull(viewModel.uiState.value.usernameErrorMsg)
        verify(publicProfileRepository, org.mockito.kotlin.times(2)).getCurrentProfile()
      }

  @Test
  fun renameUsername_successUpdatesDisplayedPublicProfile() =
      runTest(dispatcher) {
        val originalProfile = PublicProfile("uid", "old_name")
        val updatedProfile = PublicProfile("uid", "new_name")
        whenever(publicProfileRepository.getCurrentProfile()).thenReturn(originalProfile)
        whenever(profileRepository.loadProfile())
            .thenReturn(Result.success(UserProfile("Name", "user@example.com", "Google")))
        whenever(publicProfileRepository.renameUsername("new_name")).thenReturn(updatedProfile)
        val viewModel = newViewModel()

        viewModel.loadProfile()
        advanceUntilIdle()
        viewModel.renameUsername(" new_name ")
        advanceUntilIdle()

        assertEquals(updatedProfile, viewModel.uiState.value.publicProfile)
        assertFalse(viewModel.uiState.value.isUsernameSaving)
        assertNull(viewModel.uiState.value.usernameErrorMsg)
        verify(publicProfileRepository).renameUsername("new_name")
      }

  @Test
  fun renameUsername_withoutPublicProfileRepositorySetsUpdateError() {
    val viewModel = SettingsViewModel(repository, profileRepository, userInterestsRepository, null)

    viewModel.renameUsername("new_name")

    assertEquals(
        R.string.settings_username_error_update,
        viewModel.uiState.value.usernameErrorMsg,
    )
    assertFalse(viewModel.uiState.value.isUsernameSaving)
  }

  @Test
  fun renameUsername_ignoresSecondModificationWhileSaving() =
      runTest(dispatcher) {
        val result = CompletableDeferred<PublicProfile>()
        val blockingRepository = BlockingRenamePublicProfileRepository(result)
        val viewModel =
            SettingsViewModel(
                repository,
                profileRepository,
                userInterestsRepository,
                blockingRepository,
            )

        viewModel.renameUsername("first_name")
        runCurrent()
        viewModel.renameUsername("second_name")
        runCurrent()

        assertTrue(viewModel.uiState.value.isUsernameSaving)
        assertEquals(1, blockingRepository.renameCalls)

        result.complete(PublicProfile("uid", "first_name"))
        advanceUntilIdle()

        assertEquals(PublicProfile("uid", "first_name"), viewModel.uiState.value.publicProfile)
        assertFalse(viewModel.uiState.value.isUsernameSaving)
      }

  @Test
  fun renameUsername_cancellationIsRethrownWithoutSettingAnError() =
      runTest(dispatcher) {
        whenever(publicProfileRepository.renameUsername("cancel_name")).thenAnswer {
          throw CancellationException("Username update cancelled")
        }
        val viewModel = newViewModel()

        viewModel.renameUsername("cancel_name")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isUsernameSaving)
        assertNull(viewModel.uiState.value.usernameErrorMsg)
      }

  @Test
  fun renameUsername_unexpectedFailureSetsGenericUpdateError() =
      runTest(dispatcher) {
        whenever(publicProfileRepository.renameUsername("failure_name")).thenAnswer {
          throw IllegalStateException("Update failed")
        }
        val viewModel = newViewModel()

        viewModel.renameUsername("failure_name")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isUsernameSaving)
        assertEquals(
            R.string.settings_username_error_update,
            viewModel.uiState.value.usernameErrorMsg,
        )
      }

  @Test
  fun renameUsername_invalidAndTakenNamesExposeSpecificErrors() =
      runTest(dispatcher) {
        whenever(publicProfileRepository.renameUsername("bad-name")).thenAnswer {
          throw PublicProfileException.InvalidUsername()
        }
        whenever(publicProfileRepository.renameUsername("taken_name")).thenAnswer {
          throw PublicProfileException.UsernameTaken()
        }
        val viewModel = newViewModel()

        viewModel.renameUsername("bad-name")
        advanceUntilIdle()
        assertEquals(
            R.string.settings_username_error_invalid,
            viewModel.uiState.value.usernameErrorMsg,
        )

        viewModel.renameUsername("taken_name")
        advanceUntilIdle()
        assertEquals(
            R.string.settings_username_error_taken,
            viewModel.uiState.value.usernameErrorMsg,
        )
      }

  /** Verifies that a profile load already in progress is not started a second time. */
  @Test
  fun loadProfile_whileAlreadyLoading_ignoresSecondRequest() =
      runTest(dispatcher) {
        val result = CompletableDeferred<Result<UserProfile>>()
        var loadCalls = 0
        val blockingProfileRepository =
            object : ProfileRepository {
              override suspend fun loadProfile(): Result<UserProfile> {
                loadCalls++
                return result.await()
              }
            }
        val viewModel =
            SettingsViewModel(repository, blockingProfileRepository, userInterestsRepository)

        viewModel.loadProfile()
        runCurrent()
        viewModel.loadProfile()

        assertEquals(1, loadCalls)
        result.complete(Result.success(UserProfile("Victor", "victor@epfl.ch", "Google")))
        advanceUntilIdle()
      }

  private fun newViewModel() =
      SettingsViewModel(
          repository,
          profileRepository,
          userInterestsRepository,
          publicProfileRepository,
      )

  private class BlockingRenamePublicProfileRepository(
      private val renameResult: CompletableDeferred<PublicProfile>
  ) : PublicProfileRepository {
    var renameCalls = 0

    override suspend fun getCurrentProfile(): PublicProfile? = null

    override suspend fun getProfile(uid: String): PublicProfile? = null

    override suspend fun registerUsername(username: String): PublicProfile =
        error("Registration is not used in this test.")

    override suspend fun renameUsername(username: String): PublicProfile {
      renameCalls++
      return renameResult.await()
    }

    override suspend fun searchProfiles(prefix: String): List<PublicProfile> = emptyList()
  }

  private class FakeUserInterestsRepository(
      private var hasBeenAsked: Boolean = false,
      private var interests: List<String> = emptyList(),
  ) : UserInterestsRepository {
    override fun getUserInterests() = UserInterestsState(hasBeenAsked, interests)

    override fun updateInterests(interests: List<String>) {
      this.interests = interests
    }

    override fun saveInterests(interests: List<String>) {
      hasBeenAsked = true
      this.interests = interests
    }

    override fun skipInterests() {
      hasBeenAsked = true
      interests = emptyList()
    }
  }
}
