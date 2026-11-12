// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.explore

import com.github.roamswent.roam.model.location.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests the markers that the Explore map draws. */
class ExploreMapMarkersTest {
  private val userLocation = Coordinates(46.51916, 6.56673)
  private val userMarker = ExploreMapMarker(USER_LOCATION_MARKER_KEY, userLocation, "Your location")

  /** Verifies that without saved monuments only the user's location marker is drawn. */
  @Test
  fun emptyMonumentsLeaveOnlyUserMarker() {
    assertEquals(listOf(userMarker), exploreMapMarkers(userLocation, "Your location", emptyList()))
  }

  /** Verifies that each monument gets its own marker, titled with its name, after the user's. */
  @Test
  fun monumentsAreAddedAfterUserMarker() {
    val eiffel = Coordinates(48.85837, 2.29448)
    val colosseum = Coordinates(41.89021, 12.49223)

    val markers =
        exploreMapMarkers(
            userLocation,
            "Your location",
            listOf(
                ExploreMonument("guide-1", "Eiffel Tower", eiffel),
                ExploreMonument("guide-2", "Colosseum", colosseum),
            ),
        )

    assertEquals(
        listOf(
            userMarker,
            ExploreMapMarker("monument_guide-1", eiffel, "Eiffel Tower"),
            ExploreMapMarker("monument_guide-2", colosseum, "Colosseum"),
        ),
        markers,
    )
  }

  /** Verifies that a guide whose id matches the user marker key cannot replace that marker. */
  @Test
  fun monumentKeysNeverCollideWithUserMarker() {
    val markers =
        exploreMapMarkers(
            userLocation,
            "Your location",
            listOf(ExploreMonument(USER_LOCATION_MARKER_KEY, "Tower", userLocation)),
        )

    assertEquals(2, markers.map { it.key }.toSet().size)
    assertEquals(userMarker, markers.first())
  }

  /** Verifies that a selected guide ID resolves to its corresponding monument. */
  @Test
  fun selectedMonumentForIdReturnsMatchingMonument() {
    val monument = ExploreMonument("guide-1", "Eiffel Tower", userLocation)

    assertEquals(monument, selectedMonumentForId(listOf(monument), "guide-1"))
  }

  /** Verifies that a missing selected guide ID produces no selected monument. */
  @Test
  fun selectedMonumentForIdReturnsNullWhenSelectedMonumentIsMissing() {
    val monument = ExploreMonument("guide-1", "Eiffel Tower", userLocation)

    assertEquals(null, selectedMonumentForId(listOf(monument), "guide-2"))
  }

  /** Verifies that clicking the user's location marker does not select a monument. */
  @Test
  fun userLocationMarkerDoesNotSelectAMonument() {
    assertEquals(null, selectedMonumentIdForMarkerKey(USER_LOCATION_MARKER_KEY))
  }

  /** Verifies that clicking a monument marker extracts its guide ID. */
  @Test
  fun monumentMarkerSelectsItsGuideId() {
    assertEquals("guide-1", selectedMonumentIdForMarkerKey("monument_guide-1"))
  }

  /** Verifies that a selected monument remains selected while it is still available. */
  @Test
  fun selectedMonumentRemainsSelectedWhenStillAvailable() {
    val monument = ExploreMonument("guide-1", "Eiffel Tower", userLocation)

    assertEquals("guide-1", updateSelectedMonumentId(listOf(monument), "guide-1"))
  }

  /** Verifies that a selected monument is cleared after it disappears from the collection. */
  @Test
  fun selectedMonumentIsClearedWhenNoLongerAvailable() {
    assertEquals(null, updateSelectedMonumentId(emptyList(), "guide-1"))
  }

  /** Verifies that clicking a monument marker selects it and reports a handled click. */
  @Test
  fun monumentMarkerClickSelectsMonument() {
    var selectedId: String? = null

    assertEquals(
        true,
        handleExploreMarkerClick("monument_guide-1") { selectedId = it },
    )
    assertEquals("guide-1", selectedId)
  }

  /** Verifies that clicking the user's marker is not handled as a monument selection. */
  @Test
  fun userLocationMarkerClickIsNotHandled() {
    var selectedId: String? = null

    assertEquals(false, handleExploreMarkerClick(USER_LOCATION_MARKER_KEY) { selectedId = it })
    assertEquals(null, selectedId)
  }
}
