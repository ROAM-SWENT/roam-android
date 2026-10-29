// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.model.guide

import com.github.roamswent.roam.model.location.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GuideDocumentSizeTest {
  private val documentPath = "users/user-id/savedGuides/guide-id"

  @Test
  fun estimatesTextAndPhotoBytes() {
    val shortGuide = guide(photoBytes = byteArrayOf(1, 2, 3))
    val longerGuide = shortGuide.copy(history = "é".repeat(20))

    assertEquals(
        26L,
        GuideDocumentSize.estimatedBytes(longerGuide, documentPath) -
            GuideDocumentSize.estimatedBytes(shortGuide, documentPath),
    )
    GuideDocumentSize.requireWithinLimit(shortGuide, documentPath)
  }

  @Test
  fun estimatesOptionalLocationAndDiscoveryDate() {
    val withoutMetadata = guide(photoBytes = byteArrayOf(1, 2, 3)).copy(discoveryDate = null)
    val withMetadata =
        withoutMetadata.copy(
            location = Coordinates(1.0, 2.0),
            discoveryDate = 1L,
        )

    assertEquals(
        98L,
        GuideDocumentSize.estimatedBytes(withMetadata, documentPath) -
            GuideDocumentSize.estimatedBytes(withoutMetadata, documentPath),
    )
  }

  @Test
  fun includesAdditionalPathSegmentsAndFactStrings() {
    val baseGuide = guide(photoBytes = byteArrayOf(1, 2, 3))

    assertEquals(
        2L,
        GuideDocumentSize.estimatedBytes(baseGuide, "$documentPath/x") -
            GuideDocumentSize.estimatedBytes(baseGuide, documentPath),
    )
    assertEquals(
        3L,
        GuideDocumentSize.estimatedBytes(
            baseGuide.copy(facts = baseGuide.facts + "é"),
            documentPath,
        ) - GuideDocumentSize.estimatedBytes(baseGuide, documentPath),
    )
  }

  @Test
  fun accountsForBlobEncodingAndStructuredValueOverhead() {
    val threeBytes = guide(photoBytes = ByteArray(3))
    val fourBytes = threeBytes.copy(photoBytes = ByteArray(4))

    assertEquals(
        1L,
        GuideDocumentSize.estimatedBytes(fourBytes, documentPath) -
            GuideDocumentSize.estimatedBytes(threeBytes, documentPath),
    )
  }

  @Test
  fun rejectsPhotoWhoseBlobWouldExceedLimit() {
    val tooLarge = guide(photoBytes = ByteArray(1_048_000))

    assertThrows(IllegalArgumentException::class.java) {
      GuideDocumentSize.requireWithinLimit(tooLarge, documentPath)
    }
  }

  @Test
  fun rejectsGuideThatWouldExceedConservativeDocumentLimit() {
    val tooLarge =
        guide(photoBytes = ByteArray(GuideDocumentSize.FIRESTORE_DOCUMENT_LIMIT_BYTES.toInt()))

    assertThrows(IllegalArgumentException::class.java) {
      GuideDocumentSize.requireWithinLimit(tooLarge, documentPath)
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
