// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

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

  class RequestAlreadyPending :
      FriendRepositoryException("A friend request to this user is already pending.")

  class IncomingRequestPending :
      FriendRepositoryException("This user already has a pending request for you.")

  class AlreadyFriends : FriendRepositoryException("You are already friends with this user.")

  class RequestNotFound : FriendRepositoryException("The friend request was not found.")

  class RequestNotPending : FriendRepositoryException("The friend request is no longer pending.")

  class NotRequestRecipient :
      FriendRepositoryException("Only the recipient can accept or reject this request.")

  class NotRequestSender : FriendRepositoryException("Only the requester can cancel this request.")

  class FriendshipNotFound : FriendRepositoryException("The friendship was not found.")

  class FirestoreFailure(cause: Throwable) :
      FriendRepositoryException("The friend operation failed in Firestore.", cause)
}
