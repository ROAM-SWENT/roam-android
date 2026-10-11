package com.github.roamswent.roam.model.location

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
}
