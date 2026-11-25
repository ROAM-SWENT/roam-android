/*
 * Co-authored-by: Mistral Vibe <vibe@mistral.ai>
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.home

import com.github.roamswent.roam.model.authentication.AuthRepository
import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
    HomeViewModel.clearProfileCacheForTests()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun mockUser(displayName: String?, email: String?): FirebaseUser {
    val user = mockk<FirebaseUser>()
    every { user.uid } returns "user-id"
    every { user.displayName } returns displayName
    every { user.email } returns email
    return user
  }

  @Test
  fun publicProfileLoadsAvatarBytes() =
      runTest(dispatcher) {
        val user = mockUser(displayName = "Google Name", email = null)
        every { repository.currentUser() } returns user
        every { repository.currentUserFlow() } returns emptyFlow()
        val avatarBytes = byteArrayOf(1, 2, 3)
        val publicProfileRepository = mockk<PublicProfileRepository>()
        coEvery { publicProfileRepository.getCurrentProfile() } returns
            PublicProfile("uid", "app_username", avatarBytes = avatarBytes)

        val viewModel = HomeViewModel(repository, publicProfileRepository)
        advanceUntilIdle()

        assertEquals("app_username", viewModel.userName.value)
        assertEquals(true, viewModel.avatarBytes.value.contentEquals(avatarBytes))
      }

  @Test
  fun recreatedViewModelImmediatelyUsesCachedProfile() =
      runTest(dispatcher) {
        val user = mockUser(displayName = "Google Name", email = null)
        every { repository.currentUser() } returns user
        every { repository.currentUserFlow() } returns emptyFlow()
        val publicProfileRepository = mockk<PublicProfileRepository>()
        val avatarBytes = byteArrayOf(9, 8, 7)
        coEvery { publicProfileRepository.getCurrentProfile() } returns
            PublicProfile("uid", "cached_name", avatarBytes = avatarBytes)

        val first = HomeViewModel(repository, publicProfileRepository)
        advanceUntilIdle()
        val recreated = HomeViewModel(repository, publicProfileRepository)

        assertEquals("cached_name", recreated.userName.value)
        assertEquals(true, recreated.avatarBytes.value.contentEquals(avatarBytes))
      }

  @Test
  fun changingUsersClearsCachedProfileBeforeNewLookupCompletes() =
      runTest(dispatcher) {
        val userA = mockUser(displayName = "User A", email = null)
        val userB = mockUser(displayName = "User B", email = null)
        every { userA.uid } returns "uid-a"
        every { userB.uid } returns "uid-b"
        every { repository.currentUser() } returns userA
        every { repository.currentUserFlow() } returns flowOf(userB)
        val secondLookup = CompletableDeferred<PublicProfile?>()
        val publicProfileRepository = mockk<PublicProfileRepository>()
        var lookupCount = 0
        coEvery { publicProfileRepository.getCurrentProfile() } coAnswers
            {
              if (lookupCount++ == 0) {
                PublicProfile("uid-a", "name-a", avatarBytes = byteArrayOf(1))
              } else {
                secondLookup.await()
              }
            }

        val viewModel = HomeViewModel(repository, publicProfileRepository)
        runCurrent()

        assertEquals(HomeViewModel.DEFAULT_USER_NAME, viewModel.userName.value)
        assertEquals(null, viewModel.avatarBytes.value)

        secondLookup.complete(PublicProfile("uid-b", "name-b", avatarBytes = byteArrayOf(2)))
        advanceUntilIdle()
        assertEquals("name-b", viewModel.userName.value)
        assertEquals(true, viewModel.avatarBytes.value.contentEquals(byteArrayOf(2)))
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

  @Test
  fun publicUsernameOverridesAuthenticationDisplayName() =
      runTest(dispatcher) {
        val user = mockUser(displayName = "Ada Lovelace", email = null)
        every { user.uid } returns "uid"
        every { repository.currentUser() } returns user
        every { repository.currentUserFlow() } returns emptyFlow()
        val publicProfileRepository = mockk<PublicProfileRepository>()
        coEvery { publicProfileRepository.getCurrentProfile() } returns
            PublicProfile("uid", "roamer")

        val viewModel = HomeViewModel(repository, publicProfileRepository)
        advanceUntilIdle()

        assertEquals("roamer", viewModel.userName.value)
      }

  @Test
  fun refreshUserNamePicksUpUsernameRenamedInSettings() =
      runTest(dispatcher) {
        val user = mockUser(displayName = "Ada Lovelace", email = null)
        every { user.uid } returns "uid"
        every { repository.currentUser() } returns user
        every { repository.currentUserFlow() } returns emptyFlow()
        val publicProfileRepository = mockk<PublicProfileRepository>()
        var username = "old_name"
        coEvery { publicProfileRepository.getCurrentProfile() } coAnswers
            {
              PublicProfile("uid", username)
            }

        val viewModel = HomeViewModel(repository, publicProfileRepository)
        advanceUntilIdle()
        username = "new_name"
        viewModel.refreshUserName()
        advanceUntilIdle()

        assertEquals("new_name", viewModel.userName.value)
      }

  @Test
  fun refreshUserNameKeepsCurrentUsernameVisibleUntilProfileLoads() =
      runTest(dispatcher) {
        val user = mockUser(displayName = "Ada Lovelace", email = null)
        every { user.uid } returns "uid"
        every { repository.currentUser() } returns user
        every { repository.currentUserFlow() } returns emptyFlow()
        val refreshedProfile = CompletableDeferred<PublicProfile?>()
        val publicProfileRepository = mockk<PublicProfileRepository>()
        var lookupCount = 0
        coEvery { publicProfileRepository.getCurrentProfile() } coAnswers
            {
              if (lookupCount++ == 0) PublicProfile("uid", "old_name") else refreshedProfile.await()
            }

        val viewModel = HomeViewModel(repository, publicProfileRepository)
        advanceUntilIdle()
        viewModel.refreshUserName()
        runCurrent()

        assertEquals("old_name", viewModel.userName.value)

        refreshedProfile.complete(PublicProfile("uid", "new_name"))
        advanceUntilIdle()

        assertEquals("new_name", viewModel.userName.value)
      }

  @Test
  fun authUidChangeClearsUsernameUntilNewPublicProfileLoads() =
      runTest(dispatcher) {
        val userA = mockUser(displayName = "Ada Lovelace", email = null)
        val userB = mockUser(displayName = "Grace Hopper", email = null)
        every { userA.uid } returns "uid-a"
        every { userB.uid } returns "uid-b"
        every { repository.currentUser() } returns userA
        every { repository.currentUserFlow() } returns flowOf(userB)
        val newProfile = CompletableDeferred<PublicProfile?>()
        val publicProfileRepository = mockk<PublicProfileRepository>()
        var lookupCount = 0
        coEvery { publicProfileRepository.getCurrentProfile() } coAnswers
            {
              if (lookupCount++ == 0) PublicProfile("uid-a", "ada") else newProfile.await()
            }

        val viewModel = HomeViewModel(repository, publicProfileRepository)
        runCurrent()

        assertEquals(2, lookupCount)
        assertEquals(HomeViewModel.DEFAULT_USER_NAME, viewModel.userName.value)

        newProfile.complete(PublicProfile("uid-b", "grace"))
        advanceUntilIdle()

        assertEquals("grace", viewModel.userName.value)
      }

  @Test
  fun cancelledPublicProfileLookupIsNotConvertedToFallback() =
      runTest(dispatcher) {
        val user = mockUser(displayName = "Ada Lovelace", email = null)
        every { user.uid } returns "uid"
        every { repository.currentUser() } returns user
        every { repository.currentUserFlow() } returns emptyFlow()
        val firstLookup = CompletableDeferred<PublicProfile?>()
        val publicProfileRepository = mockk<PublicProfileRepository>()
        var lookupCount = 0
        coEvery { publicProfileRepository.getCurrentProfile() } coAnswers
            {
              if (lookupCount++ == 0) firstLookup.await()
              else throw CancellationException("Profile lookup cancelled")
            }

        val viewModel = HomeViewModel(repository, publicProfileRepository)
        runCurrent()
        assertEquals(1, lookupCount)

        viewModel.refreshUserName()
        runCurrent()

        assertEquals(2, lookupCount)
        assertEquals(HomeViewModel.DEFAULT_USER_NAME, viewModel.userName.value)
      }

  @Test
  fun initialPublicProfileLookupFailurePreservesDefaultUsername() =
      runTest(dispatcher) {
        val user = mockUser(displayName = "Ada Lovelace", email = null)
        every { user.uid } returns "uid"
        every { repository.currentUser() } returns user
        every { repository.currentUserFlow() } returns emptyFlow()
        val publicProfileRepository = mockk<PublicProfileRepository>()
        coEvery { publicProfileRepository.getCurrentProfile() } throws
            IllegalStateException("Profile lookup failed")

        val viewModel = HomeViewModel(repository, publicProfileRepository)
        advanceUntilIdle()

        assertEquals(HomeViewModel.DEFAULT_USER_NAME, viewModel.userName.value)
      }

  @Test
  fun missingOrFailedRefreshPreservesLastKnownUsername() =
      runTest(dispatcher) {
        val user = mockUser(displayName = "Ada Lovelace", email = null)
        every { user.uid } returns "uid"
        every { repository.currentUser() } returns user
        every { repository.currentUserFlow() } returns emptyFlow()
        val publicProfileRepository = mockk<PublicProfileRepository>()
        var lookupCount = 0
        coEvery { publicProfileRepository.getCurrentProfile() } coAnswers
            {
              when (lookupCount++) {
                0 -> PublicProfile("uid", "roamer")
                1 -> null
                else -> throw IllegalStateException("Profile lookup failed")
              }
            }

        val viewModel = HomeViewModel(repository, publicProfileRepository)
        advanceUntilIdle()
        assertEquals("roamer", viewModel.userName.value)

        viewModel.refreshUserName()
        advanceUntilIdle()
        assertEquals("roamer", viewModel.userName.value)

        viewModel.refreshUserName()
        advanceUntilIdle()

        assertEquals("roamer", viewModel.userName.value)
      }
}
