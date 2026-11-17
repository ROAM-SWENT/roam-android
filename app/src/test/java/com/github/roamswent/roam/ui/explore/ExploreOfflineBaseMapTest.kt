// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.explore

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
  fun seaBandsStopAtTheMercatorLimit() {
    offlineSeaBands().flatten().forEach { assertEquals(85.0, abs(it.latitude), 0.0) }
  }
}
