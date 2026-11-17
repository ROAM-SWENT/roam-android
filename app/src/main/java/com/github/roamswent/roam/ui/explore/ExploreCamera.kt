// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.explore

import com.github.roamswent.roam.model.location.Coordinates
import kotlin.math.floor
import kotlin.math.log2

/** What the Explore camera is framing; the camera only moves when this changes. */
internal enum class ExploreCameraFocus {
  /** The user's current location. */
  USER,
  /** The saved monuments, used when the user's location is not known. */
  MONUMENTS,
  /** The whole world, used when there is neither a location nor a saved monument. */
  WORLD,
}

/** Where the Explore camera looks and how close, in Google Maps zoom levels. */
internal data class ExploreCameraTarget(val center: Coordinates, val zoom: Float)

/** Zoom used around a single point: the user's location or a lone monument. */
internal const val EXPLORE_POINT_ZOOM = 14f

/** Zoom that shows the whole world on a phone screen. */
internal const val EXPLORE_WORLD_ZOOM = 1f

/**
 * Closest and widest zoom used when framing several monuments. The widest is the world view: a
 * higher minimum would crop monuments more than about 110 degrees apart on a phone screen.
 */
internal const val EXPLORE_MIN_FRAMING_ZOOM = EXPLORE_WORLD_ZOOM
internal const val EXPLORE_MAX_FRAMING_ZOOM = EXPLORE_POINT_ZOOM

/** Center of the world view, slightly north so most land is on screen. */
internal val EXPLORE_WORLD_CENTER = Coordinates(20.0, 0.0)

/**
 * Degrees that fit on a phone screen at zoom 0, with margins around the monuments. Each zoom level
 * halves it. Approximate: it ignores the Mercator stretch, which only matters near the poles.
 */
private const val FRAMING_DEGREES_AT_ZOOM_0 = 300.0

/**
 * Picks what to frame: the user's location when known, otherwise the saved monuments, otherwise the
 * world. The map works without location permission or a location fix.
 */
internal fun exploreCameraFocus(
    userLocation: Coordinates?,
    monuments: List<ExploreMonument>,
): ExploreCameraFocus =
    when {
      userLocation != null -> ExploreCameraFocus.USER
      monuments.isNotEmpty() -> ExploreCameraFocus.MONUMENTS
      else -> ExploreCameraFocus.WORLD
    }

/** Computes the camera position for the focus that [exploreCameraFocus] picks. */
internal fun exploreCameraTarget(
    userLocation: Coordinates?,
    monuments: List<ExploreMonument>,
): ExploreCameraTarget =
    when (exploreCameraFocus(userLocation, monuments)) {
      ExploreCameraFocus.USER -> ExploreCameraTarget(checkNotNull(userLocation), EXPLORE_POINT_ZOOM)
      ExploreCameraFocus.MONUMENTS -> framing(monuments.map { it.coordinates })
      ExploreCameraFocus.WORLD -> ExploreCameraTarget(EXPLORE_WORLD_CENTER, EXPLORE_WORLD_ZOOM)
    }

/**
 * Centers on the bounding box of [points] and zooms so the box fits. Monuments on both sides of the
 * antimeridian are framed the long way round, which is acceptable for a starting view.
 */
private fun framing(points: List<Coordinates>): ExploreCameraTarget {
  val minLatitude = points.minOf { it.latitude }
  val maxLatitude = points.maxOf { it.latitude }
  val minLongitude = points.minOf { it.longitude }
  val maxLongitude = points.maxOf { it.longitude }
  val center = Coordinates((minLatitude + maxLatitude) / 2, (minLongitude + maxLongitude) / 2)
  val span = maxOf(maxLatitude - minLatitude, maxLongitude - minLongitude)
  if (span == 0.0) return ExploreCameraTarget(center, EXPLORE_POINT_ZOOM)
  val zoom =
      floor(log2(FRAMING_DEGREES_AT_ZOOM_0 / span))
          .toFloat()
          .coerceIn(EXPLORE_MIN_FRAMING_ZOOM, EXPLORE_MAX_FRAMING_ZOOM)
  return ExploreCameraTarget(center, zoom)
}
