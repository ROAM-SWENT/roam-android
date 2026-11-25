// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.leaderboard

import com.github.roamswent.roam.model.location.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MonumentKeyTest {

  private val eiffelTower = Coordinates(48.85837, 2.294481)
  private val arcDeTriomphe = Coordinates(48.873792, 2.295028)

  @Test
  fun ofUsesTheCoarsenedCell() {
    val key = MonumentKey.of(eiffelTower)
    val coarse = eiffelTower.coarsened()

    assertEquals(MonumentKey(48858, 2294), key)
    assertEquals(coarse.latitude, key.latitudeE3 / 1000.0, 1e-9)
    assertEquals(coarse.longitude, key.longitudeE3 / 1000.0, 1e-9)
  }

  @Test
  fun idIsStableTextForm() {
    assertEquals("48858_2294", MonumentKey.of(eiffelTower).id)
    assertEquals("-33857_-151215", MonumentKey.of(Coordinates(-33.85678, -151.21530)).id)
  }

  @Test
  fun repeatedAnswersInTheSameCellGiveEqualKeys() {
    assertEquals(
        MonumentKey.of(Coordinates(48.8582, 2.2941)),
        MonumentKey.of(Coordinates(48.85837, 2.294481)),
    )
  }

  @Test
  fun answersStraddlingACellBoundaryAreTheSameMonument() {
    val below = MonumentKey.of(Coordinates(48.8584999, 2.2944999))
    val above = MonumentKey.of(Coordinates(48.8585001, 2.2945001))

    assertNotEquals(below, above)
    assertTrue(below.isSameMonumentAs(above))
    assertTrue(above.isSameMonumentAs(below))
  }

  @Test
  fun aKeyIsTheSameMonumentAsItself() {
    val key = MonumentKey.of(eiffelTower)
    assertTrue(key.isSameMonumentAs(key))
  }

  @Test
  fun cellsTwoApartAreDifferentMonuments() {
    val key = MonumentKey(48858, 2294)

    assertFalse(key.isSameMonumentAs(MonumentKey(48860, 2294)))
    assertFalse(key.isSameMonumentAs(MonumentKey(48858, 2296)))
    assertFalse(key.isSameMonumentAs(MonumentKey(48856, 2292)))
  }

  @Test
  fun distinctMonumentsInTheSameCityAreDifferent() {
    assertFalse(MonumentKey.of(eiffelTower).isSameMonumentAs(MonumentKey.of(arcDeTriomphe)))
  }

  @Test
  fun neighbourhoodHasNineKeysThatAreAllTheSameMonument() {
    val key = MonumentKey(48858, 2294)
    val neighbourhood = key.neighbourhood()

    assertEquals(9, neighbourhood.size)
    assertTrue(key in neighbourhood)
    assertTrue(neighbourhood.all { key.isSameMonumentAs(it) })
  }

  @Test
  fun neighbourhoodExcludesCellsTwoApart() {
    val neighbourhood = MonumentKey(48858, 2294).neighbourhood()

    assertFalse(MonumentKey(48860, 2294) in neighbourhood)
    assertFalse(MonumentKey(48858, 2296) in neighbourhood)
  }

  @Test
  fun antimeridianIsOneLongitude() {
    assertEquals(MonumentKey.of(Coordinates(0.0, 180.0)), MonumentKey.of(Coordinates(0.0, -180.0)))
    assertEquals(-180_000, MonumentKey.of(Coordinates(0.0, 180.0)).longitudeE3)
  }

  @Test
  fun cellsAcrossTheAntimeridianAreTheSameMonument() {
    val east = MonumentKey.of(Coordinates(10.0, 179.9994))
    val west = MonumentKey.of(Coordinates(10.0, -179.9996))

    assertEquals(179_999, east.longitudeE3)
    assertEquals(-180_000, west.longitudeE3)
    assertTrue(east.isSameMonumentAs(west))
    assertTrue(west in east.neighbourhood())
    assertTrue(east in west.neighbourhood())
  }

  @Test
  fun neighbourhoodStopsAtThePoles() {
    val northPole = MonumentKey.of(Coordinates(90.0, 0.0)).neighbourhood()
    val southPole = MonumentKey.of(Coordinates(-90.0, 0.0)).neighbourhood()

    assertEquals(6, northPole.size)
    assertEquals(6, southPole.size)
    assertTrue(northPole.all { it.latitudeE3 <= 90_000 })
    assertTrue(southPole.all { it.latitudeE3 >= -90_000 })
  }

  @Test
  fun constructorRejectsCellsOutOfRange() {
    assertThrows(IllegalArgumentException::class.java) { MonumentKey(90_001, 0) }
    assertThrows(IllegalArgumentException::class.java) { MonumentKey(-90_001, 0) }
    assertThrows(IllegalArgumentException::class.java) { MonumentKey(0, 180_000) }
    assertThrows(IllegalArgumentException::class.java) { MonumentKey(0, -180_001) }
  }
}
