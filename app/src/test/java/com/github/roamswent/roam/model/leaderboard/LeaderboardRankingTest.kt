// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.leaderboard

import com.github.roamswent.roam.model.profile.PublicProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaderboardRankingTest {

  @Test
  fun noProfilesGiveAnEmptyLeaderboard() {
    assertTrue(rankLeaderboard(emptyList()).isEmpty())
  }

  @Test
  fun singleUserIsFirst() {
    assertEquals(
        listOf(LeaderboardEntry("me", "Me", 3, rank = 1)),
        rankLeaderboard(listOf(PublicProfile("me", "Me", 3))),
    )
  }

  @Test
  fun higherCountsComeFirstWithConsecutiveRanks() {
    val ranked =
        rankLeaderboard(
            listOf(
                PublicProfile("b", "Bea", 2),
                PublicProfile("a", "Ann", 9),
                PublicProfile("c", "Cal", 5),
            )
        )

    assertEquals(listOf("a", "c", "b"), ranked.map { it.uid })
    assertEquals(listOf(9, 5, 2), ranked.map { it.verifiedMonumentCount })
    assertEquals(listOf(1, 2, 3), ranked.map { it.rank })
  }

  @Test
  fun equalCountsShareACompetitionRank() {
    val ranked =
        rankLeaderboard(
            listOf(
                PublicProfile("c", "Cal", 1),
                PublicProfile("a", "Ann", 4),
                PublicProfile("b", "Bea", 4),
            )
        )

    assertEquals(listOf(1, 1, 3), ranked.map { it.rank })
  }

  @Test
  fun tiesInTheMiddleSkipTheFollowingRanks() {
    val ranked =
        rankLeaderboard(
            listOf(
                PublicProfile("a", "Ann", 7),
                PublicProfile("b", "Bea", 3),
                PublicProfile("c", "Cal", 3),
                PublicProfile("d", "Dan", 0),
            )
        )

    assertEquals(listOf(1, 2, 2, 4), ranked.map { it.rank })
  }

  @Test
  fun tiesAreOrderedByUsernameIgnoringCaseThenByUid() {
    val ranked =
        rankLeaderboard(
            listOf(
                PublicProfile("uid-3", "bob", 2),
                PublicProfile("uid-2", "Same", 2),
                PublicProfile("uid-1", "same", 2),
                PublicProfile("uid-4", "Alice", 2),
            )
        )

    assertEquals(listOf("uid-4", "uid-3", "uid-1", "uid-2"), ranked.map { it.uid })
  }

  @Test
  fun orderDoesNotDependOnInputOrder() {
    val profiles =
        listOf(
            PublicProfile("a", "Ann", 4),
            PublicProfile("b", "bea", 4),
            PublicProfile("c", "Cal", 6),
            PublicProfile("d", "Dan", 0),
        )

    assertEquals(rankLeaderboard(profiles), rankLeaderboard(profiles.reversed()))
  }

  @Test
  fun rankDependsOnlyOnCount() {
    val ranked = rankLeaderboard(listOf(PublicProfile("z", "Zed", 5), PublicProfile("a", "Amy", 5)))

    assertEquals(listOf("Amy", "Zed"), ranked.map { it.username })
    assertTrue(ranked.all { it.rank == 1 })
  }

  @Test
  fun profileWithoutCountRanksAsZero() {
    val ranked =
        rankLeaderboard(listOf(PublicProfile("new", "Newbie"), PublicProfile("a", "Ann", 1)))

    assertEquals(
        listOf(LeaderboardEntry("a", "Ann", 1, 1), LeaderboardEntry("new", "Newbie", 0, 2)),
        ranked,
    )
  }
}
