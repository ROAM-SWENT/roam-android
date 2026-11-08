// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.model.friend

/** Manages friend requests and accepted friendships for the signed-in user. */
interface FriendRepository {
  /** Sends a request to [toUid], or returns the existing outgoing pending request. */
  suspend fun sendFriendRequest(toUid: String): FriendRequest

  /** Accepts a pending request addressed to the signed-in user. */
  suspend fun acceptFriendRequest(requestId: String)

  /** Rejects a pending request addressed to the signed-in user. */
  suspend fun rejectFriendRequest(requestId: String)

  /** Cancels a pending request sent by the signed-in user. */
  suspend fun cancelFriendRequest(requestId: String)

  /** Returns pending requests addressed to the signed-in user. */
  suspend fun getIncomingRequests(): List<FriendRequest>

  /** Returns pending requests sent by the signed-in user. */
  suspend fun getOutgoingRequests(): List<FriendRequest>

  /** Returns the signed-in user's accepted friendships. */
  suspend fun getFriends(): List<Friendship>

  /** Removes the accepted friendship with [friendUid]. */
  suspend fun removeFriend(friendUid: String)
}
