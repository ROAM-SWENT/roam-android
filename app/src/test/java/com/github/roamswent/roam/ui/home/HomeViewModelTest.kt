/*
 * Co-authored-by: Mistral Vibe <vibe@mistral.ai>
 */

package com.github.roamswent.roam.ui.home

import com.github.roamswent.roam.model.authentication.AuthRepository
import com.google.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private lateinit var repository: AuthRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    repository = mockk()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun mockUser(displayName: String?, email: String?): FirebaseUser {
    val user = mockk<FirebaseUser>()
    every { user.displayName } returns displayName
    every { user.email } returns email
    return user
  }

  @Test
  fun seedUsesCurrentUserImmediately() {
    val user = mockUser(displayName = "Ada Lovelace", email = null)
    every { repository.currentUser() } returns user
    every { repository.currentUserFlow() } returns emptyFlow()

    val viewModel = HomeViewModel(repository)

    assertEquals("Ada Lovelace", viewModel.userName.value)
  }

  @Test
  fun signedOutSeedFallsBackToDefault() {
    every { repository.currentUser() } returns null
    every { repository.currentUserFlow() } returns emptyFlow()

    val viewModel = HomeViewModel(repository)

    assertEquals(HomeViewModel.DEFAULT_USER_NAME, viewModel.userName.value)
  }

  @Test
  fun seedFallsBackToEmailLocalPart() {
    val user = mockUser(displayName = "", email = "grace.hopper@example.com")
    every { repository.currentUser() } returns user
    every { repository.currentUserFlow() } returns emptyFlow()

    val viewModel = HomeViewModel(repository)

    assertEquals("grace.hopper", viewModel.userName.value)
  }

  @Test
  fun seedHandlesBlankDisplayName() {
    val user = mockUser(displayName = " ", email = null)
    every { repository.currentUser() } returns user
    every { repository.currentUserFlow() } returns emptyFlow()

    val viewModel = HomeViewModel(repository)

    assertEquals(HomeViewModel.DEFAULT_USER_NAME, viewModel.userName.value)
  }

  @Test
  fun flowUpdatesNameOnAuthChange() =
      runTest(dispatcher) {
        val user = mockUser(displayName = "Grace Hopper", email = null)
        every { repository.currentUser() } returns null
        every { repository.currentUserFlow() } returns flowOf(user)

        val viewModel = HomeViewModel(repository)
        assertEquals(HomeViewModel.DEFAULT_USER_NAME, viewModel.userName.value)

        advanceUntilIdle()

        assertEquals("Grace Hopper", viewModel.userName.value)
      }

  @Test
  fun flowSignOutRestoresDefault() =
      runTest(dispatcher) {
        every { repository.currentUser() } returns
            mockUser(displayName = "Ada Lovelace", email = null)
        every { repository.currentUserFlow() } returns flowOf(null)

        val viewModel = HomeViewModel(repository)
        assertEquals("Ada Lovelace", viewModel.userName.value)

        advanceUntilIdle()

        assertEquals(HomeViewModel.DEFAULT_USER_NAME, viewModel.userName.value)
      }

  @Test
  fun flowFallsBackToEmailLocalPart() =
      runTest(dispatcher) {
        val user = mockUser(displayName = "", email = "grace.hopper@example.com")
        every { repository.currentUser() } returns null
        every { repository.currentUserFlow() } returns flowOf(user)

        val viewModel = HomeViewModel(repository)

        advanceUntilIdle()

        assertEquals("grace.hopper", viewModel.userName.value)
      }

  @Test
  fun seedImmediatelyThenFlowChange() =
      runTest(dispatcher) {
        val userA = mockUser(displayName = "Ada Lovelace", email = null)
        val userB = mockUser(displayName = "Grace Hopper", email = null)
        every { repository.currentUser() } returns userA
        every { repository.currentUserFlow() } returns flowOf(userB)

        val viewModel = HomeViewModel(repository)
        assertEquals("Ada Lovelace", viewModel.userName.value)

        advanceUntilIdle()

        assertEquals("Grace Hopper", viewModel.userName.value)
      }
}
