/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 * Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
 */

package com.github.roamswent.roam.ui.friends

import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.friend.FriendRequest as RepositoryFriendRequest
import com.github.roamswent.roam.model.friend.Friendship
import com.github.roamswent.roam.model.leaderboard.LeaderboardEntry
import com.github.roamswent.roam.model.leaderboard.LeaderboardException
import com.github.roamswent.roam.model.leaderboard.LeaderboardRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val friendsRepository = mockk<FriendRepository>()
  private val profilesRepository = mockk<PublicProfileRepository>()
  private val leaderboardRepository = mockk<LeaderboardRepository>()
  private val selfEntry = LeaderboardEntry("self", "Traveler", verifiedMonumentCount = 2, rank = 2)
  private val friendEntry =
      LeaderboardEntry("friend", "Friend", verifiedMonumentCount = 5, rank = 1)

  private fun viewModel() =
      FriendsViewModel(friendsRepository, profilesRepository, leaderboardRepository)

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
    coEvery { leaderboardRepository.getFriendsLeaderboard() } returns listOf(friendEntry, selfEntry)
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
        val viewModel = viewModel()

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
        val viewModel = viewModel()
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
        val viewModel = viewModel()

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
  fun reusesProfilesAcrossMutationsAndRefreshesThemAfterCacheExpires() =
      runTest(dispatcher) {
        var nowMillis = 0L
        coEvery { friendsRepository.getFriends() } returns
            listOf(Friendship("friendship", "friend"))
        coEvery { friendsRepository.getIncomingRequests() } returns
            listOf(RepositoryFriendRequest("request-id", "requester", "self"))
        coEvery { friendsRepository.acceptFriendRequest("request-id") } returns Unit
        coEvery { friendsRepository.removeFriend("friend") } returns Unit
        coEvery { profilesRepository.getProfile("friend") } coAnswers
            {
              PublicProfile("friend", if (nowMillis == 0L) "OldName" else "NewName")
            }
        val viewModel =
            FriendsViewModel(friendsRepository, profilesRepository, leaderboardRepository) {
              nowMillis
            }
        val request = FriendRequest(FriendUser("requester", "Requester"), id = "request-id")

        viewModel.loadFriends()
        advanceUntilIdle()
        viewModel.acceptRequest(request)
        advanceUntilIdle()

        assertEquals("OldName", viewModel.uiState.value.friends.single().username)
        coVerify(exactly = 1) { profilesRepository.getProfile("friend") }

        nowMillis += 5 * 60 * 1000L
        viewModel.removeFriend(FriendUser("friend", "OldName"))
        advanceUntilIdle()

        assertEquals("NewName", viewModel.uiState.value.friends.single().username)
        coVerify(exactly = 2) { profilesRepository.getProfile("friend") }
      }

  @Test
  fun explicitRefreshReloadsCachedProfiles() =
      runTest(dispatcher) {
        var username = "OldName"
        coEvery { friendsRepository.getFriends() } returns
            listOf(Friendship("friendship", "friend"))
        coEvery { profilesRepository.getProfile("friend") } coAnswers
            {
              PublicProfile("friend", username)
            }
        val viewModel = viewModel()

        viewModel.loadFriends()
        advanceUntilIdle()
        username = "NewName"
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals("NewName", viewModel.uiState.value.friends.single().username)
        coVerify(exactly = 2) { profilesRepository.getProfile("friend") }
      }

  @Test
  fun loadsAvatarBytesForFriendsAndSearchResults() =
      runTest(dispatcher) {
        val avatarBytes = byteArrayOf(1, 2, 3)
        coEvery { friendsRepository.getFriends() } returns
            listOf(Friendship("friendship", "friend"))
        coEvery { friendsRepository.getIncomingRequests() } returns emptyList()
        coEvery { friendsRepository.getOutgoingRequests() } returns emptyList()
        coEvery { profilesRepository.getProfile("friend") } returns
            PublicProfile("friend", "FriendName", avatarBytes = avatarBytes)
        coEvery { profilesRepository.searchProfiles("Friend") } returns
            listOf(PublicProfile("friend", "FriendName", avatarBytes = avatarBytes))
        val viewModel = viewModel()

        viewModel.loadFriends()
        advanceUntilIdle()
        viewModel.searchUsers("Friend")
        advanceUntilIdle()

        assertEquals(
            true,
            viewModel.uiState.value.friends.single().avatarBytes.contentEquals(avatarBytes),
        )
        assertEquals(
            true,
            viewModel.uiState.value.searchResults
                .single()
                .user
                .avatarBytes
                .contentEquals(avatarBytes),
        )
      }

  @Test
  fun searchFailureIsShownAndCanBeCleared() =
      runTest(dispatcher) {
        val error = IllegalStateException("profile search failed")
        coEvery { profilesRepository.searchProfiles("bad") } throws error
        val viewModel = viewModel()

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
        val viewModel = viewModel()

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
        val viewModel = viewModel()

        viewModel.loadFriends()
        runCurrent()
        viewModel.loadFriends()
        friendsResult.complete(emptyList())
        advanceUntilIdle()

        coVerify(exactly = 1) { friendsRepository.getFriends() }
        assertFalse(viewModel.uiState.value.isLoading)
      }

  @Test
  fun refreshDoesNotStartAnotherLoadWhileLoading() =
      runTest(dispatcher) {
        val friendsResult = CompletableDeferred<List<Friendship>>()
        coEvery { friendsRepository.getFriends() } coAnswers { friendsResult.await() }
        val viewModel = viewModel()

        viewModel.loadFriends()
        runCurrent()
        viewModel.refresh()
        friendsResult.complete(emptyList())
        advanceUntilIdle()

        coVerify(exactly = 1) { friendsRepository.getFriends() }
        assertFalse(viewModel.uiState.value.isLoading)
      }

  @Test
  fun refreshDoesNotStartAnotherLoadWhileMutating() =
      runTest(dispatcher) {
        val pendingRequest = CompletableDeferred<RepositoryFriendRequest>()
        coEvery { friendsRepository.sendFriendRequest("target") } coAnswers
            {
              pendingRequest.await()
            }
        val viewModel = viewModel()

        viewModel.sendRequest(FriendUser("target", "Target"))
        runCurrent()
        viewModel.refresh()
        pendingRequest.complete(RepositoryFriendRequest("request-id", "self", "target"))
        advanceUntilIdle()

        coVerify(exactly = 1) { friendsRepository.getFriends() }
        assertFalse(viewModel.uiState.value.isMutating)
      }

  @Test
  fun missingPublicProfileFallsBackToUidAndLoadFailuresAreReported() =
      runTest(dispatcher) {
        coEvery { friendsRepository.getFriends() } returns listOf(Friendship("id", "friend"))
        coEvery { profilesRepository.getProfile("friend") } returns null
        val viewModel = viewModel()

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
        val viewModel = viewModel()
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
        val viewModel = viewModel()

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
        val viewModel = viewModel()
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

  @Test
  fun initialLoadExposesLeaderboardInRepositoryOrderAndCurrentUser() =
      runTest(dispatcher) {
        // Deliberately not sorted by count: the view model must keep the repository's order.
        val entries =
            listOf(
                LeaderboardEntry("b", "B", verifiedMonumentCount = 1, rank = 2),
                LeaderboardEntry("a", "A", verifiedMonumentCount = 1, rank = 2),
                LeaderboardEntry("self", "Traveler", verifiedMonumentCount = 0, rank = 9),
            )
        coEvery { leaderboardRepository.getFriendsLeaderboard() } returns entries
        val viewModel = viewModel()

        viewModel.loadFriends()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(entries, state.leaderboard)
        assertEquals("self", state.currentUserUid)
        assertNull(state.leaderboardError)
        assertTrue(state.hasLoaded)
        assertFalse(state.isLoading)
        coVerify(exactly = 1) { leaderboardRepository.getFriendsLeaderboard() }
      }

  @Test
  fun friendsAndRequestsArePublishedWhileTheLeaderboardIsStillLoading() =
      runTest(dispatcher) {
        val leaderboardResult = CompletableDeferred<List<LeaderboardEntry>>()
        coEvery { leaderboardRepository.getFriendsLeaderboard() } coAnswers
            {
              leaderboardResult.await()
            }
        coEvery { friendsRepository.getFriends() } returns
            listOf(Friendship("friendship", "friend"))
        coEvery { friendsRepository.getIncomingRequests() } returns
            listOf(RepositoryFriendRequest("incoming-id", "requester", "self"))
        coEvery { friendsRepository.getOutgoingRequests() } returns
            listOf(RepositoryFriendRequest("outgoing-id", "self", "recipient"))
        val viewModel = viewModel()

        viewModel.loadFriends()
        advanceUntilIdle()

        // The leaderboard has not answered, yet the Friends view is complete and usable.
        val state = viewModel.uiState.value
        assertEquals(listOf(FriendUser("friend", "name-friend")), state.friends)
        assertEquals(
            listOf(FriendRequest(FriendUser("requester", "name-requester"), id = "incoming-id")),
            state.incomingRequests,
        )
        assertEquals(
            listOf(FriendRequest(FriendUser("recipient", "name-recipient"), id = "outgoing-id")),
            state.outgoingRequests,
        )
        assertFalse(state.isLoading)
        assertTrue(state.hasLoaded)
        assertTrue(state.isLeaderboardLoading)
        assertFalse(state.hasLoadedLeaderboard)
        assertTrue(state.leaderboard.isEmpty())

        leaderboardResult.complete(listOf(selfEntry))
        advanceUntilIdle()

        assertEquals(listOf(selfEntry), viewModel.uiState.value.leaderboard)
        assertFalse(viewModel.uiState.value.isLeaderboardLoading)
        assertTrue(viewModel.uiState.value.hasLoadedLeaderboard)
      }

  @Test
  fun friendsErrorIsPublishedWhileTheLeaderboardIsStillLoading() =
      runTest(dispatcher) {
        coEvery { leaderboardRepository.getFriendsLeaderboard() } coAnswers
            {
              CompletableDeferred<List<LeaderboardEntry>>().await()
            }
        val error = IllegalStateException("friend list failed")
        coEvery { friendsRepository.getFriends() } throws error
        val viewModel = viewModel()

        viewModel.loadFriends()
        advanceUntilIdle()

        assertEquals(error.message, viewModel.uiState.value.error?.message)
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.hasLoaded)
        assertTrue(viewModel.uiState.value.isLeaderboardLoading)
      }

  @Test
  fun mutationFinishesWithoutWaitingForTheLeaderboard() =
      runTest(dispatcher) {
        coEvery { friendsRepository.removeFriend("friend") } returns Unit
        coEvery { leaderboardRepository.getFriendsLeaderboard() } coAnswers
            {
              CompletableDeferred<List<LeaderboardEntry>>().await()
            }
        val viewModel = viewModel()

        viewModel.removeFriend(FriendUser("friend", "Friend"))
        advanceUntilIdle()

        assertEquals("Friend removed.", viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isMutating)
        assertTrue(viewModel.uiState.value.isLeaderboardLoading)
      }

  @Test
  fun refreshDuringAPendingLeaderboardLoadPublishesOnlyTheNewerResult() =
      runTest(dispatcher) {
        val first = CompletableDeferred<List<LeaderboardEntry>>()
        val second = CompletableDeferred<List<LeaderboardEntry>>()
        val results = ArrayDeque(listOf(first, second))
        coEvery { leaderboardRepository.getFriendsLeaderboard() } coAnswers
            {
              results.removeFirst().await()
            }
        val viewModel = viewModel()
        viewModel.loadFriends()
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()
        // The first load was cancelled by the refresh: its late answer must not be applied.
        first.complete(listOf(friendEntry, selfEntry))
        second.complete(listOf(selfEntry))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(selfEntry), state.leaderboard)
        assertNull(state.leaderboardError)
        assertFalse(state.isLeaderboardLoading)
        coVerify(exactly = 2) { leaderboardRepository.getFriendsLeaderboard() }
      }

  @Test
  fun explicitRefreshReloadsLeaderboard() =
      runTest(dispatcher) {
        val updated = listOf(selfEntry.copy(verifiedMonumentCount = 6, rank = 1))
        val viewModel = viewModel()
        viewModel.loadFriends()
        advanceUntilIdle()

        coEvery { leaderboardRepository.getFriendsLeaderboard() } returns updated
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(updated, viewModel.uiState.value.leaderboard)
        coVerify(exactly = 2) { leaderboardRepository.getFriendsLeaderboard() }
      }

  @Test
  fun acceptingAndRemovingFriendsRefreshesLeaderboard() =
      runTest(dispatcher) {
        coEvery { friendsRepository.acceptFriendRequest("request-id") } returns Unit
        coEvery { friendsRepository.removeFriend("friend") } returns Unit
        coEvery { leaderboardRepository.getFriendsLeaderboard() } returns listOf(selfEntry)
        val viewModel = viewModel()
        viewModel.loadFriends()
        advanceUntilIdle()

        coEvery { leaderboardRepository.getFriendsLeaderboard() } returns
            listOf(friendEntry, selfEntry)
        viewModel.acceptRequest(FriendRequest(FriendUser("friend", "Friend"), id = "request-id"))
        advanceUntilIdle()

        assertEquals(listOf(friendEntry, selfEntry), viewModel.uiState.value.leaderboard)
        assertEquals("Friend request accepted.", viewModel.uiState.value.message)

        coEvery { leaderboardRepository.getFriendsLeaderboard() } returns
            listOf(selfEntry.copy(rank = 1))
        viewModel.removeFriend(FriendUser("friend", "Friend"))
        advanceUntilIdle()

        assertEquals(listOf(selfEntry.copy(rank = 1)), viewModel.uiState.value.leaderboard)
        assertEquals("Friend removed.", viewModel.uiState.value.message)
        coVerify(exactly = 3) { leaderboardRepository.getFriendsLeaderboard() }
      }

  @Test
  fun leaderboardFailureKeepsFriendsAndRequestsAndIsExposedSeparately() =
      runTest(dispatcher) {
        val failure = LeaderboardException.FirestoreFailure(IllegalStateException("offline"))
        coEvery { friendsRepository.getFriends() } returns
            listOf(Friendship("friendship", "friend"))
        coEvery { friendsRepository.getIncomingRequests() } returns
            listOf(RepositoryFriendRequest("incoming-id", "requester", "self"))
        coEvery { leaderboardRepository.getFriendsLeaderboard() } throws failure
        val viewModel = viewModel()

        viewModel.loadFriends()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(FriendUser("friend", "name-friend")), state.friends)
        assertEquals(
            listOf(FriendRequest(FriendUser("requester", "name-requester"), id = "incoming-id")),
            state.incomingRequests,
        )
        assertEquals(failure, state.leaderboardError)
        assertNull(state.error)
        assertEquals("self", state.currentUserUid)
        assertTrue(state.hasLoaded)
        assertFalse(state.isLoading)
      }

  @Test
  fun leaderboardFailureKeepsPreviousEntriesAndNextSuccessClearsIt() =
      runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.loadFriends()
        advanceUntilIdle()

        val failure = LeaderboardException.FirestoreFailure(IllegalStateException("offline"))
        coEvery { leaderboardRepository.getFriendsLeaderboard() } throws failure
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(listOf(friendEntry, selfEntry), viewModel.uiState.value.leaderboard)
        assertEquals(failure, viewModel.uiState.value.leaderboardError)

        coEvery { leaderboardRepository.getFriendsLeaderboard() } returns listOf(selfEntry)
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(listOf(selfEntry), viewModel.uiState.value.leaderboard)
        assertNull(viewModel.uiState.value.leaderboardError)
      }

  @Test
  fun leaderboardFailureDuringMutationStillReportsMutationSuccess() =
      runTest(dispatcher) {
        coEvery { friendsRepository.removeFriend("friend") } returns Unit
        val failure = LeaderboardException.FirestoreFailure(IllegalStateException("offline"))
        coEvery { leaderboardRepository.getFriendsLeaderboard() } throws failure
        val viewModel = viewModel()

        viewModel.removeFriend(FriendUser("friend", "Friend"))
        advanceUntilIdle()

        assertEquals("Friend removed.", viewModel.uiState.value.message)
        assertNull(viewModel.uiState.value.error)
        assertEquals(failure, viewModel.uiState.value.leaderboardError)
        assertFalse(viewModel.uiState.value.isMutating)
      }

  @Test
  fun friendsFailureStillAppliesLeaderboardResult() =
      runTest(dispatcher) {
        val error = IllegalStateException("friend list failed")
        coEvery { friendsRepository.getFriends() } throws error
        val viewModel = viewModel()

        viewModel.loadFriends()
        advanceUntilIdle()

        assertEquals(error.message, viewModel.uiState.value.error?.message)
        assertEquals(listOf(friendEntry, selfEntry), viewModel.uiState.value.leaderboard)
        assertNull(viewModel.uiState.value.leaderboardError)
        assertFalse(viewModel.uiState.value.isLoading)
      }

  @Test
  fun concurrentLoadAndRefreshStartOnlyOneLeaderboardLoad() =
      runTest(dispatcher) {
        // The friends load is still running, so the extra load and refresh are ignored.
        val friendsResult = CompletableDeferred<List<Friendship>>()
        coEvery { friendsRepository.getFriends() } coAnswers { friendsResult.await() }
        coEvery { leaderboardRepository.getFriendsLeaderboard() } returns listOf(selfEntry)
        val viewModel = viewModel()

        viewModel.loadFriends()
        runCurrent()
        viewModel.loadFriends()
        viewModel.refresh()
        friendsResult.complete(emptyList())
        advanceUntilIdle()

        coVerify(exactly = 1) { leaderboardRepository.getFriendsLeaderboard() }
        assertEquals(listOf(selfEntry), viewModel.uiState.value.leaderboard)
      }
}
