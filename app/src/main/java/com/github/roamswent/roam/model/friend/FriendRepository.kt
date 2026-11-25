// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Gemini 4 Neon <noreply@google.com>

package com.github.roamswent.roam.model.friend

/** Manages friend requests, accepted friendships, and blocked users for the signed-in user. */
interface FriendRepository {
  /** Sends a request to [toUid], or returns the existing outgoing pending request. */
  suspend fun sendFriendRequest(toUid: String): FriendRequest

  /** Accepts a pending request addressed to the signed-in user. */
  suspend fun acceptFriendRequest(requestId: String)

  /**
   * Rejects a pending request addressed to the signed-in user.
   *
   * @param requestId The friendship document ID of the pending request.
   * @param blacklist When `true`, also blocks the requester and removes any incoming follow.
   */
  suspend fun rejectFriendRequest(requestId: String, blacklist: Boolean = false)

  /** Cancels a pending request sent by the signed-in user. */
  suspend fun cancelFriendRequest(requestId: String)

  /** Returns pending requests addressed to the signed-in user. */
  suspend fun getIncomingRequests(): List<FriendRequest>

  /** Returns pending requests sent by the signed-in user. */
  suspend fun getOutgoingRequests(): List<FriendRequest>

  /** Returns the signed-in user's accepted friendships. */
  suspend fun getFriends(): List<Friendship>

  /**
   * Removes the accepted friendship with [friendUid].
   *
   * @param friendUid The UID of the friend to remove.
   * @param blacklist When `true`, also blocks [friendUid] and removes any incoming follow.
   */
  suspend fun removeFriend(friendUid: String, blacklist: Boolean = false)

  /**
   * Blocks [blockedUid], removing any existing friendship or incoming follow from them.
   *
   * @param blockedUid The UID of the user to block.
   */
  suspend fun blockUser(blockedUid: String)

  /**
   * Removes [blockedUid] from the signed-in user's blocklist.
   *
   * @param blockedUid The UID of the user to unblock.
   */
  suspend fun unblockUser(blockedUid: String)

  /**
   * Returns the set of UIDs currently blocked by the signed-in user.
   *
   * @return The blocked user UIDs.
   */
  suspend fun getBlockedUids(): Set<String>
}
