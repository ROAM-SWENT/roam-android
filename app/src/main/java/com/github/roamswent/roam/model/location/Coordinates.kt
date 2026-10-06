package com.github.roamswent.roam.model.location

import kotlin.math.round

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

  private fun Double.toThreeDecimals(): Double = round(this * 1000) / 1000
}
