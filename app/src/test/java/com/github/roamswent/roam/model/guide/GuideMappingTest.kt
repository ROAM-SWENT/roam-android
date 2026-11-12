// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.guide

import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.location.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideMappingTest {

  private val monumentGuide =
      MonumentGuide(
          monumentName = "Eiffel Tower",
          confidence = 0.92,
          history = "Built in 1889.",
          facts = listOf("It grows taller in summer."),
          culture = "A symbol of Paris.",
          language = "fr",
          location = Coordinates(48.85837, 2.29448),
      )

  @Test
  fun copiesGuideContentIdPhotoAndDate() {
    val photo = byteArrayOf(1, 2, 3)

    val saved =
        monumentGuide.toSavedGuide(
            id = "guide-id",
            photoBytes = photo,
            discoveryDate = 1_700_000_000_000L,
            location = null,
        )

    assertEquals(
        Guide(
            id = "guide-id",
            photoBytes = byteArrayOf(1, 2, 3),
            monumentName = "Eiffel Tower",
            confidence = 0.92,
            history = "Built in 1889.",
            facts = listOf("It grows taller in summer."),
            culture = "A symbol of Paris.",
            location = null,
            discoveryDate = 1_700_000_000_000L,
            isFavorite = false,
        ),
        saved,
    )
  }

  @Test
  fun scanLocationIsNotCopiedImplicitly() {
    val saved = monumentGuide.toSavedGuide("guide-id", byteArrayOf(1), 0L, location = null)

    assertNull(saved.location)
    // The discovery keeps its scan location for a later Explore opt-in.
    assertEquals(Coordinates(48.85837, 2.29448), monumentGuide.location)
  }

  @Test
  fun usesTheLocationTheCallerChooses() {
    val location = Coordinates(48.85837, 2.29448)

    val saved = monumentGuide.toSavedGuide("guide-id", byteArrayOf(1), 0L, location = location)

    assertEquals(location, saved.location)
  }

  @Test
  fun savedGuideMapsBackToTheContentShownOnTheDetailScreen() {
    val saved =
        monumentGuide.toSavedGuide(
            id = "guide-1",
            photoBytes = byteArrayOf(1, 2, 3),
            discoveryDate = 0L,
            location = monumentGuide.location,
        )

    assertEquals(monumentGuide.copy(language = ""), saved.toMonumentGuide())
  }
}
