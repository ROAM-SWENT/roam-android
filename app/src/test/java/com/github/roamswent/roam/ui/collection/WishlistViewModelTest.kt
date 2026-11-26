// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.friend.Friendship
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.guide.MonumentSharingRepository
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.model.location.LocationName
import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import com.github.roamswent.roam.model.wishlist.FriendScannedMonumentsSource
import com.github.roamswent.roam.model.wishlist.FriendWishlistRepository
import com.github.roamswent.roam.model.wishlist.FriendWishlistedMonumentsSource
import com.github.roamswent.roam.model.wishlist.ResolvedWishlistItem
import com.github.roamswent.roam.model.wishlist.WishlistItem
import com.github.roamswent.roam.model.wishlist.WishlistMonumentKey
import com.github.roamswent.roam.model.wishlist.WishlistRepository
import com.github.roamswent.roam.model.wishlist.WishlistUnavailableReason
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WishlistViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val repository = FakeWishlistRepository()
  private val guide =
      Guide(
          id = "guide",
          photoBytes = byteArrayOf(1, 2),
          monumentName = "Louvre",
          confidence = 1.0,
          history = "",
          facts = emptyList(),
          culture = "",
          location = Coordinates(48.86, 2.34),
      )
  private val key = WishlistMonumentKey.forGuide("friend", guide)
  private val item = WishlistItem(key, "friend", "guide", "Louvre", 10, guide.location)

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  @Test
  fun loadsResolvedAndUnavailableItemsAndObservesUpdates() =
      runTest(dispatcher) {
        repository.guides[key] = guide
        val unavailable = WishlistItem("ref_friend~other", "friend", "other", "Arc", 5)
        repository.items.value = listOf(item, unavailable)
        val viewModel = WishlistViewModel(repository)
        viewModel.loadWishlist()
        advanceUntilIdle()

        val monuments = viewModel.uiState.value.monuments
        assertEquals(listOf("Arc", "Louvre"), monuments.map { it.name })
        assertTrue(monuments[0].unavailable)
        assertEquals(WishlistUnavailableReason.GUIDE_DELETED, monuments[0].unavailableReason)
        assertEquals("friend", monuments[0].sourceOwnerUid)
        assertEquals(guide.location, monuments[1].location)
        assertEquals("friend", monuments[1].sourceOwnerUid)
        assertEquals("guide", monuments[1].sourceGuideId)
        assertArrayEquals(guide.photoBytes, monuments[1].photoBytes)
        assertEquals(setOf(key, unavailable.monumentKey), viewModel.uiState.value.wishlistedKeys)
        repository.items.value = listOf(unavailable)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.monuments.last().isWishlisted)
        viewModel.loadWishlist()
        advanceUntilIdle()
        assertEquals(listOf("Arc"), viewModel.uiState.value.monuments.map { it.name })
        assertEquals(2, viewModel.uiState.value.refreshVersion)
      }

  @Test
  fun entriesOnlyModeResolvesAndGeocodesNothing() =
      runTest(dispatcher) {
        repository.items.value = listOf(item)
        var resolved = 0
        var geocoded = 0
        val counting =
            object : WishlistRepository by repository {
              override suspend fun getResolvedWishlist(): List<ResolvedWishlistItem> {
                resolved++
                return repository.getResolvedWishlist()
              }
            }
        val viewModel =
            WishlistViewModel(counting) {
              geocoded++
              null
            }
        viewModel.loadWishlistEntries()
        advanceUntilIdle()

        assertEquals(setOf(key), viewModel.uiState.value.wishlistedKeys)
        assertEquals(mapOf(("friend" to "guide") to key), viewModel.uiState.value.guideKeys)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(0, resolved)
        assertEquals(0, geocoded)

        viewModel.toggleGuide("friend", guide)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.guideKeys.isEmpty())
        assertEquals(0, resolved)
      }

  @Test
  fun entriesOnlyModeReportsLoadErrorAndRetriesInTheSameMode() =
      runTest(dispatcher) {
        repository.loadError = IllegalStateException("offline")
        var resolved = 0
        val counting =
            object : WishlistRepository by repository {
              override suspend fun getResolvedWishlist(): List<ResolvedWishlistItem> {
                resolved++
                return repository.getResolvedWishlist()
              }
            }
        val viewModel = WishlistViewModel(counting)
        viewModel.loadWishlistEntries()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)

        repository.loadError = null
        repository.items.value = listOf(item)
        viewModel.retryLoadingWishlist()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.error)
        assertEquals(setOf(key), viewModel.uiState.value.wishlistedKeys)
        assertEquals(0, resolved)
      }

  @Test
  fun stoppingFriendObservationClosesListenersAndStartingReopensThem() =
      runTest(dispatcher) {
        var subscriptions = 0
        val sharing = mockk<MonumentSharingRepository>()
        val friends = mockk<FriendRepository>()
        val profiles = mockk<PublicProfileRepository>()
        coEvery { friends.getFriends() } returns listOf(Friendship("a", "alice"))
        coEvery { profiles.getProfile(any()) } returns null
        every { sharing.observeSharedGuidesFrom("alice") } answers
            {
              kotlinx.coroutines.flow.flow {
                subscriptions++
                emit(listOf(guide))
                kotlinx.coroutines.awaitCancellation()
              }
            }
        val viewModel =
            WishlistViewModel(repository, FriendScannedMonumentsSource(friends, sharing, profiles))

        viewModel.startFriendObservation()
        viewModel.startFriendObservation()
        advanceUntilIdle()
        assertEquals(1, subscriptions)

        viewModel.stopFriendObservation()
        advanceUntilIdle()
        viewModel.startFriendObservation()
        advanceUntilIdle()
        assertEquals(2, subscriptions)
      }

  @Test
  fun resolvesTheGuideLocationAndFallsBackToSavedCoordinates() =
      runTest(dispatcher) {
        val savedLocation = Coordinates(46.0, 7.0)
        repository.items.value = listOf(item.copy(location = savedLocation))
        repository.guides[key] = guide
        val requested = mutableListOf<Coordinates>()
        val viewModel =
            WishlistViewModel(repository) {
              requested.add(it)
              LocationName("Paris", "France")
            }
        viewModel.loadWishlist()
        advanceUntilIdle()
        assertEquals(listOf(guide.location), requested)
        assertEquals("Paris", viewModel.uiState.value.monuments.single().city)
        assertEquals("France", viewModel.uiState.value.monuments.single().country)

        repository.guides.clear()
        viewModel.loadWishlist()
        advanceUntilIdle()
        assertEquals(savedLocation, viewModel.uiState.value.monuments.single().location)
        assertTrue(viewModel.uiState.value.monuments.single().unavailable)
      }

  @Test
  fun missingLocationNameKeepsCoordinates() =
      runTest(dispatcher) {
        repository.items.value = listOf(item)
        repository.guides[key] = guide
        val viewModel = WishlistViewModel(repository) { null }
        viewModel.loadWishlist()
        advanceUntilIdle()
        val monument = viewModel.uiState.value.monuments.single()
        assertEquals(guide.location, monument.location)
        assertEquals("", monument.city)
      }

  @Test
  fun sharedMonumentsShowBothFriendsWithEachGuideOwnerFirst() =
      runTest(dispatcher) {
        val friends = mockk<FriendRepository>()
        val sharing = mockk<MonumentSharingRepository>()
        val profiles = mockk<PublicProfileRepository>()
        val aliceGuide = guide.copy(id = "alice-guide", monumentName = " Eiffel  Tower ")
        val bobGuide = guide.copy(id = "bob-guide", monumentName = "eiffel tower")
        val aliceShared = MutableStateFlow(listOf(aliceGuide, aliceGuide.copy(id = "duplicate")))
        val bobShared = MutableStateFlow(listOf(bobGuide))
        coEvery { friends.getFriends() } returns
            listOf(Friendship("a", "alice"), Friendship("b", "bob"))
        every { sharing.observeSharedGuidesFrom("alice") } returns aliceShared
        every { sharing.observeSharedGuidesFrom("bob") } returns bobShared
        val alicePhoto = byteArrayOf(1)
        val bobPhoto = byteArrayOf(2)
        coEvery { profiles.getProfile("alice") } returns
            PublicProfile("alice", "Alice", avatarBytes = alicePhoto)
        coEvery { profiles.getProfile("bob") } returns
            PublicProfile("bob", "Bob", avatarBytes = bobPhoto)
        val source = FriendScannedMonumentsSource(friends, sharing, profiles)
        val aliceKey = WishlistMonumentKey.forSharedGuide("alice", aliceGuide.id)
        val bobKey = WishlistMonumentKey.forSharedGuide("bob", bobGuide.id)
        repository.items.value =
            listOf(
                WishlistItem(aliceKey, "alice", aliceGuide.id, aliceGuide.monumentName, 1),
                WishlistItem(bobKey, "bob", bobGuide.id, bobGuide.monumentName, 2),
            )
        repository.guides[aliceKey] = aliceGuide
        repository.guides[bobKey] = bobGuide
        val viewModel = WishlistViewModel(repository, source)
        viewModel.loadWishlist()
        viewModel.retryLoadingScannedFriends()
        advanceUntilIdle()

        val byOwner = viewModel.uiState.value.monuments.associateBy { it.sourceOwnerUid }
        assertEquals(listOf("Alice", "Bob"), byOwner.getValue("alice").scannedBy.map { it.name })
        assertEquals(listOf("Bob", "Alice"), byOwner.getValue("bob").scannedBy.map { it.name })
        assertArrayEquals(alicePhoto, byOwner.getValue("alice").scannedBy.first().avatarBytes)
        assertArrayEquals(bobPhoto, byOwner.getValue("bob").scannedBy.first().avatarBytes)
        assertEquals("alice-guide", byOwner.getValue("alice").sourceGuideId)
        assertEquals("bob-guide", byOwner.getValue("bob").sourceGuideId)

        bobShared.value = emptyList()
        advanceUntilIdle()
        assertEquals(
            listOf("Alice"),
            viewModel.uiState.value.monuments.first().scannedBy.map { it.name },
        )
      }

  @Test
  fun sharedFriendsErrorCanBeRetried() =
      runTest(dispatcher) {
        val friends = mockk<FriendRepository>()
        val sharing = mockk<MonumentSharingRepository>()
        val profiles = mockk<PublicProfileRepository>()
        coEvery { friends.getFriends() } throws IllegalStateException("offline") andThen emptyList()
        val viewModel =
            WishlistViewModel(repository, FriendScannedMonumentsSource(friends, sharing, profiles))
        viewModel.loadWishlist()
        viewModel.retryLoadingScannedFriends()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.scannedByError)
        viewModel.retryLoadingScannedFriends()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.scannedByError)
      }

  @Test
  fun wishlistingFriendsAreMatchedByNameAndUpdateLive() =
      runTest(dispatcher) {
        val friends = mockk<FriendRepository>()
        val wishlists = mockk<FriendWishlistRepository>()
        val profiles = mockk<PublicProfileRepository>()
        val aliceWishlist = MutableStateFlow(listOf("  LOUVRE ", "louvre"))
        val bobWishlist = MutableStateFlow(listOf("louvre"))
        coEvery { friends.getFriends() } returns
            listOf(Friendship("a", "alice"), Friendship("b", "bob"))
        every { wishlists.observeWishlistedMonumentNames("alice") } returns aliceWishlist
        every { wishlists.observeWishlistedMonumentNames("bob") } returns bobWishlist
        val photo = byteArrayOf(3)
        coEvery { profiles.getProfile("alice") } returns
            PublicProfile("alice", "Alice", avatarBytes = photo)
        coEvery { profiles.getProfile("bob") } returns null
        repository.items.value = listOf(item)
        repository.guides[key] = guide
        val viewModel =
            WishlistViewModel(
                repository,
                friendWishlistsSource =
                    FriendWishlistedMonumentsSource(friends, wishlists, profiles),
            )
        viewModel.loadWishlist()
        viewModel.retryLoadingFriends()
        advanceUntilIdle()
        val wishlistedBy = viewModel.uiState.value.monuments.single().wishlistedBy
        assertEquals(listOf("Alice", ""), wishlistedBy.map { it.name })
        assertArrayEquals(photo, wishlistedBy.first().avatarBytes)
        aliceWishlist.value = emptyList()
        bobWishlist.value = emptyList()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.monuments.single().wishlistedBy.isEmpty())
      }

  @Test
  fun wishlistFriendsErrorCanBeRetried() =
      runTest(dispatcher) {
        val friends = mockk<FriendRepository>()
        val wishlists = mockk<FriendWishlistRepository>()
        val profiles = mockk<PublicProfileRepository>()
        coEvery { friends.getFriends() } throws IllegalStateException("offline") andThen emptyList()
        val viewModel =
            WishlistViewModel(
                repository,
                friendWishlistsSource =
                    FriendWishlistedMonumentsSource(friends, wishlists, profiles),
            )
        viewModel.retryLoadingFriends()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.wishlistedByError)
        viewModel.retryLoadingFriends()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.wishlistedByError)
      }

  @Test
  fun addsRemovesAndRestoresUsingOriginalReference() =
      runTest(dispatcher) {
        val viewModel = WishlistViewModel(repository)
        viewModel.loadWishlist()
        advanceUntilIdle()
        viewModel.toggleGuide("friend", guide)
        advanceUntilIdle()
        val referenceKey = WishlistMonumentKey.fromFallback("friend", guide.id)
        assertEquals(referenceKey, repository.items.value.single().monumentKey)
        assertEquals("friend", repository.items.value.single().sourceOwnerUid)
        viewModel.setWishlisted(referenceKey, false)
        advanceUntilIdle()
        assertTrue(repository.items.value.isEmpty())
        assertFalse(viewModel.uiState.value.monuments.single().isWishlisted)
        viewModel.setWishlisted(referenceKey, true)
        advanceUntilIdle()
        assertEquals("guide", repository.items.value.single().sourceGuideId)
        assertTrue(viewModel.uiState.value.monuments.single().isWishlisted)
      }

  @Test
  fun retryingScannedFriendsWithoutASourceDoesNothing() =
      runTest(dispatcher) {
        val viewModel = WishlistViewModel(repository)
        viewModel.retryLoadingScannedFriends()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.scannedByError)
      }

  @Test
  fun resolvedLocationNamesAreCachedAcrossUpdates() =
      runTest(dispatcher) {
        val second = item.copy(monumentKey = "ref_other~guide", sourceOwnerUid = "other")
        repository.items.value = listOf(item, second)
        repository.guides[item.monumentKey] = guide
        repository.guides[second.monumentKey] = guide
        var calls = 0
        val viewModel =
            WishlistViewModel(repository) {
              calls++
              LocationName("Paris", "France")
            }
        viewModel.loadWishlist()
        advanceUntilIdle()
        val callsAfterLoad = calls
        assertEquals(listOf("Paris", "Paris"), viewModel.uiState.value.monuments.map { it.city })
        repository.items.value = listOf(item, second.copy(addedAt = 99))
        advanceUntilIdle()
        assertEquals(callsAfterLoad, calls)
      }

  @Test
  fun unknownWishlistItemIsReportedAsAnError() =
      runTest(dispatcher) {
        val viewModel = WishlistViewModel(repository)
        viewModel.loadWishlist()
        advanceUntilIdle()
        viewModel.setWishlisted("missing", true)
        assertNotNull(viewModel.uiState.value.mutationError)
      }

  @Test
  fun invalidGuideIdentityIsReportedAsAnError() =
      runTest(dispatcher) {
        val viewModel = WishlistViewModel(repository)
        viewModel.loadWishlist()
        advanceUntilIdle()
        viewModel.toggleGuide("bad/owner", guide)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.mutationError)
        assertTrue(repository.items.value.isEmpty())
      }

  @Test
  fun writeFailureIsReportedWithoutChangingMembership() =
      runTest(dispatcher) {
        repository.items.value = listOf(item)
        val viewModel = WishlistViewModel(repository)
        viewModel.loadWishlist()
        advanceUntilIdle()
        repository.writeError = IllegalStateException("offline")
        viewModel.setWishlisted(key, false)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.monuments.single().isWishlisted)
        assertNotNull(viewModel.uiState.value.mutationError)
        viewModel.clearMutationError()
        assertNull(viewModel.uiState.value.mutationError)
      }

  @Test
  fun sharedCoordinatesAllowSeparateGuidesAcrossOwners() =
      runTest(dispatcher) {
        repository.items.value = listOf(item)
        val viewModel = WishlistViewModel(repository)
        viewModel.loadWishlist()
        advanceUntilIdle()
        viewModel.toggleGuide("other", guide.copy(id = "other-guide"))
        advanceUntilIdle()
        assertEquals(2, repository.items.value.size)
        assertEquals(
            setOf("friend", "other"),
            repository.items.value.map { it.sourceOwnerUid }.toSet(),
        )
        assertEquals(2, viewModel.uiState.value.monuments.size)
        assertEquals(key, viewModel.uiState.value.guideKeys["friend" to "guide"])
        viewModel.toggleGuide("other", guide.copy(id = "other-guide"))
        advanceUntilIdle()
        assertEquals("friend", repository.items.value.single().sourceOwnerUid)
        assertEquals(key, repository.items.value.single().monumentKey)
      }

  @Test
  fun missingCoordinatesUseTheSourceReference() =
      runTest(dispatcher) {
        val viewModel = WishlistViewModel(repository)
        viewModel.loadWishlist()
        advanceUntilIdle()
        viewModel.toggleGuide("friend", guide.copy(location = null))
        advanceUntilIdle()
        assertEquals("ref_friend~guide", repository.items.value.single().monumentKey)
      }

  @Test
  fun legacyCoordinateEntryCanBeRemovedAndRestored() =
      runTest(dispatcher) {
        repository.items.value = listOf(item)
        val viewModel = WishlistViewModel(repository)
        viewModel.loadWishlist()
        advanceUntilIdle()
        assertEquals(key, viewModel.uiState.value.guideKeys["friend" to "guide"])
        viewModel.toggleGuide("friend", guide)
        advanceUntilIdle()
        assertTrue(repository.items.value.isEmpty())
        viewModel.setWishlisted(key, true)
        advanceUntilIdle()
        assertEquals(item, repository.items.value.single())
      }

  @Test
  fun writeThatNeverCompletesStillUpdatesTheStateAndStaysUsable() =
      runTest(dispatcher) {
        repository.items.value = listOf(item)
        repository.guides[key] = guide
        val never = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()
        val hangingRepository =
            object : WishlistRepository by repository {
              override suspend fun addToWishlist(item: WishlistItem): WishlistItem {
                writes += "add"
                never.await()
                return item
              }

              override suspend fun removeFromWishlist(monumentKey: String) {
                writes += "remove"
                never.await()
              }
            }
        val viewModel = WishlistViewModel(hangingRepository)
        viewModel.loadWishlist()
        advanceUntilIdle()

        viewModel.setWishlisted(key, false)
        assertFalse(viewModel.uiState.value.monuments.single().isWishlisted)
        assertFalse(key in viewModel.uiState.value.wishlistedKeys)
        assertEquals(false, viewModel.uiState.value.mutationSuccess?.added)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.monuments.single().isWishlisted)

        viewModel.setWishlisted(key, true)
        assertTrue(viewModel.uiState.value.monuments.single().isWishlisted)
        assertEquals(true, viewModel.uiState.value.mutationSuccess?.added)
        advanceUntilIdle()
        assertEquals(listOf("remove", "add"), writes)
        assertNull(viewModel.uiState.value.mutationError)
      }

  @Test
  fun failureOfAnOlderWriteDoesNotUndoANewerChange() =
      runTest(dispatcher) {
        repository.items.value = listOf(item)
        repository.guides[key] = guide
        val fail = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        val mixedRepository =
            object : WishlistRepository by repository {
              override suspend fun removeFromWishlist(monumentKey: String) {
                fail.await()
                throw IllegalStateException("write failed")
              }

              override suspend fun addToWishlist(item: WishlistItem): WishlistItem {
                never.await()
                return item
              }
            }
        val viewModel = WishlistViewModel(mixedRepository)
        viewModel.loadWishlist()
        advanceUntilIdle()
        viewModel.setWishlisted(key, false)
        viewModel.setWishlisted(key, true)
        fail.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.monuments.single().isWishlisted)
        assertNull(viewModel.uiState.value.mutationError)
      }

  @Test
  fun retryRecoversAfterLoadingFails() =
      runTest(dispatcher) {
        repository.loadError = IllegalStateException("offline")
        val viewModel = WishlistViewModel(repository)
        viewModel.loadWishlist()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)
        repository.loadError = null
        repository.items.value = listOf(item)
        viewModel.retryLoadingWishlist()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.error)
        assertEquals(listOf("Louvre"), viewModel.uiState.value.monuments.map { it.name })
      }

  @Test
  fun mutationsAreBlockedWhileLoadingOrAfterLoadFailure() =
      runTest(dispatcher) {
        val viewModel = WishlistViewModel(repository)
        viewModel.loadWishlist()
        viewModel.toggleGuide("friend", guide)
        advanceUntilIdle()
        assertTrue(repository.items.value.isEmpty())

        repository.loadError = IllegalStateException("offline")
        viewModel.loadWishlist()
        advanceUntilIdle()
        viewModel.toggleGuide("friend", guide)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)
        assertTrue(repository.items.value.isEmpty())
      }

  @Test
  fun successFeedbackIsShownAtOnceAndCanBeConsumed() =
      runTest(dispatcher) {
        val viewModel = WishlistViewModel(repository)
        viewModel.loadWishlist()
        advanceUntilIdle()
        viewModel.toggleGuide("friend", guide)
        assertTrue(("friend" to guide.id) in viewModel.uiState.value.guideKeys)
        val success = viewModel.uiState.value.mutationSuccess
        assertEquals(true, success?.added)
        advanceUntilIdle()
        assertEquals(1, repository.items.value.size)
        viewModel.clearMutationSuccess(checkNotNull(success).id)
        assertNull(viewModel.uiState.value.mutationSuccess)
      }

  @Test
  fun optimisticRemovalSurvivesStaleSnapshotAndRollsBackOnFailure() =
      runTest(dispatcher) {
        repository.items.value = listOf(item)
        repository.guides[key] = guide
        val release = CompletableDeferred<Unit>()
        val delayedRepository =
            object : WishlistRepository by repository {
              override suspend fun removeFromWishlist(monumentKey: String) {
                release.await()
                throw IllegalStateException("write failed")
              }
            }
        val viewModel = WishlistViewModel(delayedRepository)
        viewModel.loadWishlist()
        advanceUntilIdle()
        viewModel.setWishlisted(key, false)
        assertFalse(viewModel.uiState.value.monuments.single().isWishlisted)
        assertFalse(key in viewModel.uiState.value.wishlistedKeys)
        repository.items.value = listOf(item.copy(addedAt = 11))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.monuments.single().isWishlisted)
        release.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.monuments.single().isWishlisted)
        assertTrue(key in viewModel.uiState.value.wishlistedKeys)
        assertNotNull(viewModel.uiState.value.mutationError)
        assertNull(viewModel.uiState.value.mutationSuccess)
      }

  @Test
  fun optimisticAdditionRollsBackOnFailure() =
      runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        val delayedRepository =
            object : WishlistRepository by repository {
              override suspend fun addToWishlist(item: WishlistItem): WishlistItem {
                release.await()
                throw IllegalStateException("write failed")
              }
            }
        val viewModel = WishlistViewModel(delayedRepository)
        viewModel.loadWishlist()
        advanceUntilIdle()
        viewModel.toggleGuide("friend", guide)
        assertTrue(("friend" to guide.id) in viewModel.uiState.value.guideKeys)
        release.complete(Unit)
        advanceUntilIdle()
        assertFalse(("friend" to guide.id) in viewModel.uiState.value.guideKeys)
        assertNotNull(viewModel.uiState.value.mutationError)
      }

  @Test
  fun confirmedMutationRemainsVisibleUntilSnapshotCatchesUp() =
      runTest(dispatcher) {
        val delayedSnapshotRepository =
            object : WishlistRepository by repository {
              override suspend fun addToWishlist(item: WishlistItem): WishlistItem = item
            }
        val viewModel = WishlistViewModel(delayedSnapshotRepository)
        viewModel.loadWishlist()
        advanceUntilIdle()
        viewModel.toggleGuide("friend", guide)
        advanceUntilIdle()
        val addedKey = WishlistMonumentKey.forSharedGuide("friend", guide.id)
        assertTrue(addedKey in viewModel.uiState.value.wishlistedKeys)

        repository.items.value = listOf(item)
        advanceUntilIdle()
        assertTrue(addedKey in viewModel.uiState.value.wishlistedKeys)
        val added = WishlistItem(addedKey, "friend", guide.id, guide.monumentName, 20)
        repository.items.value = listOf(added)
        advanceUntilIdle()
        assertTrue(addedKey in viewModel.uiState.value.wishlistedKeys)
        repository.items.value = emptyList()
        advanceUntilIdle()
        assertFalse(addedKey in viewModel.uiState.value.wishlistedKeys)
      }

  @Test
  fun cancelledMutationClearsPendingStateWithoutReportingFailure() =
      runTest(dispatcher) {
        val cancelledRepository =
            object : WishlistRepository by repository {
              override suspend fun addToWishlist(item: WishlistItem): WishlistItem {
                throw CancellationException("cancelled")
              }
            }
        val viewModel = WishlistViewModel(cancelledRepository)
        viewModel.loadWishlist()
        advanceUntilIdle()
        viewModel.toggleGuide("friend", guide)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.mutationError)
        assertTrue(repository.items.value.isEmpty())
      }

  @Test
  fun slowLocationLookupDoesNotDelayWishlistUpdates() =
      runTest(dispatcher) {
        repository.items.value = listOf(item)
        repository.guides[key] = guide
        val release = CompletableDeferred<LocationName?>()
        val viewModel = WishlistViewModel(repository) { release.await() }
        viewModel.loadWishlist()
        advanceUntilIdle()
        repository.items.value = emptyList()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.monuments.single().isWishlisted)
        assertTrue(viewModel.uiState.value.wishlistedKeys.isEmpty())
        release.complete(LocationName("Paris", "France"))
        advanceUntilIdle()
        assertEquals("Paris", viewModel.uiState.value.monuments.single().city)
      }

  @Test
  fun failingLocationLookupKeepsCoordinatesAndKeepsObserving() =
      runTest(dispatcher) {
        repository.items.value = listOf(item)
        repository.guides[key] = guide
        val viewModel = WishlistViewModel(repository) { throw IllegalStateException("geocoder") }
        viewModel.loadWishlist()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.error)
        val monument = viewModel.uiState.value.monuments.single()
        assertEquals(guide.location, monument.location)
        assertEquals("", monument.city)
        val second = item.copy(monumentKey = "ref_other~guide", sourceOwnerUid = "other")
        repository.items.value = listOf(item, second)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.error)
        assertEquals(2, viewModel.uiState.value.monuments.size)
      }

  @Test
  fun deletedOrUnsharedGuidesStopLookingAvailableWhenFriendsGuidesChange() =
      runTest(dispatcher) {
        val friends = mockk<FriendRepository>()
        val sharing = mockk<MonumentSharingRepository>()
        val profiles = mockk<PublicProfileRepository>()
        val aliceGuide = guide.copy(id = "alice-guide")
        val shared = MutableStateFlow(listOf(aliceGuide))
        coEvery { friends.getFriends() } returns listOf(Friendship("a", "alice"))
        every { sharing.observeSharedGuidesFrom("alice") } returns shared
        coEvery { profiles.getProfile("alice") } returns PublicProfile("alice", "Alice")
        val aliceKey = WishlistMonumentKey.forSharedGuide("alice", aliceGuide.id)
        repository.items.value =
            listOf(WishlistItem(aliceKey, "alice", aliceGuide.id, aliceGuide.monumentName, 1))
        repository.guides[aliceKey] = aliceGuide
        val viewModel =
            WishlistViewModel(repository, FriendScannedMonumentsSource(friends, sharing, profiles))
        viewModel.loadWishlist()
        viewModel.retryLoadingScannedFriends()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.monuments.single().unavailable)
        assertNotNull(viewModel.uiState.value.monuments.single().photoBytes)

        repository.guides.remove(aliceKey)
        shared.value = emptyList()
        advanceUntilIdle()
        val monument = viewModel.uiState.value.monuments.single()
        assertTrue(monument.unavailable)
        assertEquals(WishlistUnavailableReason.GUIDE_DELETED, monument.unavailableReason)
        assertNull(monument.photoBytes)
        assertNull(monument.sourceGuideId)
      }

  @Test
  fun unknownFailureIsRetryableAndKeepsTheReason() =
      runTest(dispatcher) {
        repository.items.value = listOf(item)
        var result: ResolvedWishlistItem =
            ResolvedWishlistItem.Unavailable(item, WishlistUnavailableReason.UNKNOWN)
        val flakyRepository =
            object : WishlistRepository by repository {
              override suspend fun getResolvedWishlist() = listOf(result)
            }
        val viewModel = WishlistViewModel(flakyRepository)
        viewModel.loadWishlist()
        advanceUntilIdle()
        val failed = viewModel.uiState.value.monuments.single()
        assertTrue(failed.unavailable)
        assertEquals(WishlistUnavailableReason.UNKNOWN, failed.unavailableReason)

        result = ResolvedWishlistItem.Available(item, guide)
        viewModel.refreshAvailability()
        advanceUntilIdle()
        val recovered = viewModel.uiState.value.monuments.single()
        assertFalse(recovered.unavailable)
        assertNull(recovered.unavailableReason)
        assertArrayEquals(guide.photoBytes, recovered.photoBytes)
      }

  @Test
  fun staleResolvedGuideIsNotUsedForUpdatedWishlistItem() =
      runTest(dispatcher) {
        val updated = item.copy(monumentName = "Updated Louvre")
        repository.items.value = listOf(updated)
        val staleRepository =
            object : WishlistRepository by repository {
              override suspend fun getResolvedWishlist(): List<ResolvedWishlistItem> =
                  listOf(ResolvedWishlistItem.Available(item, guide))
            }
        val viewModel = WishlistViewModel(staleRepository)
        viewModel.loadWishlist()
        advanceUntilIdle()
        val monument = viewModel.uiState.value.monuments.single()
        assertEquals("Updated Louvre", monument.name)
        assertTrue(monument.unavailable)
        assertNull(monument.sourceGuideId)
      }

  @Test
  fun missingFriendProfileStillAppearsInScannedBy() =
      runTest(dispatcher) {
        val friends = mockk<FriendRepository>()
        val sharing = mockk<MonumentSharingRepository>()
        val profiles = mockk<PublicProfileRepository>()
        coEvery { friends.getFriends() } returns listOf(Friendship("a", "friend"))
        every { sharing.observeSharedGuidesFrom("friend") } returns MutableStateFlow(listOf(guide))
        coEvery { profiles.getProfile("friend") } returns null
        repository.items.value = listOf(item)
        repository.guides[key] = guide
        val viewModel =
            WishlistViewModel(repository, FriendScannedMonumentsSource(friends, sharing, profiles))
        viewModel.loadWishlist()
        viewModel.retryLoadingScannedFriends()
        advanceUntilIdle()
        val friend = viewModel.uiState.value.monuments.single().scannedBy.single()
        assertEquals("", friend.name)
        assertNull(friend.avatarBytes)
      }

  @Test
  fun resolvingOneLocationDoesNotChangeOtherMonuments() =
      runTest(dispatcher) {
        val second = item.copy(monumentKey = "ref_other~guide", location = null)
        repository.items.value = listOf(item, second)
        val viewModel = WishlistViewModel(repository) { LocationName("Paris", "France") }
        viewModel.loadWishlist()
        advanceUntilIdle()
        val monuments = viewModel.uiState.value.monuments.associateBy { it.id }
        assertEquals("Paris", monuments.getValue(item.monumentKey).city)
        assertEquals("", monuments.getValue(second.monumentKey).city)
      }
}
