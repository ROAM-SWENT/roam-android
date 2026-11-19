// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.location

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/** A GPS position in decimal degrees. */
data class Coordinates(val latitude: Double, val longitude: Double) {

  init {
    require(latitude in -90.0..90.0) { "Latitude out of range: $latitude" }
    require(longitude in -180.0..180.0) { "Longitude out of range: $longitude" }
  }

  /**
   * Rounds both values to 3 decimal places (about 110 m), so the exact position is never sent to
   * the model.
   */
  fun coarsened(): Coordinates =
      Coordinates(latitude.toThreeDecimals(), longitude.toThreeDecimals())

  /**
   * Great-circle distance to [other] in metres, by the haversine formula on a spherical Earth of
   * radius [EARTH_RADIUS_METERS]. Accurate to about 0.5%, which is enough to compare against a
   * proximity threshold.
   */
  fun distanceTo(other: Coordinates): Double {
    val dLat = Math.toRadians(other.latitude - latitude)
    val dLon = Math.toRadians(other.longitude - longitude)
    val h =
        sin(dLat / 2).squared() +
            cos(Math.toRadians(latitude)) *
                cos(Math.toRadians(other.latitude)) *
                sin(dLon / 2).squared()
    // Rounding can push h slightly above 1 for antipodal points, where asin would return NaN.
    return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(h)))
  }

  private fun Double.toThreeDecimals(): Double = round(this * 1000) / 1000

  private fun Double.squared(): Double = this * this

  companion object {
    /** Mean Earth radius in metres. */
    const val EARTH_RADIUS_METERS = 6_371_000.0
  }
}
