// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.location

/**
 * Decides whether an observed position (where the user or the photo was) is close enough to a
 * monument for the discovery to be put on Explore.
 */
object ProximityPolicy {

  /** Largest distance, in metres, at which an observed position still counts as near. */
  const val MAX_DISTANCE_METERS = 1_000.0

  /** Whether [observed] is within [MAX_DISTANCE_METERS] of [monument], boundary included. */
  fun isNear(observed: Coordinates, monument: Coordinates): Boolean =
      observed.distanceTo(monument) <= MAX_DISTANCE_METERS

  /**
   * Checks [observed] against [monument], either of which may be unknown. A missing observed
   * position is reported first, since the user can act on it, then a missing monument position,
   * then the distance.
   */
  fun check(observed: Coordinates?, monument: Coordinates?): ProximityResult {
    if (observed == null) return ProximityResult.ObservedLocationMissing
    if (monument == null) return ProximityResult.MonumentLocationMissing
    val distance = observed.distanceTo(monument)
    return if (distance <= MAX_DISTANCE_METERS) ProximityResult.Near
    else ProximityResult.TooFar(distance)
  }
}

/** Outcome of [ProximityPolicy.check]. */
sealed interface ProximityResult {
  /** The observed position is within [ProximityPolicy.MAX_DISTANCE_METERS] of the monument. */
  data object Near : ProximityResult

  /** The observed position is [distanceMeters] from the monument, beyond the threshold. */
  data class TooFar(val distanceMeters: Double) : ProximityResult

  /** No observed position is known, so nothing can be checked. */
  data object ObservedLocationMissing : ProximityResult

  /** No position is known for the monument, so nothing can be checked. */
  data object MonumentLocationMissing : ProximityResult
}
