// Co-authored-by: Codex <codex@openai.com>
package com.github.roamswent.roam.ui.explore

import androidx.lifecycle.viewModelScope
import com.github.roamswent.roam.model.basemap.FakeOfflineBaseMapRepository
import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.friend.Friendship
import com.github.roamswent.roam.model.guide.FakeMonumentSharingRepository
import com.github.roamswent.roam.model.guide.FakeSavedGuideRepository
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.guide.MonumentShareSettings
import com.github.roamswent.roam.model.guide.MonumentSharingRepository
import com.github.roamswent.roam.model.guide.ShareAudience
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.model.location.LocationProvider
import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreFriendMonumentsTest {
  private val dispatcher = StandardTestDispatcher()
  private val friends = mock<FriendRepository>()
  private val profiles = mock<PublicProfileRepository>()
  private val first = guide("first", Coordinates(45.0, 6.0))
  private val second = guide("second", Coordinates(46.0, 7.0))
  private lateinit var viewModel: ExploreViewModel

  @Before fun setMain() = Dispatchers.setMain(dispatcher)

  @After
  fun resetMain() {
    if (::viewModel.isInitialized) viewModel.viewModelScope.cancel()
    Dispatchers.resetMain()
  }

  @Test
  fun showsLocatedSharesPerOwnerAndKeepsProfilesWhenSharesChange() =
      runTest(dispatcher) {
        val alice = PublicProfile("alice", "Alice", avatarBytes = byteArrayOf(1))
        val sharing =
            FakeMonumentSharingRepository(
                mapOf("alice" to listOf(first, guide("unlocated", null)), "bob" to listOf(first))
            )
        setFriends("alice", "alice", "bob", "empty")
        whenever(profiles.getProfile("alice")).thenReturn(alice)
        observe(sharing)

        assertEquals(
            mapOf("alice" to pins("alice", alice, first), "bob" to pins("bob", null, first)),
            viewModel.friendMonuments.value.associateBy { it.ownerUid },
        )
        sharing.guidesByOwner.value = mapOf("alice" to listOf(second))
        runCurrent()

        assertEquals(listOf(pins("alice", alice, second)), viewModel.friendMonuments.value)
        verify(profiles).getProfile("alice")
        verify(profiles).getProfile("bob")
        verifyNoMoreInteractions(profiles)
      }

  @Test
  fun emptyFriendListLeavesOnlyPersonalMonuments() =
      runTest(dispatcher) {
        val sharing = mock<MonumentSharingRepository>()
        setFriends()
        observe(sharing)
        backgroundScope.launch { viewModel.monuments.collect {} }
        runCurrent()

        assertTrue(viewModel.friendMonuments.value.isEmpty())
        assertEquals(listOf(first).toExploreMonuments(), viewModel.monuments.value)
        verifyNoInteractions(sharing, profiles)
      }

  @Test
  fun failedFriendListLeavesSharedPinsEmpty() =
      runTest(dispatcher) {
        val sharing = mock<MonumentSharingRepository>()
        whenever(friends.getFriends()).thenThrow(IllegalStateException("Friend list unavailable"))
        observe(sharing)

        assertTrue(viewModel.friendMonuments.value.isEmpty())
        verifyNoInteractions(sharing, profiles)
      }

  @Test
  fun failedShareStreamRemovesOnlyThatOwnersPins() =
      runTest(dispatcher) {
        val sharing = mock<MonumentSharingRepository>()
        val loseAccess = CompletableDeferred<Unit>()
        setFriends("alice", "bob")
        whenever(sharing.observeSharedGuidesFrom("alice"))
            .thenReturn(
                flow {
                  emit(listOf(first))
                  loseAccess.await()
                  error("Sharing access revoked")
                }
            )
        whenever(sharing.observeSharedGuidesFrom("bob")).thenReturn(flow { emit(listOf(second)) })
        observe(sharing)

        assertEquals(
            setOf("alice", "bob"),
            viewModel.friendMonuments.value.map { it.ownerUid }.toSet(),
        )
        loseAccess.complete(Unit)
        runCurrent()

        assertEquals(listOf(pins("bob", null, second)), viewModel.friendMonuments.value)
      }

  @Test
  fun failedProfileLookupLeavesSharedPinAvailable() =
      runTest(dispatcher) {
        setFriends("alice")
        whenever(profiles.getProfile("alice"))
            .thenThrow(IllegalStateException("Profile unavailable"))
        observe(FakeMonumentSharingRepository(mapOf("alice" to listOf(first))))

        assertEquals(listOf(pins("alice", null, first)), viewModel.friendMonuments.value)
      }

  @Test
  fun returningQuicklyReplacesPinsFromRemovedFriendsWithPinsFromNewFriends() =
      runTest(dispatcher) {
        val sharing =
            FakeMonumentSharingRepository(mapOf("alice" to listOf(first), "bob" to listOf(second)))
        setFriends("alice")
        val subscription = observe(sharing)
        assertEquals(listOf(pins("alice", null, first)), viewModel.friendMonuments.value)

        subscription.cancel()
        advanceTimeBy(1_000)
        setFriends("bob")
        backgroundScope.launch { viewModel.friendMonuments.collect {} }
        runCurrent()

        assertEquals(listOf(pins("bob", null, second)), viewModel.friendMonuments.value)
      }

  @Test
  fun leavingExploreCancelsPendingProfileAndReturningRefreshesShares() =
      runTest(dispatcher) {
        val sharing = FakeMonumentSharingRepository(mapOf("alice" to listOf(first)))
        val profile = PublicProfile("alice", "Alice")
        val pending = CompletableDeferred<PublicProfile?>()
        var lookupCancelled = false
        val slowProfiles =
            object : PublicProfileRepository by profiles {
              override suspend fun getProfile(uid: String): PublicProfile? =
                  try {
                    pending.await()
                  } finally {
                    lookupCancelled = !pending.isCompleted
                  }
            }
        setFriends("alice")
        val subscription = observe(sharing, slowProfiles)
        assertEquals(listOf(pins("alice", null, first)), viewModel.friendMonuments.value)

        subscription.cancel()
        advanceTimeBy(5_001)
        runCurrent()
        assertTrue(lookupCancelled)
        assertTrue(viewModel.friendMonuments.value.isEmpty())

        sharing.guidesByOwner.value = mapOf("alice" to listOf(second))
        pending.complete(profile)
        backgroundScope.launch { viewModel.friendMonuments.collect {} }
        runCurrent()

        assertEquals(listOf(pins("alice", profile, second)), viewModel.friendMonuments.value)
      }

  private suspend fun setFriends(vararg uids: String) {
    whenever(friends.getFriends()).thenReturn(uids.map { Friendship("friendship-$it", it) })
  }

  private fun TestScope.observe(
      sharing: MonumentSharingRepository,
      publicProfiles: PublicProfileRepository = profiles,
  ): Job {
    viewModel =
        ExploreViewModel(
            mock<LocationProvider>(),
            FakeSavedGuideRepository(listOf(first)),
            FakeOfflineBaseMapRepository(),
            friends,
            publicProfiles,
            sharing,
        )
    return backgroundScope.launch { viewModel.friendMonuments.collect {} }.also { runCurrent() }
  }

  private fun pins(owner: String, profile: PublicProfile?, guide: Guide) =
      ExploreFriendMonuments(owner, profile, listOf(guide).toExploreMonuments())

  private fun guide(id: String, location: Coordinates?) =
      Guide(
          id = id,
          photoBytes = byteArrayOf(),
          monumentName = id,
          confidence = 1.0,
          history = "",
          facts = emptyList(),
          culture = "",
          location = location,
          discoveryDate = null,
          shareSettings = MonumentShareSettings(audience = ShareAudience.ALL_FRIENDS),
      )
}
