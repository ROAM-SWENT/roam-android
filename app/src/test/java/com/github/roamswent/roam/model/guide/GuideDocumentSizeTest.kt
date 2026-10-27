// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.model.guide

import com.github.roamswent.roam.model.location.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideDocumentSizeTest {
  @Test
  fun estimatesTextAndPhotoBytes() {
    val shortGuide = guide(photoBytes = byteArrayOf(1, 2, 3))
    val longerGuide = shortGuide.copy(history = "é".repeat(20))

    assertEquals(
        26L,
        GuideDocumentSize.estimatedBytes(longerGuide) -
            GuideDocumentSize.estimatedBytes(shortGuide),
    )
    GuideDocumentSize.requireWithinLimit(shortGuide)
  }

  @Test
  fun estimatesOptionalLocationAndDiscoveryDate() {
    val withoutMetadata = guide(photoBytes = byteArrayOf(1, 2, 3)).copy(discoveryDate = null)
    val withMetadata =
        withoutMetadata.copy(
            location = Coordinates(1.0, 2.0),
            discoveryDate = 1L,
        )

    assertTrue(
        GuideDocumentSize.estimatedBytes(withMetadata) >
            GuideDocumentSize.estimatedBytes(withoutMetadata)
    )
  }

  @Test
  fun rejectsGuideThatWouldExceedConservativeDocumentLimit() {
    val tooLarge = guide(photoBytes = ByteArray(GuideDocumentSize.FIRESTORE_DOCUMENT_LIMIT_BYTES))

    assertThrows(IllegalArgumentException::class.java) {
      GuideDocumentSize.requireWithinLimit(tooLarge)
    }
  }

  private fun guide(photoBytes: ByteArray) =
      Guide(
          id = "guide-id",
          photoBytes = photoBytes,
          monumentName = "Eiffel Tower",
          confidence = 0.92,
          history = "Built in 1889.",
          facts = listOf("330 meters tall"),
          culture = "A symbol of Paris.",
      )
}
