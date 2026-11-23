// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.leaderboard

/**
 * One user on a leaderboard.
 *
 * @property uid Firebase Authentication UID of the user.
 * @property username Public username of the user.
 * @property verifiedMonumentCount Number of distinct monuments the user has verified by scanning
 *   them in person.
 * @property rank 1-based position; users with the same count share a rank and the next rank is
 *   skipped (1, 1, 3).
 */
data class LeaderboardEntry(
    val uid: String,
    val username: String,
    val verifiedMonumentCount: Int,
    val rank: Int,
)

/** Outcome of [LeaderboardRepository.recordVerifiedMonument]. */
sealed interface VerifiedMonumentResult {
  /** The total after recording, in both cases. */
  val verifiedMonumentCount: Int

  /** The monument had not been counted yet, so the total went up by one. */
  data class Counted(override val verifiedMonumentCount: Int) : VerifiedMonumentResult

  /** The same monument was already counted, so the total is unchanged. */
  data class AlreadyCounted(override val verifiedMonumentCount: Int) : VerifiedMonumentResult
}

/** A distinguishable failure from a leaderboard operation. */
sealed class LeaderboardException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
  /** No user is signed in. */
  class UnauthenticatedUser :
      LeaderboardException("A signed-in user is required for this operation.")

  /** The signed-in user has no public profile, which holds their count. */
  class ProfileNotRegistered :
      LeaderboardException("The signed-in user does not have a registered username.")

  /** The underlying Firestore operation failed. */
  class FirestoreFailure(cause: Throwable) :
      LeaderboardException("The leaderboard operation failed in Firestore.", cause)
}
