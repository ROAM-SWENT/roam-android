// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Gemini 4 Neon <noreply@google.com>

package com.github.roamswent.roam.model.friend

/** A pending request between two Firebase Authentication UIDs. */
data class FriendRequest(
    val id: String,
    val requesterUid: String,
    val recipientUid: String,
)

/** An accepted friendship, identified by the other user's UID. */
data class Friendship(
    val id: String,
    val friendUid: String,
)

/** A distinguishable failure from a friend operation. */
sealed class FriendRepositoryException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
  class UnauthenticatedUser :
      FriendRepositoryException("A signed-in user is required for this operation.")

  class InvalidUid : FriendRepositoryException("The user ID is not a valid Firebase UID.")

  class SelfFriendRequest :
      FriendRepositoryException("You cannot send a friend request to yourself.")

  class RecipientProfileNotFound :
      FriendRepositoryException("The recipient does not have a registered public profile.")

  class IncomingRequestPending :
      FriendRepositoryException("This user already has a pending request for you.")

  class AlreadyFriends : FriendRepositoryException("You are already friends with this user.")

  class RequestNotFound : FriendRepositoryException("The friend request was not found.")

  class RequestNotPending : FriendRepositoryException("The friend request is no longer pending.")

  class NotRequestRecipient :
      FriendRepositoryException("Only the recipient can accept or reject this request.")

  class NotRequestSender : FriendRepositoryException("Only the requester can cancel this request.")

  class FriendshipNotFound : FriendRepositoryException("The friendship was not found.")

  /** Thrown when the recipient has disabled incoming friend requests. */
  class FriendRequestsDisabled :
      FriendRepositoryException("This user is not accepting friend requests.")

  /** Thrown when the recipient has blocked the signed-in user. */
  class BlockedByUser : FriendRepositoryException("You cannot send a friend request to this user.")

  class FirestoreFailure(cause: Throwable) :
      FriendRepositoryException("The friend operation failed in Firestore.", cause)
}
