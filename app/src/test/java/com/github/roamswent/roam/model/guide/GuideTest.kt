// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.guide

import com.github.roamswent.roam.model.location.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class GuideTest {
  private val guide =
      Guide(
          id = "guide-id",
          photoBytes = byteArrayOf(1, 2, 3),
          monumentName = "Eiffel Tower",
          confidence = 0.92,
          history = "Built in 1889.",
          facts = listOf("330 meters tall"),
          culture = "A symbol of Paris.",
          location = Coordinates(48.8584, 2.2945),
          discoveryDate = 1_798_000_000_000,
      )

  @Test
  fun equalityAndHashCodeUsePhotoContentsAndAllGuideFields() {
    val equalGuide = guide.copy(photoBytes = byteArrayOf(1, 2, 3))

    assertNotSame(guide.photoBytes, equalGuide.photoBytes)
    assertEquals(guide, equalGuide)
    assertEquals(guide.hashCode(), equalGuide.hashCode())
  }

  @Test
  fun equalityRejectsNullAndDifferencesInEveryField() {
    assertNotEquals(guide, null)
    assertNotEquals(guide, guide.copy(id = "another-id"))
    assertNotEquals(guide, guide.copy(photoBytes = byteArrayOf(1, 2, 4)))
    assertNotEquals(guide, guide.copy(monumentName = "Colosseum"))
    assertNotEquals(guide, guide.copy(confidence = 0.91))
    assertNotEquals(guide, guide.copy(history = "Different history"))
    assertNotEquals(guide, guide.copy(facts = listOf("Different fact")))
    assertNotEquals(guide, guide.copy(culture = "Different culture"))
    assertNotEquals(guide, guide.copy(location = null))
    assertNotEquals(guide, guide.copy(discoveryDate = null))
    assertNotEquals(guide, guide.copy(isFavorite = true))
  }

  @Test
  fun newGuidesAreNotFavoritesAndFavoriteChangesTheHashCode() {
    assertEquals(false, guide.isFavorite)
    assertNotEquals(guide.hashCode(), guide.copy(isFavorite = true).hashCode())
  }

  @Test
  fun hashCodeSupportsMissingOptionalFields() {
    val guideWithoutOptionalFields = guide.copy(location = null, discoveryDate = null)
    val equivalentGuide = guideWithoutOptionalFields.copy(photoBytes = byteArrayOf(1, 2, 3))

    assertEquals(guideWithoutOptionalFields, equivalentGuide)
    assertEquals(guideWithoutOptionalFields.hashCode(), equivalentGuide.hashCode())
  }
}
