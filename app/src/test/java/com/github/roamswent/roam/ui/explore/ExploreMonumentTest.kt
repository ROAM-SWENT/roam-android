// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.explore

import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.location.Coordinates
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ExploreMonumentTest {
  private val coordinates = Coordinates(48.85837, 2.29448)

  /**
   * Verifies that a located guide supplies the title, description, photo, and location card data.
   */
  @Test
  fun locatedGuideMapsToMonumentWithCardContent() {
    val photo = byteArrayOf(1, 2, 3)
    val guide =
        Guide(
            id = "guide-1",
            photoBytes = photo,
            monumentName = "Eiffel Tower",
            confidence = 0.9,
            history = "Built for the 1889 exposition.",
            facts = listOf("Fact."),
            culture = "Culture.",
            location = coordinates,
        )

    val monument = listOf(guide).toExploreMonuments().single()

    assertEquals("guide-1", monument.guideId)
    assertEquals("Eiffel Tower", monument.name)
    assertEquals("Built for the 1889 exposition.", monument.description)
    assertArrayEquals(photo, monument.photoBytes)
    assertEquals(coordinates, monument.coordinates)
  }

  /** Verifies that card-content changes do not change the map marker identity. */
  @Test
  fun markerEqualityIgnoresCardContentChanges() {
    val first =
        ExploreMonument(
            guideId = "guide-1",
            name = "Eiffel Tower",
            coordinates = coordinates,
            description = "Original description",
            photoBytes = byteArrayOf(1),
        )
    val updated = first.copy(description = "Updated description", photoBytes = byteArrayOf(2))

    assertEquals(first, updated)
    assertEquals(first.hashCode(), updated.hashCode())
  }

  /** Verifies that two guides with the same name and location remain separate markers. */
  @Test
  fun markersWithDifferentGuideIdsAreDifferentEvenAtSameLocation() {
    val first = ExploreMonument("guide-1", "Paris", coordinates)
    val second = ExploreMonument("guide-2", "Paris", coordinates)

    assertNotEquals(first, second)
  }
}
