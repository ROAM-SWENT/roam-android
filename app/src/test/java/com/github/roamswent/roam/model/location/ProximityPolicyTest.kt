// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityPolicyTest {

  private val monument = Coordinates(48.8584, 2.2945)

  /** Moves [meters] due north of [from], which changes the latitude only. */
  private fun northOf(from: Coordinates, meters: Double): Coordinates =
      Coordinates(
          from.latitude + Math.toDegrees(meters / Coordinates.EARTH_RADIUS_METERS),
          from.longitude,
      )

  @Test
  fun thresholdIsOneKilometer() {
    assertEquals(1_000.0, ProximityPolicy.MAX_DISTANCE_METERS, 0.0)
  }

  @Test
  fun samePositionIsNear() {
    assertTrue(ProximityPolicy.isNear(monument, monument))
  }

  @Test
  fun positionJustInsideThresholdIsNear() {
    assertTrue(ProximityPolicy.isNear(northOf(monument, 990.0), monument))
  }

  @Test
  fun positionJustOutsideThresholdIsNotNear() {
    assertFalse(ProximityPolicy.isNear(northOf(monument, 1_010.0), monument))
  }

  @Test
  fun distantCityIsNotNear() {
    assertFalse(ProximityPolicy.isNear(Coordinates(46.5197, 6.6323), monument))
  }

  @Test
  fun isNearIsSymmetric() {
    val observed = northOf(monument, 500.0)
    assertEquals(
        ProximityPolicy.isNear(observed, monument),
        ProximityPolicy.isNear(monument, observed),
    )
  }
}
