// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.model.wishlist

import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.friend.Friendship
import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FriendWishlistedMonumentsSourceTest {
  private val friends = mockk<FriendRepository>()
  private val wishlists = mockk<FriendWishlistRepository>()
  private val profiles = mockk<PublicProfileRepository>()
  private val source = FriendWishlistedMonumentsSource(friends, wishlists, profiles)

  private fun friend(vararg uids: String) {
    coEvery { friends.getFriends() } returns uids.map { Friendship("id-$it", it) }
  }

  private fun wishes(uid: String, vararg names: String) {
    wishes(uid, MutableStateFlow(names.toList()))
  }

  private fun wishes(uid: String, flow: Flow<List<String>>) {
    every { wishlists.observeWishlistedMonumentNames(uid) } returns flow
  }

  private fun profile(uid: String) {
    coEvery { profiles.getProfile(uid) } returns PublicProfile(uid, "user_$uid")
  }

  @Test
  fun noFriendsEmitsEmptyList() = runTest {
    friend()
    assertEquals(emptyList<FriendWishlistedMonument>(), source.observe().first())
  }

  @Test
  fun matchesDifferentReferencesByNormalizedName() = runTest {
    friend("b", "a")
    wishes("a", "  Eiffel   Tower ", "eiffel tower")
    wishes("b", "EIFFEL\tTOWER", "Louvre")
    profile("a")
    profile("b")

    val result = source.observe().first()

    assertEquals(listOf("eiffel tower", "louvre"), result.map { it.key })
    val eiffel = result.first()
    assertEquals("Eiffel Tower", eiffel.displayName)
    assertEquals(2, eiffel.friendCount)
    assertEquals(listOf("a", "b"), eiffel.friends.map { it.uid })
    assertEquals("user_a", eiffel.friends[0].profile?.username)
    assertEquals(1, result[1].friendCount)
  }

  @Test
  fun blankNamesAreIgnored() = runTest {
    friend("a")
    wishes("a", "   ", "")
    assertTrue(source.observe().first().isEmpty())
  }

  @Test
  fun friendWithoutProfileIsKeptWithNullProfile() = runTest {
    friend("a", "b")
    wishes("a", "Louvre")
    wishes("b", "Louvre")
    profile("a")
    coEvery { profiles.getProfile("b") } returns null

    val result = source.observe().first().single()

    assertEquals(listOf("a", "b"), result.friends.map { it.uid })
    assertEquals(null, result.friends[1].profile)
  }

  @Test
  fun profilesAreLoadedOncePerFriendAndOnlyForWishers() = runTest {
    friend("a", "b")
    wishes("a", "Louvre")
    wishes("b")
    profile("a")

    source.observe().first()

    coVerify(exactly = 1) { profiles.getProfile("a") }
    coVerify(exactly = 0) { profiles.getProfile("b") }
  }

  @Test
  fun liveUpdatesAreEmitted() =
      runTest(UnconfinedTestDispatcher()) {
        friend("a")
        val live = MutableStateFlow(listOf("Louvre"))
        wishes("a", live)
        profile("a")
        val emissions = mutableListOf<List<String>>()
        val job = launch { source.observe().collect { emissions += it.map { m -> m.displayName } } }

        live.value = listOf("Louvre", "Big  Ben")
        live.value = emptyList()
        job.cancel()

        assertEquals(listOf(listOf("Louvre"), listOf("Louvre", "Big Ben"), emptyList()), emissions)
      }

  @Test
  fun friendLoadErrorIsPropagated() = runTest {
    coEvery { friends.getFriends() } throws IllegalStateException("friends")
    assertPropagated("friends") { source.observe().toList() }
  }

  @Test
  fun wishlistErrorIsPropagated() = runTest {
    friend("a")
    wishes("a", flow { throw IllegalStateException("wishlist") })
    assertPropagated("wishlist") { source.observe().toList() }
  }

  @Test
  fun wishlistObservationSetupErrorIsPropagated() = runTest {
    friend("a")
    every { wishlists.observeWishlistedMonumentNames("a") } throws
        WishlistRepositoryException.UnauthenticatedUser()
    try {
      source.observe().toList()
      fail("expected exception")
    } catch (_: WishlistRepositoryException.UnauthenticatedUser) {}
  }

  @Test
  fun profileErrorIsPropagated() = runTest {
    friend("a")
    wishes("a", "Louvre")
    coEvery { profiles.getProfile("a") } throws IllegalStateException("profile")
    assertPropagated("profile") { source.observe().first() }
  }

  private suspend fun assertPropagated(message: String, block: suspend () -> Unit) {
    try {
      block()
      fail("expected exception")
    } catch (e: IllegalStateException) {
      assertEquals(message, e.message)
    }
  }
}
