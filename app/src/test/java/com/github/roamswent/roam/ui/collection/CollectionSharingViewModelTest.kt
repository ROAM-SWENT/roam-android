// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.friend.Friendship
import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionSharingViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val friendRepository = mockk<FriendRepository>()
  private val profileRepository = mockk<PublicProfileRepository>()

  @Before
  fun setMainDispatcher() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun resetMainDispatcher() {
    Dispatchers.resetMain()
  }

  @Test
  fun loadsAcceptedFriendsAndTheirUsernames() =
      runTest(dispatcher) {
        coEvery { friendRepository.getFriends() } returns
            listOf(Friendship("friendship-a", "friend-a"), Friendship("friendship-b", "friend-b"))
        coEvery { profileRepository.getProfile("friend-a") } returns
            PublicProfile("friend-a", "Alice")
        coEvery { profileRepository.getProfile("friend-b") } returns null
        val viewModel = CollectionSharingViewModel(friendRepository, profileRepository)

        viewModel.loadFriends()
        assertTrue(viewModel.uiState.value.isLoadingFriends)
        advanceUntilIdle()

        assertEquals(
            listOf(
                CollectionFriend("friend-a", "Alice"),
                CollectionFriend("friend-b", "friend-b"),
            ),
            viewModel.uiState.value.friends,
        )
        assertFalse(viewModel.uiState.value.isLoadingFriends)
        assertEquals(null, viewModel.uiState.value.friendsError)
      }

  @Test
  fun friendLoadFailureIsExposedAndCanBeRetried() =
      runTest(dispatcher) {
        val failure = IllegalStateException("offline")
        coEvery { friendRepository.getFriends() } throws failure
        val viewModel = CollectionSharingViewModel(friendRepository, profileRepository)

        viewModel.loadFriends()
        advanceUntilIdle()

        assertEquals(failure, viewModel.uiState.value.friendsError)
        assertFalse(viewModel.uiState.value.isLoadingFriends)
        viewModel.retryLoadingFriends()
        advanceUntilIdle()

        coVerify(exactly = 2) { friendRepository.getFriends() }
      }

  @Test
  fun subsequentLoadsRefreshFriendsAfterSuccess() =
      runTest(dispatcher) {
        coEvery { friendRepository.getFriends() } returns
            emptyList() andThen
            listOf(Friendship("id", "friend"))
        coEvery { profileRepository.getProfile("friend") } returns PublicProfile("friend", "Alice")
        val viewModel = CollectionSharingViewModel(friendRepository, profileRepository)

        viewModel.loadFriends()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.friends.isEmpty())

        viewModel.loadFriends()
        assertTrue(viewModel.uiState.value.isLoadingFriends)
        advanceUntilIdle()

        coVerify(exactly = 2) { friendRepository.getFriends() }
        assertEquals(listOf(CollectionFriend("friend", "Alice")), viewModel.uiState.value.friends)
        assertFalse(viewModel.uiState.value.isLoadingFriends)
      }

  @Test
  fun newLoadCancelsAnInProgressLoad() =
      runTest(dispatcher) {
        val firstLoad = CompletableDeferred<List<Friendship>>()
        coEvery { friendRepository.getFriends() } coAnswers
            {
              firstLoad.await()
            } andThen
            listOf(Friendship("id", "friend"))
        coEvery { profileRepository.getProfile("friend") } returns PublicProfile("friend", "Alice")
        val viewModel = CollectionSharingViewModel(friendRepository, profileRepository)

        viewModel.loadFriends()
        advanceUntilIdle()
        viewModel.loadFriends()
        advanceUntilIdle()

        coVerify(exactly = 2) { friendRepository.getFriends() }
        assertEquals(listOf(CollectionFriend("friend", "Alice")), viewModel.uiState.value.friends)
        assertFalse(viewModel.uiState.value.isLoadingFriends)
      }

  @Test
  fun profileFailureIsReportedAndSuccessfulRetryClearsError() =
      runTest(dispatcher) {
        val failure = IllegalStateException("profile unavailable")
        coEvery { friendRepository.getFriends() } returns listOf(Friendship("id", "friend"))
        coEvery { profileRepository.getProfile("friend") } throws failure
        val viewModel = CollectionSharingViewModel(friendRepository, profileRepository)

        viewModel.loadFriends()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.friendsError is IllegalStateException)
        assertEquals(failure.message, viewModel.uiState.value.friendsError?.message)
        assertTrue(viewModel.uiState.value.friends.isEmpty())

        coEvery { profileRepository.getProfile("friend") } returns PublicProfile("friend", "Alice")
        viewModel.retryLoadingFriends()
        assertEquals(null, viewModel.uiState.value.friendsError)
        advanceUntilIdle()

        assertEquals(listOf(CollectionFriend("friend", "Alice")), viewModel.uiState.value.friends)
        assertEquals(null, viewModel.uiState.value.friendsError)
      }
}
