// Co-authored-by: Codex <codex@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.explore

import com.github.roamswent.roam.model.location.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests where the Explore camera starts, with and without a known location. */
class ExploreCameraTest {
  private val user = Coordinates(46.51916, 6.56673)
  private val eiffel = ExploreMonument("guide-1", "Eiffel Tower", Coordinates(48.85837, 2.29448))
  private val colosseum = ExploreMonument("guide-2", "Colosseum", Coordinates(41.89021, 12.49223))

  @Test
  fun knownLocationIsFramedFirstEvenWithMonuments() {
    assertEquals(ExploreCameraFocus.USER, exploreCameraFocus(user, listOf(eiffel)))
    assertEquals(
        ExploreCameraTarget(user, EXPLORE_POINT_ZOOM),
        exploreCameraTarget(user, listOf(eiffel)),
    )
  }

  @Test
  fun landmarkFromGuideIsFramedEvenWhenUserLocationArrives() {
    val landmark = eiffel.coordinates
    assertEquals(ExploreCameraFocus.LANDMARK, exploreCameraFocus(null, emptyList(), landmark))
    val expected = ExploreCameraTarget(landmark, EXPLORE_POINT_ZOOM)
    assertEquals(expected, exploreCameraTarget(null, emptyList(), landmark))
    assertEquals(expected, exploreCameraTarget(user, listOf(colosseum), landmark))
  }

  @Test
  fun withoutLocationTheMonumentsAreFramed() {
    assertEquals(ExploreCameraFocus.MONUMENTS, exploreCameraFocus(null, listOf(eiffel)))
  }

  @Test
  fun withoutLocationOrMonumentsTheWorldIsShown() {
    assertEquals(ExploreCameraFocus.WORLD, exploreCameraFocus(null, emptyList()))
    assertEquals(
        ExploreCameraTarget(EXPLORE_WORLD_CENTER, EXPLORE_WORLD_ZOOM),
        exploreCameraTarget(null, emptyList()),
    )
  }

  @Test
  fun aSingleMonumentIsShownUpClose() {
    assertEquals(
        ExploreCameraTarget(eiffel.coordinates, EXPLORE_POINT_ZOOM),
        exploreCameraTarget(null, listOf(eiffel)),
    )
  }

  @Test
  fun severalMonumentsAreCenteredOnTheirBoundingBox() {
    val target = exploreCameraTarget(null, listOf(eiffel, colosseum))

    assertEquals((48.85837 + 41.89021) / 2, target.center.latitude, 1e-9)
    assertEquals((2.29448 + 12.49223) / 2, target.center.longitude, 1e-9)
    // Paris to Rome spans about ten degrees, which fits on screen at zoom 4.
    assertEquals(4f, target.zoom)
  }

  @Test
  fun framingZoomGetsWiderAsMonumentsSpreadOut() {
    val near = ExploreMonument("guide-3", "Near", Coordinates(48.86, 2.30))
    val sydney = ExploreMonument("guide-4", "Opera", Coordinates(-33.8568, 151.2153))

    val close = exploreCameraTarget(null, listOf(eiffel, near)).zoom
    val europe = exploreCameraTarget(null, listOf(eiffel, colosseum)).zoom
    val world = exploreCameraTarget(null, listOf(eiffel, sydney)).zoom

    assertTrue(close > europe)
    assertTrue(europe > world)
  }

  /** Paris and Sydney are about 150 degrees apart: the zoom must be wide enough to show both. */
  @Test
  fun monumentsMoreThanAScreenApartAreStillBothFramed() {
    val sydney = ExploreMonument("guide-4", "Opera", Coordinates(-33.8568, 151.2153))

    assertEquals(EXPLORE_WORLD_ZOOM, exploreCameraTarget(null, listOf(eiffel, sydney)).zoom)
  }

  @Test
  fun framingZoomStaysWithinBounds() {
    val nextDoor = ExploreMonument("guide-3", "Next door", Coordinates(48.858371, 2.294481))
    val antipode = ExploreMonument("guide-4", "Far", Coordinates(-89.0, -179.0))
    val farEast = ExploreMonument("guide-5", "Far east", Coordinates(89.0, 179.0))

    assertEquals(EXPLORE_MAX_FRAMING_ZOOM, exploreCameraTarget(null, listOf(eiffel, nextDoor)).zoom)
    assertEquals(
        EXPLORE_MIN_FRAMING_ZOOM,
        exploreCameraTarget(null, listOf(antipode, farEast)).zoom,
    )
  }
}
