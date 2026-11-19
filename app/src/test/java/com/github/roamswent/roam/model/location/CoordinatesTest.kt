// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.location

import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CoordinatesTest {

  private val delta = 1e-9

  @Test
  fun coarsenedRoundsPositiveCoordinatesToThreeDecimals() {
    val coarse = Coordinates(45.76543, 4.85678).coarsened()
    assertEquals(45.765, coarse.latitude, delta)
    assertEquals(4.857, coarse.longitude, delta)
  }

  @Test
  fun coarsenedRoundsNegativeCoordinatesToThreeDecimals() {
    val coarse = Coordinates(-33.86789, -151.20934).coarsened()
    assertEquals(-33.868, coarse.latitude, delta)
    assertEquals(-151.209, coarse.longitude, delta)
  }

  @Test
  fun coarsenedLeavesAlreadyCoarseCoordinatesUnchanged() {
    val coarse = Coordinates(48.858, 2.294).coarsened()
    assertEquals(48.858, coarse.latitude, delta)
    assertEquals(2.294, coarse.longitude, delta)
  }

  @Test
  fun coarsenedNeverChangesTheOriginalInstance() {
    val original = Coordinates(45.76543, 4.85678)
    original.coarsened()
    assertEquals(45.76543, original.latitude, delta)
    assertEquals(4.85678, original.longitude, delta)
  }

  @Test
  fun acceptsBoundaryValues() {
    Coordinates(90.0, 180.0)
    Coordinates(-90.0, -180.0)
  }

  @Test
  fun rejectsLatitudeOutOfRange() {
    assertThrows(IllegalArgumentException::class.java) { Coordinates(90.001, 0.0) }
  }

  @Test
  fun rejectsLongitudeOutOfRange() {
    assertThrows(IllegalArgumentException::class.java) { Coordinates(0.0, -180.5) }
  }

  @Test
  fun rejectsNaN() {
    assertThrows(IllegalArgumentException::class.java) { Coordinates(Double.NaN, 0.0) }
  }

  /** Length of one degree of arc on the model sphere, in metres. */
  private val oneDegreeMeters = Coordinates.EARTH_RADIUS_METERS * PI / 180

  @Test
  fun distanceToSamePointIsZero() {
    val point = Coordinates(46.5191, 6.5668)
    assertEquals(0.0, point.distanceTo(point), delta)
  }

  @Test
  fun distanceOfOneDegreeAlongMeridianMatchesArcLength() {
    assertEquals(oneDegreeMeters, Coordinates(10.0, 7.0).distanceTo(Coordinates(11.0, 7.0)), 1e-6)
  }

  @Test
  fun distanceOfOneDegreeAlongEquatorMatchesArcLength() {
    assertEquals(oneDegreeMeters, Coordinates(0.0, 7.0).distanceTo(Coordinates(0.0, 8.0)), 1e-6)
  }

  @Test
  fun distanceIsSymmetric() {
    val a = Coordinates(48.8584, 2.2945)
    val b = Coordinates(-33.8568, 151.2153)
    assertEquals(a.distanceTo(b), b.distanceTo(a), delta)
  }

  @Test
  fun distanceBetweenKnownCitiesIsRealistic() {
    // Lausanne to Geneva is about 51 km as the crow flies.
    val lausanne = Coordinates(46.5197, 6.6323)
    val geneva = Coordinates(46.2044, 6.1432)
    assertEquals(51_000.0, lausanne.distanceTo(geneva), 1_000.0)
  }

  @Test
  fun distanceTakesShortestPathAcrossAntimeridian() {
    val east = Coordinates(0.0, 179.5)
    val west = Coordinates(0.0, -179.5)
    assertEquals(oneDegreeMeters, east.distanceTo(west), 1e-6)
  }

  @Test
  fun distanceBetweenPointsOnSamePoleIsZero() {
    assertEquals(0.0, Coordinates(90.0, 0.0).distanceTo(Coordinates(90.0, 120.0)), 1e-6)
  }

  @Test
  fun distanceBetweenAntipodesIsHalfCircumference() {
    val distance = Coordinates(0.0, 0.0).distanceTo(Coordinates(0.0, 180.0))
    assertEquals(PI * Coordinates.EARTH_RADIUS_METERS, distance, 1e-6)
  }

  @Test
  fun distanceBetweenNearlyAntipodalPointsIsNeverNaN() {
    val distance = Coordinates(45.0, 10.0).distanceTo(Coordinates(-45.0, -170.0))
    assertEquals(PI * Coordinates.EARTH_RADIUS_METERS, distance, 1e-3)
  }
}
