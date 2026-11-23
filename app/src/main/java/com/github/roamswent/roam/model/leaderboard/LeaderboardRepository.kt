// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.leaderboard

/**
 * Counts the monuments each user has verified and ranks the signed-in user among their friends.
 *
 * A monument is verified when the observed scan position is near the monument's own position, as
 * decided by [com.github.roamswent.roam.model.location.ProximityPolicy]. This is separate from
 * whether the guide is saved to the collection or shown on Explore.
 */
interface LeaderboardRepository {

  /**
   * Records that the signed-in user verified the monument identified by [monument]. Counts it only
   * if no key that [MonumentKey.isSameMonumentAs] it was recorded before, so re-scanning a monument
   * never raises the count.
   *
   * @throws LeaderboardException.UnauthenticatedUser if no user is signed in.
   * @throws LeaderboardException.FirestoreFailure if the count could not be stored.
   */
  suspend fun recordVerifiedMonument(monument: MonumentKey): VerifiedMonumentResult

  /**
   * Returns the signed-in user and their accepted friends who have a public profile, ordered by
   * [LeaderboardEntry.rank], then by username ignoring case, then by UID. Users who have verified
   * no monument appear with a count of 0.
   *
   * @throws LeaderboardException.UnauthenticatedUser if no user is signed in.
   * @throws LeaderboardException.FirestoreFailure if the leaderboard could not be loaded.
   */
  suspend fun getFriendsLeaderboard(): List<LeaderboardEntry>
}
