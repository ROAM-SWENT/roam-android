// Co-authored-by: Codex <codex@openai.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Gemini 3.8 Flash <noreply@google.com>
package com.github.roamswent.roam.model.guide

import com.github.roamswent.roam.model.ai.GuideMilestone
import com.github.roamswent.roam.model.ai.GuideSection
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
  fun estimatesGuideSectionStringsAndMapOverhead() {
    val withoutSections = guide(photoBytes = byteArrayOf(1, 2, 3))
    val withOneSection =
        withoutSections.copy(
            sections = listOf(GuideSection("Architecture", "Detailed context.")),
        )

    // Title and content field names add 14 bytes to the strings and 32-byte map overhead.
    assertEquals(
        77L,
        GuideDocumentSize.estimatedBytes(withOneSection, documentPath) -
            GuideDocumentSize.estimatedBytes(withoutSections, documentPath),
    )
  }

  @Test
  fun estimatesOptionalOverviewAndTimeline() {
    val base = guide(photoBytes = byteArrayOf(1, 2, 3))
    val withTimeline =
        base.copy(overview = "Résumé", timeline = listOf(GuideMilestone(-200, "Érigé")))

    assertEquals(
        86L,
        GuideDocumentSize.estimatedBytes(withTimeline, documentPath) -
            GuideDocumentSize.estimatedBytes(base, documentPath),
    )
  }

  @Test
  fun favoriteFlagHasTheSameSizeWhateverItsValue() {
    val notFavorite = guide(photoBytes = byteArrayOf(1, 2, 3))

    assertEquals(
        GuideDocumentSize.estimatedBytes(notFavorite, documentPath),
        GuideDocumentSize.estimatedBytes(notFavorite.copy(isFavorite = true), documentPath),
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

  @Test
  fun estimatesSharingSettingsFieldsAndUidCollections() {
    val baseGuide =
        guide(photoBytes = byteArrayOf(1, 2, 3))
            .copy(shareSettings = MonumentShareSettings(audience = ShareAudience.NONE))
    val withLongerAudience =
        baseGuide.copy(
            shareSettings = baseGuide.shareSettings.copy(audience = ShareAudience.SELECTED_FRIENDS),
        )
    // "SELECTED_FRIENDS" (16 chars + 1) vs "NONE" (4 chars + 1) = 17 - 5 = 12 bytes
    assertEquals(
        12L,
        GuideDocumentSize.estimatedBytes(withLongerAudience, documentPath) -
            GuideDocumentSize.estimatedBytes(baseGuide, documentPath),
    )

    // Adding sharedWithUids: field name "sharedWithUids" (14 chars + 1 = 15 bytes)
    // + values: "uid1" (4 + 1 = 5) + "uid2" (4 + 1 = 5) = 10 bytes -> total 25 bytes
    val withSharedUids =
        baseGuide.copy(
            shareSettings = baseGuide.shareSettings.copy(sharedWithUids = setOf("uid1", "uid2")),
        )
    assertEquals(
        25L,
        GuideDocumentSize.estimatedBytes(withSharedUids, documentPath) -
            GuideDocumentSize.estimatedBytes(baseGuide, documentPath),
    )

    // Adding dismissedByUids: field name "dismissedByUids" (15 chars + 1 = 16 bytes)
    // + values: "uid3" (4 + 1 = 5 bytes) -> total 21 bytes
    val withDismissedUids =
        baseGuide.copy(
            shareSettings = baseGuide.shareSettings.copy(dismissedByUids = setOf("uid3")),
        )
    assertEquals(
        21L,
        GuideDocumentSize.estimatedBytes(withDismissedUids, documentPath) -
            GuideDocumentSize.estimatedBytes(baseGuide, documentPath),
    )
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
