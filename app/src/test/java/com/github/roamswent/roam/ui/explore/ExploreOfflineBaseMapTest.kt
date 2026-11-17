// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.explore

import com.github.roamswent.roam.model.basemap.BaseMapPolygon
import com.github.roamswent.roam.model.location.Coordinates
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.MapType
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests the pieces of the offline base map that do not need a rendered Google Map. */
class ExploreOfflineBaseMapTest {

  @Test
  fun googleTilesAreOnlyShownOnline() {
    assertEquals(MapType.NORMAL, exploreMapProperties(isOnline = true).mapType)
    assertEquals(MapType.NONE, exploreMapProperties(isOnline = false).mapType)
  }

  @Test
  fun seaBandsCoverEveryLongitudeWithoutGapsOrOverlaps() {
    val bands = offlineSeaBands()
    val ranges = bands.map { band -> band.minOf { it.longitude } to band.maxOf { it.longitude } }

    assertEquals(-180.0, ranges.first().first, 0.0)
    assertEquals(180.0, ranges.last().second, 0.0)
    ranges.zipWithNext().forEach { (west, east) -> assertEquals(west.second, east.first, 0.0) }
  }

  @Test
  fun eachSeaBandIsNarrowerThanHalfTheGlobe() {
    // Google Maps joins two points the short way round, so a band 180 degrees or wider would flip.
    offlineSeaBands().forEach { band ->
      assertTrue(band.maxOf { it.longitude } - band.minOf { it.longitude } < 180.0)
    }
  }

  @Test
  fun seaShapesMatchTheSeaBandsInGoogleMapsPoints() {
    val shapes = offlineSeaShapes()

    assertEquals(offlineSeaBands().size, shapes.size)
    assertEquals(LatLng(-85.0, -180.0), shapes.first().first())
  }

  @Test
  fun landShapesKeepOutlinesAndHolesInLatLngOrder() {
    val outline = listOf(Coordinates(0.0, 10.0), Coordinates(0.0, 20.0), Coordinates(5.0, 20.0))
    val hole = listOf(Coordinates(1.0, 12.0), Coordinates(1.0, 13.0), Coordinates(2.0, 13.0))

    val shapes =
        offlineLandShapes(listOf(BaseMapPolygon(outline, listOf(hole)), BaseMapPolygon(outline)))

    assertEquals(2, shapes.size)
    assertEquals(LatLng(0.0, 20.0), shapes[0].outline[1])
    assertEquals(LatLng(1.0, 13.0), shapes[0].holes[0][1])
    assertTrue(shapes[1].holes.isEmpty())
  }

  @Test
  fun noCountriesGiveNoLandShapes() {
    assertTrue(offlineLandShapes(emptyList()).isEmpty())
  }

  @Test
  fun seaBandsStopAtTheMercatorLimit() {
    offlineSeaBands().flatten().forEach { assertEquals(85.0, abs(it.latitude), 0.0) }
  }
}
