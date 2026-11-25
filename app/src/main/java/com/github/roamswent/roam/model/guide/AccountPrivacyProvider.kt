// Co-authored-by: Gemini 3.8 Flash <noreply@google.com>
// Co-authored-by: Gemini 4 Neon <noreply@google.com>

package com.github.roamswent.roam.model.guide

import com.github.roamswent.roam.model.follow.FollowRepository
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Provides the signed-in user's account privacy configuration and follow graph state. */
interface AccountPrivacyProvider {
  /**
   * Returns the current [AccountPrivacyState] for the signed-in user.
   *
   * @return The account privacy snapshot.
   */
  suspend fun getPrivacyState(): AccountPrivacyState

  /**
   * Returns whether the signed-in user's account is currently public.
   *
   * @return `true` if the account is public, `false` otherwise.
   */
  suspend fun isPublicAccount(): Boolean
}

/**
 * In-memory [AccountPrivacyProvider] returning a fixed [AccountPrivacyState].
 *
 * @property state The static privacy state returned by this provider.
 */
class StubAccountPrivacyProvider(private val state: AccountPrivacyState = AccountPrivacyState()) :
    AccountPrivacyProvider {
  override suspend fun getPrivacyState(): AccountPrivacyState = state

  override suspend fun isPublicAccount(): Boolean = state.isPublic
}

/**
 * Repository-backed [AccountPrivacyProvider] combining profile privacy flags with follow lists.
 *
 * @param publicProfileRepository Repository used to load the signed-in user's public profile.
 * @param followRepository Repository used to load the signed-in user's following and followers.
 */
class RepositoryAccountPrivacyProvider(
    private val publicProfileRepository: PublicProfileRepository,
    private val followRepository: FollowRepository,
) : AccountPrivacyProvider {
  override suspend fun getPrivacyState(): AccountPrivacyState {
    val profile = publicProfileRepository.getCurrentProfile() ?: return AccountPrivacyState()
    return coroutineScope {
      val followingDeferred = async { followRepository.getFollowing() }
      val followersDeferred = async { followRepository.getFollowers() }
      val following = followingDeferred.await()
      val followers = followersDeferred.await()
      AccountPrivacyState(
          isPublic = profile.isPublic,
          followedUids = following.mapTo(mutableSetOf()) { it.ownerUid },
          followerUids = followers.mapTo(mutableSetOf()) { it.followerUid },
          allowFriendRequests = profile.allowFriendRequests,
      )
    }
  }

  override suspend fun isPublicAccount(): Boolean =
      publicProfileRepository.getCurrentProfile()?.isPublic ?: false
}
