package com.github.roamswent.roam.ui.monumentDetail

import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.guide.Guide
import org.junit.Assert.assertEquals
import org.junit.Test

class MonumentDetailContentTest {
  private val expected =
      MonumentDetailContent(
          monumentName = "Eiffel Tower",
          confidence = 0.92,
          history = "Built in 1889.",
          facts = listOf("330 meters tall"),
          culture = "A symbol of Paris.",
      )

  @Test
  fun aGeneratedGuideKeepsTheTextTheScreenShows() {
    val guide =
        MonumentGuide(
            monumentName = "Eiffel Tower",
            confidence = 0.92,
            history = "Built in 1889.",
            facts = listOf("330 meters tall"),
            culture = "A symbol of Paris.",
            language = "fr",
        )

    assertEquals(expected, guide.toDetailContent())
  }

  @Test
  fun aSavedGuideKeepsTheTextTheScreenShows() {
    val guide =
        Guide(
            id = "eiffel",
            photoBytes = byteArrayOf(1, 2, 3),
            monumentName = "Eiffel Tower",
            confidence = 0.92,
            history = "Built in 1889.",
            facts = listOf("330 meters tall"),
            culture = "A symbol of Paris.",
            discoveryDate = 1_798_000_000_000,
            isFavorite = true,
        )

    assertEquals(expected, guide.toDetailContent())
  }
}
