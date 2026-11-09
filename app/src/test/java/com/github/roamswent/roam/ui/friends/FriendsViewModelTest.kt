/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.friends

import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.friend.FriendRequest as RepositoryFriendRequest
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
import kotlinx.coroutines.test.advanceTimeBy
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

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val friendsRepository = mockk<FriendRepository>()
  private val profilesRepository = mockk<PublicProfileRepository>()

  @Before
  fun setMainDispatcher() {
    Dispatchers.setMain(dispatcher)
    coEvery { profilesRepository.getCurrentProfile() } returns PublicProfile("self", "Traveler")
    coEvery { profilesRepository.getProfile(any()) } coAnswers
        {
          val uid = firstArg<String>()
          PublicProfile(uid, "name-$uid")
        }
    coEvery { friendsRepository.getFriends() } returns emptyList()
    coEvery { friendsRepository.getIncomingRequests() } returns emptyList()
    coEvery { friendsRepository.getOutgoingRequests() } returns emptyList()
  }

  @After
  fun resetMainDispatcher() {
    Dispatchers.resetMain()
  }

  @Test
  fun loadsFriendsAndRequestProfilesForTheScreen() =
      runTest(dispatcher) {
        coEvery { friendsRepository.getFriends() } returns
            listOf(Friendship("friendship", "friend"))
        coEvery { friendsRepository.getIncomingRequests() } returns
            listOf(RepositoryFriendRequest("incoming-id", "requester", "self"))
        coEvery { friendsRepository.getOutgoingRequests() } returns
            listOf(RepositoryFriendRequest("outgoing-id", "self", "recipient"))
        val viewModel = FriendsViewModel(friendsRepository, profilesRepository)

        viewModel.loadFriends()
        advanceUntilIdle()

        assertEquals(listOf(FriendUser("friend", "name-friend")), viewModel.uiState.value.friends)
        assertEquals(
            listOf(FriendRequest(FriendUser("requester", "name-requester"), id = "incoming-id")),
            viewModel.uiState.value.incomingRequests,
        )
        assertEquals(
            listOf(FriendRequest(FriendUser("recipient", "name-recipient"), id = "outgoing-id")),
            viewModel.uiState.value.outgoingRequests,
        )
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
      }

  @Test
  fun searchesUsersAndMarksCurrentFriendRequestRelationships() =
      runTest(dispatcher) {
        coEvery { friendsRepository.getFriends() } returns
            listOf(Friendship("friendship", "friend"))
        coEvery { friendsRepository.getIncomingRequests() } returns
            listOf(RepositoryFriendRequest("incoming-id", "requester", "self"))
        coEvery { friendsRepository.getOutgoingRequests() } returns
            listOf(RepositoryFriendRequest("outgoing-id", "self", "recipient"))
        coEvery { profilesRepository.searchProfiles("na") } returns
            listOf(
                PublicProfile("self", "Traveler"),
                PublicProfile("friend", "NameFriend"),
                PublicProfile("requester", "NameRequester"),
                PublicProfile("recipient", "NameRecipient"),
                PublicProfile("new-user", "NameNewUser"),
            )
        val viewModel = FriendsViewModel(friendsRepository, profilesRepository)
        viewModel.loadFriends()
        advanceUntilIdle()

        viewModel.searchUsers("na")
        advanceTimeBy(300)
        runCurrent()

        assertEquals(
            listOf(
                FriendSearchResult(FriendUser("friend", "NameFriend"), isFriend = true),
                FriendSearchResult(
                    FriendUser("requester", "NameRequester"),
                    incomingRequestId = "incoming-id",
                ),
                FriendSearchResult(
                    FriendUser("recipient", "NameRecipient"),
                    outgoingRequestId = "outgoing-id",
                ),
                FriendSearchResult(FriendUser("new-user", "NameNewUser")),
            ),
            viewModel.uiState.value.searchResults,
        )
        coVerify { profilesRepository.searchProfiles("na") }
      }

  @Test
  fun actionsDelegateToRepositoryAndRefreshTheScreen() =
      runTest(dispatcher) {
        coEvery { friendsRepository.sendFriendRequest("target") } returns
            RepositoryFriendRequest("created-id", "self", "target")
        coEvery { friendsRepository.acceptFriendRequest(any()) } returns Unit
        coEvery { friendsRepository.rejectFriendRequest(any()) } returns Unit
        coEvery { friendsRepository.cancelFriendRequest(any()) } returns Unit
        coEvery { friendsRepository.removeFriend(any()) } returns Unit
        val request = FriendRequest(FriendUser("target", "Target"), id = "request-id")
        val user = FriendUser("target", "Target")
        val viewModel = FriendsViewModel(friendsRepository, profilesRepository)

        viewModel.sendRequest(user)
        advanceUntilIdle()
        assertEquals("Friend request sent.", viewModel.uiState.value.message)
        viewModel.acceptRequest(request)
        advanceUntilIdle()
        viewModel.rejectRequest(request)
        advanceUntilIdle()
        viewModel.cancelRequest(request)
        advanceUntilIdle()
        viewModel.removeFriend(user)
        advanceUntilIdle()

        coVerify { friendsRepository.sendFriendRequest("target") }
        coVerify { friendsRepository.acceptFriendRequest("request-id") }
        coVerify { friendsRepository.rejectFriendRequest("request-id") }
        coVerify { friendsRepository.cancelFriendRequest("request-id") }
        coVerify { friendsRepository.removeFriend("target") }
        assertFalse(viewModel.uiState.value.isMutating)
        assertNull(viewModel.uiState.value.error)
      }

  @Test
  fun searchFailureIsShownAndCanBeCleared() =
      runTest(dispatcher) {
        val error = IllegalStateException("profile search failed")
        coEvery { profilesRepository.searchProfiles("bad") } throws error
        val viewModel = FriendsViewModel(friendsRepository, profilesRepository)

        viewModel.searchUsers("bad")
        advanceTimeBy(300)
        runCurrent()

        assertEquals(error, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSearching)
        viewModel.clearMessage()
        assertNull(viewModel.uiState.value.error)
      }

  @Test
  fun blankSearchClearsResultsWithoutCallingRepository() =
      runTest(dispatcher) {
        val viewModel = FriendsViewModel(friendsRepository, profilesRepository)

        viewModel.searchUsers(" ")
        advanceUntilIdle()

        assertEquals(emptyList<FriendSearchResult>(), viewModel.uiState.value.searchResults)
        assertFalse(viewModel.uiState.value.isSearching)
        coVerify(exactly = 0) { profilesRepository.searchProfiles(any()) }
      }

  @Test
  fun loadFriendsDoesNotStartConcurrentRefreshes() =
      runTest(dispatcher) {
        val friendsResult = CompletableDeferred<List<Friendship>>()
        coEvery { friendsRepository.getFriends() } coAnswers { friendsResult.await() }
        val viewModel = FriendsViewModel(friendsRepository, profilesRepository)

        viewModel.loadFriends()
        runCurrent()
        viewModel.loadFriends()
        friendsResult.complete(emptyList())
        advanceUntilIdle()

        coVerify(exactly = 1) { friendsRepository.getFriends() }
        assertFalse(viewModel.uiState.value.isLoading)
      }

  @Test
  fun missingPublicProfileFallsBackToUidAndLoadFailuresAreReported() =
      runTest(dispatcher) {
        coEvery { friendsRepository.getFriends() } returns listOf(Friendship("id", "friend"))
        coEvery { profilesRepository.getProfile("friend") } returns null
        val viewModel = FriendsViewModel(friendsRepository, profilesRepository)

        viewModel.loadFriends()
        advanceUntilIdle()

        assertEquals(listOf(FriendUser("friend", "friend")), viewModel.uiState.value.friends)

        val error = IllegalStateException("friend list failed")
        coEvery { friendsRepository.getFriends() } throws error
        viewModel.loadFriends()
        advanceUntilIdle()

        assertEquals(error.message, viewModel.uiState.value.error?.message)
        assertFalse(viewModel.uiState.value.isLoading)
      }

  @Test
  fun failedMutationReportsErrorAndPreventsConcurrentActions() =
      runTest(dispatcher) {
        val pendingRequest = CompletableDeferred<RepositoryFriendRequest>()
        coEvery { friendsRepository.sendFriendRequest("target") } coAnswers
            {
              pendingRequest.await()
            }
        val viewModel = FriendsViewModel(friendsRepository, profilesRepository)
        val target = FriendUser("target", "Target")

        viewModel.sendRequest(target)
        runCurrent()
        viewModel.removeFriend(target)
        pendingRequest.completeExceptionally(IllegalStateException("request failed"))
        advanceUntilIdle()

        assertEquals("request failed", viewModel.uiState.value.error?.message)
        assertFalse(viewModel.uiState.value.isMutating)
        coVerify(exactly = 0) { friendsRepository.removeFriend("target") }
      }

  @Test
  fun cancellingAnInFlightSearchAllowsTheLatestQueryToComplete() =
      runTest(dispatcher) {
        val firstSearch = CompletableDeferred<List<PublicProfile>>()
        coEvery { profilesRepository.searchProfiles("first") } coAnswers { firstSearch.await() }
        coEvery { profilesRepository.searchProfiles("second") } returns
            listOf(PublicProfile("result", "SecondResult"))
        val viewModel = FriendsViewModel(friendsRepository, profilesRepository)

        viewModel.searchUsers("first")
        advanceTimeBy(300)
        runCurrent()
        viewModel.searchUsers("second")
        advanceTimeBy(300)
        runCurrent()

        assertEquals(
            listOf(FriendSearchResult(FriendUser("result", "SecondResult"))),
            viewModel.uiState.value.searchResults,
        )
        assertFalse(viewModel.uiState.value.isSearching)
      }

  @Test
  fun refreshUpdatesRelationshipsForExistingSearchResults() =
      runTest(dispatcher) {
        coEvery { profilesRepository.searchProfiles("friend") } returns
            listOf(
                PublicProfile("friend", "Friend"),
                PublicProfile("incoming", "Incoming"),
                PublicProfile("outgoing", "Outgoing"),
                PublicProfile("new", "New"),
            )
        val viewModel = FriendsViewModel(friendsRepository, profilesRepository)
        viewModel.searchUsers("friend")
        advanceTimeBy(300)
        runCurrent()

        coEvery { friendsRepository.getFriends() } returns
            listOf(Friendship("friendship", "friend"))
        coEvery { friendsRepository.getIncomingRequests() } returns
            listOf(RepositoryFriendRequest("incoming-id", "incoming", "self"))
        coEvery { friendsRepository.getOutgoingRequests() } returns
            listOf(RepositoryFriendRequest("outgoing-id", "self", "outgoing"))
        viewModel.loadFriends()
        advanceUntilIdle()

        assertEquals(
            listOf(
                FriendSearchResult(FriendUser("friend", "Friend"), isFriend = true),
                FriendSearchResult(
                    FriendUser("incoming", "Incoming"),
                    incomingRequestId = "incoming-id",
                ),
                FriendSearchResult(
                    FriendUser("outgoing", "Outgoing"),
                    outgoingRequestId = "outgoing-id",
                ),
                FriendSearchResult(FriendUser("new", "New")),
            ),
            viewModel.uiState.value.searchResults,
        )
      }
}
