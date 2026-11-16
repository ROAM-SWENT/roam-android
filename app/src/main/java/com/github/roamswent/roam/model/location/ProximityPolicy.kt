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
}
