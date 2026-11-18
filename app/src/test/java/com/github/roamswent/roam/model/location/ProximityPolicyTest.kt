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
  fun checkIsNearWithinThreshold() {
    assertEquals(
        ProximityResult.Near,
        ProximityPolicy.check(northOf(monument, 990.0), monument),
    )
  }

  @Test
  fun checkIsTooFarBeyondThresholdAndKeepsTheDistance() {
    val result = ProximityPolicy.check(northOf(monument, 1_500.0), monument)
    assertTrue(result is ProximityResult.TooFar)
    assertEquals(1_500.0, (result as ProximityResult.TooFar).distanceMeters, 1e-6)
  }

  @Test
  fun checkReportsMissingObservedLocation() {
    assertEquals(ProximityResult.ObservedLocationMissing, ProximityPolicy.check(null, monument))
  }

  @Test
  fun checkReportsMissingMonumentLocation() {
    assertEquals(ProximityResult.MonumentLocationMissing, ProximityPolicy.check(monument, null))
  }

  @Test
  fun checkReportsMissingObservedLocationFirstWhenBothAreMissing() {
    assertEquals(ProximityResult.ObservedLocationMissing, ProximityPolicy.check(null, null))
  }

  @Test
  fun checkAgreesWithIsNear() {
    listOf(0.0, 500.0, 999.0, 1_001.0, 5_000.0).forEach { meters ->
      val observed = northOf(monument, meters)
      assertEquals(
          "at $meters m",
          ProximityPolicy.isNear(observed, monument),
          ProximityPolicy.check(observed, monument) == ProximityResult.Near,
      )
    }
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
