// Co-authored-by: Gemini 4 Neon <noreply@google.com>

package com.github.roamswent.roam.model.follow

/** Manages unidirectional follow relationships for the signed-in user. */
interface FollowRepository {
  /**
   * Follows the public account identified by [targetUid], or returns the existing follow record.
   *
   * @param targetUid The UID of the public account to follow.
   * @return The created or existing [Follow] record.
   */
  suspend fun follow(targetUid: String): Follow

  /**
   * Unfollows the account identified by [targetUid].
   *
   * @param targetUid The UID of the account to unfollow.
   */
  suspend fun unfollow(targetUid: String)

  /**
   * Removes [followerUid] from the signed-in user's followers.
   *
   * @param followerUid The UID of the follower to remove.
   * @param blacklist When `true`, also blocks [followerUid] and removes any existing friendship.
   */
  suspend fun removeFollower(followerUid: String, blacklist: Boolean = false)

  /**
   * Returns all follow relationships where the signed-in user is the follower.
   *
   * @return The list of accounts the signed-in user follows.
   */
  suspend fun getFollowing(): List<Follow>

  /**
   * Returns all follow relationships where the signed-in user is the followed account.
   *
   * @return The list of followers of the signed-in user.
   */
  suspend fun getFollowers(): List<Follow>

  /**
   * Checks whether the signed-in user currently follows [targetUid].
   *
   * @param targetUid The UID of the account to check.
   * @return `true` if the signed-in user follows [targetUid], `false` otherwise.
   */
  suspend fun isFollowing(targetUid: String): Boolean
}
