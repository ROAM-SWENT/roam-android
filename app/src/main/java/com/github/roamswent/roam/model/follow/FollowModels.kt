// Co-authored-by: Gemini 4 Neon <noreply@google.com>

package com.github.roamswent.roam.model.follow

/**
 * A unidirectional follow relationship where [followerUid] follows [ownerUid].
 *
 * @property id The Firestore document ID in the form `"$ownerUid~$followerUid"`.
 * @property ownerUid The UID of the public account being followed.
 * @property followerUid The UID of the user following [ownerUid].
 */
data class Follow(
    val id: String,
    val ownerUid: String,
    val followerUid: String,
)

/** A distinguishable failure from a follow repository operation. */
sealed class FollowRepositoryException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
  /** Thrown when no signed-in user is available for the operation. */
  class UnauthenticatedUser :
      FollowRepositoryException("A signed-in user is required for this operation.")

  /** Thrown when a provided UID does not match the valid Firebase UID format. */
  class InvalidUid : FollowRepositoryException("The user ID is not a valid Firebase UID.")

  /** Thrown when a user attempts to follow, unfollow, or remove themselves. */
  class SelfFollow : FollowRepositoryException("You cannot follow or unfollow yourself.")

  /** Thrown when the target user does not have a registered public profile. */
  class TargetProfileNotFound :
      FollowRepositoryException("The target user does not have a registered public profile.")

  /** Thrown when attempting to follow an account that is not set to public. */
  class AccountNotPublic : FollowRepositoryException("Only public accounts can be followed.")

  /** Thrown when the target user has blocked the signed-in user. */
  class BlockedByUser : FollowRepositoryException("You cannot follow this user.")

  /** Thrown when the expected follow relationship document does not exist. */
  class FollowNotFound : FollowRepositoryException("The follow relationship was not found.")

  /** Thrown when an underlying Firestore error occurs. */
  class FirestoreFailure(cause: Throwable) :
      FollowRepositoryException("The follow operation failed in Firestore.", cause)
}
