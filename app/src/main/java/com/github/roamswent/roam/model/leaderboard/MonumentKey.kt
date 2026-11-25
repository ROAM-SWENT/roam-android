// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.leaderboard

import com.github.roamswent.roam.model.location.Coordinates
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Stable identity of a monument, used to count each verified monument only once on the leaderboard.
 *
 * The key is the cell of [Coordinates.coarsened] (3 decimal places, about 110 m) that contains the
 * monument's own position as given by the AI
 * ([com.github.roamswent.roam.model.ai.MonumentGuide.monumentLocation]). It never uses the saved
 * guide ID, which changes on every scan, the observed scan position, which depends on where the
 * user stood, or the monument name, which is written in the user's language.
 *
 * Two answers for the same monument can differ by a few metres yet fall on either side of a cell
 * boundary, so two keys are the same monument when their cells touch, see [isSameMonumentAs].
 *
 * This is an approximate deduplication with known errors in both directions, accepted deliberately:
 * - Under-counting: distinct monuments whose positions are in touching cells, at most about 220 m
 *   apart in latitude, count as one.
 * - Over-counting: touching is not transitive, so the result depends on scan order. For three cells
 *   in a row A, B, C, scanning B first counts once, as A and C then touch B; scanning A and C first
 *   counts twice, as they do not touch each other. If the AI places one monument in cells more than
 *   one cell apart, about 110 m or more, it can be counted twice.
 *
 * The same order dependence decides which of several close distinct monuments get counted.
 *
 * Cells are measured in thousandths of a degree. Longitude wraps at the antimeridian, where 180 is
 * stored as -180. The narrowing of longitude cells towards the poles is not corrected for.
 *
 * @property latitudeE3 Latitude of the cell in thousandths of a degree, from -90 000 to 90 000.
 * @property longitudeE3 Longitude of the cell in thousandths of a degree, from -180 000 inclusive
 *   to 180 000 exclusive.
 */
data class MonumentKey(val latitudeE3: Int, val longitudeE3: Int) {

  init {
    require(latitudeE3 in -MAX_LATITUDE_E3..MAX_LATITUDE_E3) {
      "Latitude cell out of range: $latitudeE3"
    }
    require(longitudeE3 in -HALF_TURN_E3 until HALF_TURN_E3) {
      "Longitude cell out of range: $longitudeE3"
    }
  }

  /** Text form of the key, safe to use as a Firestore document ID, such as `48858_2294`. */
  val id: String
    get() = "${latitudeE3}_$longitudeE3"

  /**
   * Whether [other] identifies the same monument: its cell is this cell or one of the 8 cells
   * around it. The relation is symmetric but not transitive: two keys that each touch a third need
   * not touch each other, so counting can depend on scan order (see the class documentation).
   */
  fun isSameMonumentAs(other: MonumentKey): Boolean =
      abs(latitudeE3 - other.latitudeE3) <= 1 && longitudeDistanceE3(other) <= 1

  /**
   * Every key that [isSameMonumentAs] this one: this key and the cells around it, 9 in all, or 6 at
   * a pole. A store of counted keys can look these up to tell whether this monument was counted.
   */
  fun neighbourhood(): Set<MonumentKey> = buildSet {
    for (dLat in -1..1) {
      val latitude = latitudeE3 + dLat
      if (latitude !in -MAX_LATITUDE_E3..MAX_LATITUDE_E3) continue
      for (dLon in -1..1) add(MonumentKey(latitude, wrapLongitude(longitudeE3 + dLon)))
    }
  }

  private fun longitudeDistanceE3(other: MonumentKey): Int {
    val difference = abs(longitudeE3 - other.longitudeE3)
    return min(difference, FULL_TURN_E3 - difference)
  }

  companion object {
    private const val MAX_LATITUDE_E3 = 90_000
    private const val HALF_TURN_E3 = 180_000
    private const val FULL_TURN_E3 = 360_000

    /**
     * Key of the monument at [monumentLocation], which must be the monument's own position from the
     * AI, never the observed scan position. Uses the same rounding as [Coordinates.coarsened].
     */
    fun of(monumentLocation: Coordinates): MonumentKey {
      val cell = monumentLocation.coarsened()
      return MonumentKey(
          latitudeE3 = (cell.latitude * 1000).roundToInt(),
          longitudeE3 = wrapLongitude((cell.longitude * 1000).roundToInt()),
      )
    }

    private fun wrapLongitude(longitudeE3: Int): Int =
        Math.floorMod(longitudeE3 + HALF_TURN_E3, FULL_TURN_E3) - HALF_TURN_E3
  }
}
