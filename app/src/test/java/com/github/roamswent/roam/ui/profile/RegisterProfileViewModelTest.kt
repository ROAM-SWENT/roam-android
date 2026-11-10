/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.profile

import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import kotlinx.coroutines.CancellationException
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
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterProfileViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val repository: PublicProfileRepository = mock()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun checkCurrentProfile_missingProfileRequiresRegistration() =
      runTest(dispatcher) {
        whenever(repository.getCurrentProfile()).thenReturn(null)
        val viewModel = RegisterProfileViewModel(repository)

        viewModel.checkCurrentProfile()
        advanceUntilIdle()

        assertEquals(
            ProfileRegistrationStatus.REGISTRATION_REQUIRED,
            viewModel.uiState.value.status,
        )
      }

  @Test
  fun checkCurrentProfile_existingProfileMarksUserRegistered() =
      runTest(dispatcher) {
        whenever(repository.getCurrentProfile()).thenReturn(PublicProfile("uid", "traveler"))
        val viewModel = RegisterProfileViewModel(repository)

        viewModel.checkCurrentProfile()
        advanceUntilIdle()

        assertEquals(ProfileRegistrationStatus.REGISTERED, viewModel.uiState.value.status)
      }

  @Test
  fun checkCurrentProfile_failureSetsErrorStatusAndMessage() =
      runTest(dispatcher) {
        val failure = IllegalStateException("Profile lookup failed")
        whenever(repository.getCurrentProfile()).thenThrow(failure)
        val viewModel = RegisterProfileViewModel(repository)

        viewModel.checkCurrentProfile()
        advanceUntilIdle()

        assertEquals(ProfileRegistrationStatus.ERROR, viewModel.uiState.value.status)
        assertEquals(failure.localizedMessage, viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
      }

  @Test
  fun checkCurrentProfile_ignoresDuplicateChecksWhileLoading() =
      runTest(dispatcher) {
        val result = CompletableDeferred<PublicProfile?>()
        val blockingRepository = BlockingPublicProfileRepository(result)
        val viewModel = RegisterProfileViewModel(blockingRepository)

        viewModel.checkCurrentProfile()
        runCurrent()
        viewModel.checkCurrentProfile()
        viewModel.registerUsername("too_soon")
        assertEquals(0, blockingRepository.registrations)
        result.complete(null)
        advanceUntilIdle()

        assertEquals(1, blockingRepository.profileLookups)
        assertEquals(
            ProfileRegistrationStatus.REGISTRATION_REQUIRED,
            viewModel.uiState.value.status,
        )
      }

  @Test
  fun checkCurrentProfile_cancellationLeavesTheCheckCancelled() =
      runTest(dispatcher) {
        whenever(repository.getCurrentProfile())
            .thenThrow(CancellationException("Profile lookup cancelled"))
        val viewModel = RegisterProfileViewModel(repository)

        viewModel.checkCurrentProfile()
        advanceUntilIdle()

        assertEquals(ProfileRegistrationStatus.CHECKING, viewModel.uiState.value.status)
        assertEquals(true, viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
      }

  @Test
  fun registerUsername_beforeProfileCheckIsIgnored() =
      runTest(dispatcher) {
        val viewModel = RegisterProfileViewModel(repository)

        viewModel.registerUsername("too_soon")
        advanceUntilIdle()

        assertEquals(ProfileRegistrationStatus.UNKNOWN, viewModel.uiState.value.status)
        assertFalse(viewModel.uiState.value.isLoading)
        verify(repository, never()).registerUsername("too_soon")
      }

  @Test
  fun registerUsername_cancellationDoesNotBecomeARegistrationError() =
      runTest(dispatcher) {
        whenever(repository.getCurrentProfile()).thenReturn(null)
        whenever(repository.registerUsername("traveler"))
            .thenThrow(CancellationException("Registration cancelled"))
        val viewModel = RegisterProfileViewModel(repository)
        viewModel.checkCurrentProfile()
        advanceUntilIdle()

        viewModel.registerUsername("traveler")
        advanceUntilIdle()

        assertEquals(
            ProfileRegistrationStatus.REGISTRATION_REQUIRED,
            viewModel.uiState.value.status,
        )
        assertEquals(true, viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
      }

  @Test
  fun registerUsername_successMarksUserRegistered() =
      runTest(dispatcher) {
        whenever(repository.getCurrentProfile()).thenReturn(null)
        whenever(repository.registerUsername("traveler"))
            .thenReturn(PublicProfile("uid", "traveler"))
        val viewModel = RegisterProfileViewModel(repository)

        viewModel.checkCurrentProfile()
        advanceUntilIdle()
        viewModel.registerUsername("traveler")
        advanceUntilIdle()

        assertEquals(ProfileRegistrationStatus.REGISTERED, viewModel.uiState.value.status)
      }

  @Test
  fun registerUsername_failureKeepsRegistrationAvailableAndReportsError() =
      runTest(dispatcher) {
        val failure = IllegalStateException("That username is already taken.")
        whenever(repository.getCurrentProfile()).thenReturn(null)
        whenever(repository.registerUsername("taken_name")).thenThrow(failure)
        val viewModel = RegisterProfileViewModel(repository)

        viewModel.checkCurrentProfile()
        advanceUntilIdle()
        viewModel.registerUsername("taken_name")
        advanceUntilIdle()

        assertEquals(
            ProfileRegistrationStatus.REGISTRATION_REQUIRED,
            viewModel.uiState.value.status,
        )
        assertEquals(failure.localizedMessage, viewModel.uiState.value.errorMessage)
      }

  private class BlockingPublicProfileRepository(
      private val result: CompletableDeferred<PublicProfile?>
  ) : PublicProfileRepository {
    var profileLookups = 0
      private set

    var registrations = 0
      private set

    override suspend fun getCurrentProfile(): PublicProfile? {
      profileLookups++
      return result.await()
    }

    override suspend fun getProfile(uid: String): PublicProfile? = null

    override suspend fun registerUsername(username: String): PublicProfile {
      registrations++
      return PublicProfile("uid", username)
    }

    override suspend fun renameUsername(username: String): PublicProfile =
        error("Not used in this test")

    override suspend fun searchProfiles(prefix: String): List<PublicProfile> = emptyList()
  }
}
