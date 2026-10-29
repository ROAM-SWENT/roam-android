// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.guide

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceholderSavedGuideRepositoryTest {
  private val guide =
      Guide(
          id = "",
          photoBytes = ByteArray(0),
          monumentName = "Pantheon",
          confidence = 0.9,
          history = "History",
          facts = emptyList(),
          culture = "Culture",
      )

  @Test
  fun defaultSeedHasDatedPlaceholderGuidesWithUniqueIds() = runTest {
    val guides = PlaceholderSavedGuideRepository().getSavedGuides()

    assertEquals(6, guides.size)
    assertEquals(guides.size, guides.map { it.id }.toSet().size)
    assertTrue(guides.all { it.photoBytes.isEmpty() && it.discoveryDate != null })
  }

  @Test
  fun addGuideAssignsAnIdAndIsObserved() = runTest {
    val repository = PlaceholderSavedGuideRepository(emptyList())

    val saved = repository.addGuide(guide)

    assertTrue(saved.id.isNotBlank())
    assertEquals(listOf(saved), repository.observeSavedGuides().first())
    assertEquals(saved, repository.getGuide(saved.id))
  }

  @Test
  fun addGuideWithAnExistingIdReplacesIt() = runTest {
    val repository = PlaceholderSavedGuideRepository(listOf(guide.copy(id = "guide-1")))

    repository.addGuide(guide.copy(id = "guide-1", monumentName = "Renamed"))

    assertEquals(listOf("Renamed"), repository.getSavedGuides().map { it.monumentName })
  }

  @Test
  fun removeGuideIsObservedAndMissingGuidesAreNull() = runTest {
    val repository = PlaceholderSavedGuideRepository(listOf(guide.copy(id = "guide-1")))

    repository.removeGuide("guide-1")

    assertTrue(repository.observeSavedGuides().first().isEmpty())
    assertNull(repository.getGuide("guide-1"))
  }
}
