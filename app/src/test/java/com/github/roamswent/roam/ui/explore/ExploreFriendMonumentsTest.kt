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
import com.github.roamswent.roam.model.wishlist.WishlistItem
import com.github.roamswent.roam.model.wishlist.WishlistMonumentKey
import com.github.roamswent.roam.model.wishlist.WishlistRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
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

  @Test
  fun wishlistWritePreservesTheSharedGuideReferenceAndSuppressesDuplicateTaps() =
      runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        val items = mutableListOf<WishlistItem>()
        val backend = mock<WishlistRepository>()
        val repository =
            object : WishlistRepository by backend {
              override suspend fun addToWishlist(item: WishlistItem): WishlistItem {
                items += item
                release.await()
                return item
              }
            }
        viewModel = createViewModel(wishlist = repository)
        val pin = ExploreSharedPin("alice", listOf(first).toExploreMonuments().single())
        val key = WishlistMonumentKey.fromCoordinates(pin.monument.coordinates)

        viewModel.addToWishlist(pin)
        runCurrent()
        assertEquals(
            ExploreWishlistWrite(key, ExploreWishlistWrite.Status.PENDING),
            viewModel.wishlistWrite.value,
        )
        viewModel.addToWishlist(pin)
        release.complete(Unit)
        runCurrent()
        assertEquals(
            ExploreWishlistWrite(key, ExploreWishlistWrite.Status.SAVED),
            viewModel.wishlistWrite.value,
        )
        viewModel.addToWishlist(pin)
        runCurrent()

        val item = items.single()
        assertEquals(key, item.monumentKey)
        assertEquals(pin.ownerUid, item.sourceOwnerUid)
        assertEquals(pin.monument.guideId, item.sourceGuideId)
        assertEquals(pin.monument.name, item.monumentName)
        assertNull(item.location)
      }

  @Test
  fun rejectedWishlistWriteCanBeRetried() =
      runTest(dispatcher) {
        val repository = mock<WishlistRepository>()
        whenever(repository.addToWishlist(any()))
            .thenThrow(IllegalStateException("Write rejected"))
            .thenAnswer { it.getArgument(0) }
        viewModel = createViewModel(wishlist = repository)
        val pin = ExploreSharedPin("alice", listOf(first).toExploreMonuments().single())
        val key = WishlistMonumentKey.fromCoordinates(pin.monument.coordinates)
        viewModel.addToWishlist(pin)
        runCurrent()
        assertEquals(
            ExploreWishlistWrite(key, ExploreWishlistWrite.Status.FAILED),
            viewModel.wishlistWrite.value,
        )
        viewModel.addToWishlist(pin)
        runCurrent()
        assertEquals(
            ExploreWishlistWrite(key, ExploreWishlistWrite.Status.SAVED),
            viewModel.wishlistWrite.value,
        )
        verify(repository, times(2)).addToWishlist(any())
      }

  @Test
  fun wishlistLookupsReturnResultsAndDegradeGracefullyWhenReadsFail() =
      runTest(dispatcher) {
        val repository = mock<WishlistRepository>()
        val key = WishlistMonumentKey.fromCoordinates(checkNotNull(first.location))
        val alice = PublicProfile("alice", "Alice")
        whenever(repository.isWishlisted(key))
            .thenReturn(true)
            .thenThrow(IllegalStateException("Unavailable"))
            .thenReturn(false)
        whenever(repository.getFriendsWhoWishlisted(key))
            .thenReturn(listOf(alice))
            .thenThrow(IllegalStateException("Unavailable"))
        viewModel = createViewModel(wishlist = repository)
        assertEquals(ExploreWishlistStatus.SAVED, viewModel.checkWishlist(key))
        assertEquals(listOf(alice), viewModel.friendsWhoWishlisted(key))
        assertEquals(ExploreWishlistStatus.CHECK_FAILED, viewModel.checkWishlist(key))
        assertEquals(ExploreWishlistStatus.NOT_SAVED, viewModel.checkWishlist(key))
        assertTrue(viewModel.friendsWhoWishlisted(key).isEmpty())
      }

  @Test
  fun cancelledWishlistLookupsPropagateCancellation() =
      runTest(dispatcher) {
        val repository = mock<WishlistRepository>()
        val key = WishlistMonumentKey.fromCoordinates(checkNotNull(first.location))
        whenever(repository.isWishlisted(key)).thenThrow(CancellationException())
        whenever(repository.getFriendsWhoWishlisted(key)).thenThrow(CancellationException())
        viewModel = createViewModel(wishlist = repository)
        assertThrows(CancellationException::class.java) {
          runBlocking { viewModel.checkWishlist(key) }
        }
        assertThrows(CancellationException::class.java) {
          runBlocking { viewModel.friendsWhoWishlisted(key) }
        }
      }

  @Test
  fun pendingWishlistWriteIsCancelledWithExplore() =
      runTest(dispatcher) {
        val cancelled = CompletableDeferred<Unit>()
        val backend = mock<WishlistRepository>()
        val repository =
            object : WishlistRepository by backend {
              override suspend fun addToWishlist(item: WishlistItem): WishlistItem =
                  try {
                    awaitCancellation()
                  } finally {
                    cancelled.complete(Unit)
                  }
            }
        viewModel = createViewModel(wishlist = repository)
        val pin = ExploreSharedPin("alice", listOf(first).toExploreMonuments().single())
        viewModel.addToWishlist(pin)
        runCurrent()

        viewModel.viewModelScope.cancel()
        runCurrent()

        assertTrue(cancelled.isCompleted)
        assertEquals(ExploreWishlistWrite.Status.PENDING, viewModel.wishlistWrite.value?.status)
      }

  @Test
  fun wishlistIsUnavailableWhenExploreHasNoWishlistBackend() =
      runTest(dispatcher) {
        viewModel = createViewModel()
        val pin = ExploreSharedPin("alice", listOf(first).toExploreMonuments().single())
        val key = WishlistMonumentKey.fromCoordinates(pin.monument.coordinates)

        assertEquals(ExploreWishlistStatus.UNAVAILABLE, viewModel.checkWishlist(key))
        assertTrue(viewModel.friendsWhoWishlisted(key).isEmpty())
        viewModel.addToWishlist(pin)
        runCurrent()
        assertNull(viewModel.wishlistWrite.value)
      }

  private suspend fun setFriends(vararg uids: String) {
    whenever(friends.getFriends()).thenReturn(uids.map { Friendship("friendship-$it", it) })
  }

  private fun TestScope.observe(
      sharing: MonumentSharingRepository,
      publicProfiles: PublicProfileRepository = profiles,
  ): Job {
    viewModel = createViewModel(sharing, publicProfiles)
    return backgroundScope.launch { viewModel.friendMonuments.collect {} }.also { runCurrent() }
  }

  private fun createViewModel(
      sharing: MonumentSharingRepository = FakeMonumentSharingRepository(),
      publicProfiles: PublicProfileRepository = profiles,
      wishlist: WishlistRepository? = null,
  ): ExploreViewModel =
      ExploreViewModel(
          mock<LocationProvider>(),
          FakeSavedGuideRepository(listOf(first)),
          FakeOfflineBaseMapRepository(),
          friends,
          publicProfiles,
          sharing,
          wishlist,
      )

  private fun pins(owner: String, profile: PublicProfile?, guide: Guide) =
      ExploreFriendMonuments(
          owner,
          profile,
          listOf(guide).toExploreMonuments(),
          guide.discoveryDate?.let { mapOf(guide.id to it) }.orEmpty(),
      )

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
          discoveryDate = if (id == "first") 1_700_000_000_000L else null,
          shareSettings = MonumentShareSettings(audience = ShareAudience.ALL_FRIENDS),
      )
}
