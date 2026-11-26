// Co-authored-by: Codex <codex@openai.com>
package com.github.roamswent.roam.ui.explore

import com.github.roamswent.roam.model.location.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Test

class ExploreFriendPinTest {
  @Test
  fun nearbyScansDoNotChainTogetherDistantPinsOrMergeDifferentLandmarks() {
    val groups =
        listOf(
                shares("alice", pin("a", "Clock tower", 0.0)),
                shares("bob", pin("b", " clock TOWER ", 0.00035)),
                shares("cora", pin("c", "Clock tower", 0.00070)),
                shares("dora", pin("d", "Town hall", 0.0)),
                shares("emma"),
            )
            .groupFriendPins()

    assertEquals(
        setOf(setOf("a", "b"), setOf("c"), setOf("d")),
        groups.map { group -> group.pins.map { it.monument.guideId }.toSet() }.toSet(),
    )
  }

  @Test
  fun repeatSharesKeepEveryGuideAndCountEachFriendOnceForOverflow() {
    val group =
        listOf(
                shares("alice", pin("a", "Tower", 0.0), pin("a2", "Tower", 0.0)),
                shares("bob", pin("b", "Tower", 0.0)),
                shares("cora", pin("c", "Tower", 0.0)),
                shares("dora", pin("d", "Tower", 0.0)),
            )
            .groupFriendPins()
            .single()

    assertEquals(listOf("alice", "bob", "cora"), group.visibleOwnerUids)
    assertEquals(1, group.additionalOwnerCount)
    assertEquals(setOf("a", "a2", "b", "c", "d"), group.pins.map { it.monument.guideId }.toSet())
  }

  private fun pin(guideId: String, name: String, latitude: Double) =
      ExploreMonument(guideId, name, Coordinates(latitude, 0.0))

  private fun shares(owner: String, vararg pins: ExploreMonument) =
      ExploreFriendMonuments(owner, null, pins.toList())
}
