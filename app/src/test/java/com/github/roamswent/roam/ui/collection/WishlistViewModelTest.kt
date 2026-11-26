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
import com.github.roamswent.roam.model.wishlist.WishlistItem
import com.github.roamswent.roam.model.wishlist.WishlistMonumentKey
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
        assertTrue(viewModel.uiState.value.pendingKeys.isEmpty())
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
  fun pendingGuideRemainsBlockedAfterLegacyEntryDisappearsFromSnapshot() =
      runTest(dispatcher) {
        repository.items.value = listOf(item)
        val release = CompletableDeferred<Unit>()
        val delayedRepository =
            object : com.github.roamswent.roam.model.wishlist.WishlistRepository by repository {
              override suspend fun removeFromWishlist(monumentKey: String) {
                repository.removeFromWishlist(monumentKey)
                release.await()
              }
            }
        val viewModel = WishlistViewModel(delayedRepository)
        viewModel.loadWishlist()
        advanceUntilIdle()
        viewModel.toggleGuide("friend", guide)
        advanceUntilIdle()
        assertTrue(repository.items.value.isEmpty())
        assertTrue(("friend" to "guide") in viewModel.uiState.value.pendingGuides)
        viewModel.toggleGuide("friend", guide)
        advanceUntilIdle()
        assertTrue(repository.items.value.isEmpty())
        release.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.pendingGuides.isEmpty())
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
}
