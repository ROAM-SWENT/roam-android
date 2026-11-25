// Co-authored-by: Gemini 4 Neon <noreply@google.com>

package com.github.roamswent.roam.model.guide

import com.github.roamswent.roam.model.follow.Follow
import com.github.roamswent.roam.model.follow.FollowRepository
import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountPrivacyProviderTest {
  private val publicProfileRepository = mockk<PublicProfileRepository>()
  private val followRepository = mockk<FollowRepository>()
  private val provider = RepositoryAccountPrivacyProvider(publicProfileRepository, followRepository)

  @Test
  fun stubAccountPrivacyProvider_returnsDefaultAndCustomStates() = runTest {
    val defaultProvider = StubAccountPrivacyProvider()
    val defaultState = defaultProvider.getPrivacyState()
    assertFalse(defaultState.isPublic)
    assertTrue(defaultState.allowFriendRequests)
    assertTrue(defaultState.followedUids.isEmpty())
    assertTrue(defaultState.followerUids.isEmpty())
    assertFalse(defaultProvider.isPublicAccount())

    val customState =
        AccountPrivacyState(
            isPublic = true,
            followedUids = setOf("followed-1"),
            followerUids = setOf("follower-1"),
            allowFriendRequests = false,
        )
    val customProvider = StubAccountPrivacyProvider(customState)
    assertEquals(customState, customProvider.getPrivacyState())
    assertTrue(customProvider.isPublicAccount())
  }

  @Test
  fun getPrivacyState_returnsDefaultAndSkipsFollowRepositoryWhenProfileIsNull() = runTest {
    coEvery { publicProfileRepository.getCurrentProfile() } returns null

    val result = provider.getPrivacyState()

    assertEquals(AccountPrivacyState(), result)
    coVerify(exactly = 0) { followRepository.getFollowing() }
    coVerify(exactly = 0) { followRepository.getFollowers() }
  }

  @Test
  fun getPrivacyState_combinesPublicProfileAndDistinctFollowSets() = runTest {
    coEvery { publicProfileRepository.getCurrentProfile() } returns
        PublicProfile(
            uid = "me",
            username = "Roamer",
            isPublic = true,
            allowFriendRequests = false,
        )
    coEvery { followRepository.getFollowing() } returns
        listOf(
            Follow("owner-1~me", ownerUid = "owner-1", followerUid = "me"),
            Follow("owner-2~me", ownerUid = "owner-2", followerUid = "me"),
        )
    coEvery { followRepository.getFollowers() } returns
        listOf(
            Follow("me~follower-1", ownerUid = "me", followerUid = "follower-1"),
        )

    val result = provider.getPrivacyState()

    assertEquals(
        AccountPrivacyState(
            isPublic = true,
            followedUids = setOf("owner-1", "owner-2"),
            followerUids = setOf("follower-1"),
            allowFriendRequests = false,
        ),
        result,
    )
  }

  @Test
  fun getPrivacyState_combinesPrivateProfileAndDistinctFollowSets() = runTest {
    coEvery { publicProfileRepository.getCurrentProfile() } returns
        PublicProfile(
            uid = "me",
            username = "Roamer",
            isPublic = false,
            allowFriendRequests = true,
        )
    coEvery { followRepository.getFollowing() } returns
        listOf(Follow("owner-9~me", ownerUid = "owner-9", followerUid = "me"))
    coEvery { followRepository.getFollowers() } returns
        listOf(
            Follow("me~follower-8", ownerUid = "me", followerUid = "follower-8"),
            Follow("me~follower-9", ownerUid = "me", followerUid = "follower-9"),
        )

    val result = provider.getPrivacyState()

    assertEquals(
        AccountPrivacyState(
            isPublic = false,
            followedUids = setOf("owner-9"),
            followerUids = setOf("follower-8", "follower-9"),
            allowFriendRequests = true,
        ),
        result,
    )
  }

  @Test
  fun isPublicAccount_reflectsProfileVisibilityWithoutCallingFollowRepository() = runTest {
    coEvery { publicProfileRepository.getCurrentProfile() } returns
        PublicProfile("me", "Roamer", isPublic = true)
    assertTrue(provider.isPublicAccount())

    coEvery { publicProfileRepository.getCurrentProfile() } returns
        PublicProfile("me", "Roamer", isPublic = false)
    assertFalse(provider.isPublicAccount())

    coEvery { publicProfileRepository.getCurrentProfile() } returns null
    assertFalse(provider.isPublicAccount())

    coVerify(exactly = 0) { followRepository.getFollowing() }
    coVerify(exactly = 0) { followRepository.getFollowers() }
  }

  @Test
  fun provider_propagatesRepositoryExceptionsAndCancellation() = runTest {
    val profileFailure = IllegalStateException("Profile lookup failed")
    coEvery { publicProfileRepository.getCurrentProfile() } throws profileFailure
    assertSame(profileFailure, captureFailure { provider.getPrivacyState() })
    assertSame(profileFailure, captureFailure { provider.isPublicAccount() })

    val followFailure = IllegalStateException("Follow lookup failed")
    coEvery { publicProfileRepository.getCurrentProfile() } returns
        PublicProfile("me", "Roamer", isPublic = true)
    coEvery { followRepository.getFollowing() } throws followFailure
    coEvery { followRepository.getFollowers() } returns emptyList()
    val capturedFollowFailure = captureFailure { provider.getPrivacyState() }
    assertTrue(capturedFollowFailure is IllegalStateException)
    assertEquals("Follow lookup failed", capturedFollowFailure.message)

    val cancellation = CancellationException("Cancelled")
    coEvery { publicProfileRepository.getCurrentProfile() } throws cancellation
    assertTrue(captureFailure { provider.getPrivacyState() } is CancellationException)
    assertTrue(captureFailure { provider.isPublicAccount() } is CancellationException)
  }

  private suspend fun captureFailure(action: suspend () -> Any?): Exception {
    try {
      action()
    } catch (exception: Exception) {
      return exception
    }
    throw AssertionError("Expected operation to fail")
  }
}
