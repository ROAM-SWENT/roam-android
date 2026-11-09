/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.profile

import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
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
        assertEquals(false, viewModel.uiState.value.isLoading)
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
}
