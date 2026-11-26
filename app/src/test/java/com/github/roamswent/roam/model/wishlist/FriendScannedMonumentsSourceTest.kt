// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.model.wishlist

import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.friend.Friendship
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.guide.MonumentSharingRepository
import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FriendScannedMonumentsSourceTest {
  private val friends = mockk<FriendRepository>()
  private val sharing = mockk<MonumentSharingRepository>()
  private val profiles = mockk<PublicProfileRepository>()
  private val source = FriendScannedMonumentsSource(friends, sharing, profiles)

  private fun guide(name: String) =
      Guide(
          photoBytes = ByteArray(0),
          monumentName = name,
          confidence = 1.0,
          history = "",
          facts = emptyList(),
          culture = "",
      )

  private fun friend(vararg uids: String) {
    coEvery { friends.getFriends() } returns uids.map { Friendship("id-$it", it) }
  }

  private fun shares(uid: String, vararg names: String) {
    every(uid, MutableStateFlow(names.map(::guide)))
  }

  private fun every(uid: String, flow: kotlinx.coroutines.flow.Flow<List<Guide>>) {
    io.mockk.every { sharing.observeSharedGuidesFrom(uid) } returns flow
  }

  private fun profile(uid: String) {
    coEvery { profiles.getProfile(uid) } returns PublicProfile(uid, "user_$uid")
  }

  @Test
  fun noFriendsEmitsEmptyList() = runTest {
    friend()
    assertEquals(emptyList<FriendScannedMonument>(), source.observe().first())
  }

  @Test
  fun normalizesNamesAndCountsUniqueFriends() = runTest {
    friend("a", "b")
    shares("a", "  Eiffel   Tower ", "eiffel tower")
    shares("b", "EIFFEL\tTOWER", "Louvre")
    profile("a")
    profile("b")

    val result = source.observe().first()

    assertEquals(listOf("eiffel tower", "louvre"), result.map { it.key })
    val eiffel = result.first()
    assertEquals("Eiffel Tower", eiffel.displayName)
    assertEquals(2, eiffel.friendCount)
    assertEquals(listOf("a", "b"), eiffel.friends.map { it.uid })
    assertEquals(1, result[1].friendCount)
  }

  @Test
  fun duplicateGuidesFromOneFriendCountOnce() = runTest {
    friend("a")
    shares("a", "Louvre", "louvre", " LOUVRE ")
    profile("a")

    val result = source.observe().first()

    assertEquals(1, result.size)
    assertEquals(1, result[0].friendCount)
    assertEquals(1, result[0].friends.size)
  }

  @Test
  fun blankNamesAreIgnored() = runTest {
    friend("a")
    shares("a", "   ", "")
    assertTrue(source.observe().first().isEmpty())
  }

  @Test
  fun friendWithoutProfileIsKeptWithNullProfile() = runTest {
    friend("a", "b")
    shares("a", "Louvre")
    shares("b", "Louvre")
    profile("a")
    coEvery { profiles.getProfile("b") } returns null

    val result = source.observe().first().single()

    assertEquals(2, result.friendCount)
    assertEquals(listOf("a", "b"), result.friends.map { it.uid })
    assertEquals("user_a", result.friends[0].profile?.username)
    assertEquals(null, result.friends[1].profile)
  }

  @Test
  fun profilesAreLoadedOncePerFriendAndOnlyForSharers() = runTest {
    friend("a", "b")
    shares("a", "Louvre")
    shares("b")
    profile("a")

    source.observe().first()

    coVerify(exactly = 1) { profiles.getProfile("a") }
    coVerify(exactly = 0) { profiles.getProfile("b") }
  }

  @Test
  fun liveSharedUpdatesAreEmitted() =
      runTest(UnconfinedTestDispatcher()) {
        friend("a")
        val shared = MutableStateFlow(listOf(guide("Louvre")))
        every("a", shared)
        profile("a")
        val emissions = mutableListOf<List<String>>()
        val job = launch { source.observe().collect { emissions += it.map { m -> m.displayName } } }

        shared.value = listOf(guide("Louvre"), guide("Big  Ben"))
        shared.value = emptyList()
        job.cancel()

        assertEquals(listOf(listOf("Louvre"), listOf("Louvre", "Big Ben"), emptyList()), emissions)
      }

  @Test
  fun friendLoadErrorIsPropagated() = runTest {
    coEvery { friends.getFriends() } throws IllegalStateException("friends")
    try {
      source.observe().toList()
      fail("expected exception")
    } catch (e: IllegalStateException) {
      assertEquals("friends", e.message)
    }
  }

  @Test
  fun sharedGuidesErrorIsPropagated() = runTest {
    friend("a")
    every("a", flow { throw IllegalStateException("shared") })
    try {
      source.observe().toList()
      fail("expected exception")
    } catch (e: IllegalStateException) {
      assertEquals("shared", e.message)
    }
  }

  @Test
  fun profileErrorIsPropagated() = runTest {
    friend("a")
    shares("a", "Louvre")
    coEvery { profiles.getProfile("a") } throws IllegalStateException("profile")
    try {
      source.observe().first()
      fail("expected exception")
    } catch (e: IllegalStateException) {
      assertEquals("profile", e.message)
    }
  }

  @Test
  fun cancellationStopsCollection() =
      runTest(UnconfinedTestDispatcher()) {
        friend("a")
        val shared = MutableStateFlow(listOf(guide("Louvre")))
        every("a", shared)
        profile("a")
        var count = 0
        val job = launch { source.observe().collect { count++ } }
        job.cancel()
        shared.value = emptyList()
        assertEquals(1, count)
        assertFalse(job.isActive)
      }
}
