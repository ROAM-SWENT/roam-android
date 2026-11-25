// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.leaderboard

import com.github.roamswent.roam.model.profile.PublicProfile
import java.util.Locale

/**
 * Ranks [profiles], which must have distinct UIDs, by verified monument count.
 *
 * Higher counts come first. Users with the same count share a rank and the ranks after them are
 * skipped (1, 1, 3), so the rank depends only on the count. Within a tie the order is by username
 * ignoring case, then by UID, so it is the same on every load.
 */
internal fun rankLeaderboard(profiles: List<PublicProfile>): List<LeaderboardEntry> {
  val ordered =
      profiles.sortedWith(
          compareByDescending<PublicProfile> { it.verifiedMonumentCount }
              .thenBy { it.username.lowercase(Locale.ROOT) }
              .thenBy { it.uid }
      )
  var rank = 0
  return ordered.mapIndexed { index, profile ->
    if (index == 0 || profile.verifiedMonumentCount != ordered[index - 1].verifiedMonumentCount) {
      rank = index + 1
    }
    LeaderboardEntry(profile.uid, profile.username, profile.verifiedMonumentCount, rank)
  }
}
