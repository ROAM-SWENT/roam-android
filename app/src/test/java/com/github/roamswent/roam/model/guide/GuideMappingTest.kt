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
  fun copiesGuideContentPhotoLocationAndDate() {
    val photo = byteArrayOf(1, 2, 3)

    val saved = monumentGuide.toSavedGuide(photo, discoveryDate = 1_700_000_000_000L)

    assertEquals(
        Guide(
            id = "",
            photoBytes = byteArrayOf(1, 2, 3),
            monumentName = "Eiffel Tower",
            confidence = 0.92,
            history = "Built in 1889.",
            facts = listOf("It grows taller in summer."),
            culture = "A symbol of Paris.",
            location = Coordinates(48.85837, 2.29448),
            discoveryDate = 1_700_000_000_000L,
            isFavorite = false,
        ),
        saved,
    )
  }

  @Test
  fun keepsMissingLocationAsNull() {
    val saved = monumentGuide.copy(location = null).toSavedGuide(byteArrayOf(1), discoveryDate = 0L)

    assertNull(saved.location)
  }

  @Test
  fun savedGuideMapsBackToTheContentShownOnTheDetailScreen() {
    val saved = monumentGuide.toSavedGuide(byteArrayOf(1, 2, 3), discoveryDate = 0L)

    assertEquals(monumentGuide.copy(language = ""), saved.copy(id = "guide-1").toMonumentGuide())
  }
}
