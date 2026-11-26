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
  fun changingFriendOrderKeepsTheSameClustersAndAnchors() {
    val alice = shares("alice", pin("a", "Clock tower", 0.0))
    val bob = shares("bob", pin("b", "Clock tower", 0.00035))
    val cora = shares("cora", pin("c", "Clock tower", 0.00070))
    val orderings =
        listOf(
            listOf(alice, bob, cora),
            listOf(alice, cora, bob),
            listOf(bob, alice, cora),
            listOf(bob, cora, alice),
            listOf(cora, alice, bob),
            listOf(cora, bob, alice),
        )

    for (friends in orderings) {
      val groups = friends.groupFriendPins()
      assertEquals(
          listOf(listOf("a", "b"), listOf("c")),
          groups.map { group -> group.pins.map { it.monument.guideId } },
      )
      assertEquals(listOf("a", "c"), groups.map { it.first.monument.guideId })
    }
  }

  @Test
  fun canonicallyEquivalentNamesClusterButPunctuationStillDistinguishesNames() {
    val groups =
        listOf(
                shares("alice", pin("a", "Caf\u00e9 de Paris", 0.0)),
                shares("bob", pin("b", " CAFE\u0301 DE PARIS ", 0.0)),
                shares("cora", pin("c", "Caf\u00e9-de-Paris", 0.0)),
            )
            .groupFriendPins()

    assertEquals(
        listOf(listOf("a", "b"), listOf("c")),
        groups.map { group -> group.pins.map { it.monument.guideId } },
    )
  }

  @Test
  fun repeatSharesKeepEveryGuideAndCountEachFriendOnceForOverflow() {
    val group =
        listOf(
                shares("dora", pin("d", "Tower", 0.0)),
                shares("cora", pin("c", "Tower", 0.0)),
                shares("bob", pin("b", "Tower", 0.0)),
                shares("alice", pin("a2", "Tower", 0.0), pin("a", "Tower", 0.0)),
            )
            .groupFriendPins()
            .single()

    assertEquals(listOf("alice", "bob", "cora"), group.visibleOwnerUids)
    assertEquals("a", group.first.monument.guideId)
    assertEquals(1, group.additionalOwnerCount)
    assertEquals(setOf("a", "a2", "b", "c", "d"), group.pins.map { it.monument.guideId }.toSet())
  }

  private fun pin(guideId: String, name: String, latitude: Double) =
      ExploreMonument(guideId, name, Coordinates(latitude, 0.0))

  private fun shares(owner: String, vararg pins: ExploreMonument) =
      ExploreFriendMonuments(owner, null, pins.toList())
}
