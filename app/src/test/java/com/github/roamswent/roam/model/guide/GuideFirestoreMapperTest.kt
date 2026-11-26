// Co-authored-by: Codex <codex@openai.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Gemini 3.8 Flash <noreply@google.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>

package com.github.roamswent.roam.model.guide

import com.github.roamswent.roam.model.ai.GuideMilestone
import com.github.roamswent.roam.model.ai.GuideSection
import com.github.roamswent.roam.model.location.Coordinates
import com.google.firebase.firestore.Blob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideFirestoreMapperTest {
  private val guide =
      Guide(
          id = "guide-id",
          photoBytes = byteArrayOf(1, 2, 3),
          monumentName = "Eiffel Tower",
          confidence = 0.92,
          history = "Built in 1889.",
          facts = listOf("330 meters tall"),
          culture = "A symbol of Paris.",
          sections = listOf(GuideSection("Architecture", "A detailed paragraph.")),
          interestCount = 1,
          overview = "An iron landmark.",
          timeline = listOf(GuideMilestone(1889, "Opened.")),
          location = Coordinates(48.8584, 2.2945),
          discoveryDate = 1_798_000_000_000,
      )

  @Test
  fun fieldsRoundTripAllGuideInformationAndPhotoBytes() {
    val fields = GuideFirestoreMapper.toFields(guide)
    val restored = GuideFirestoreMapper.fromFields(guide.id, fields)

    assertEquals(guide, restored)
    assertEquals(guide.hashCode(), restored.hashCode())
    assertTrue((fields.getValue("photo") as Blob).toBytes().contentEquals(guide.photoBytes))
  }

  @Test
  fun fromFieldsRejectsMissingGuideInformation() {
    val fields = GuideFirestoreMapper.toFields(guide).toMutableMap()
    fields.remove("history")

    assertThrows(IllegalStateException::class.java) {
      GuideFirestoreMapper.fromFields(guide.id, fields)
    }
  }

  @Test
  fun fromFieldsSupportsLegacyGuidesWithoutLocationOrDiscoveryDate() {
    val fields = GuideFirestoreMapper.toFields(guide).toMutableMap()
    fields.remove("location")
    fields.remove("discoveryDate")

    val restored = GuideFirestoreMapper.fromFields(guide.id, fields)

    assertEquals(null, restored.location)
    assertEquals(null, restored.discoveryDate)
  }

  @Test
  fun oldGuidesStillLoadWithTheirInterestContentAndWithoutNewFields() {
    val legacy = guide.copy(overview = null, timeline = emptyList())

    val fields = GuideFirestoreMapper.toFields(legacy)

    assertFalse(fields.containsKey("overview"))
    assertFalse(fields.containsKey("timeline"))
    assertEquals(legacy, GuideFirestoreMapper.fromFields(guide.id, fields))
  }

  @Test
  fun timelineReadsFirestoreIntegersAndSkipsInvalidOptionalRows() {
    val fields =
        GuideFirestoreMapper.toFields(guide) +
            ("timeline" to
                listOf(
                    mapOf("year" to 1889L, "event" to "Opened."),
                    5,
                    mapOf("year" to 0L, "event" to "Invalid."),
                    mapOf(
                        "year" to Int.MIN_VALUE.toLong(),
                        "event" to "Outside the supported range.",
                    ),
                    mapOf("year" to Long.MAX_VALUE, "event" to "Outside the supported range."),
                    mapOf("year" to "1900", "event" to "Invalid."),
                    mapOf("year" to 1900L, "event" to 5),
                    mapOf("year" to 1900L, "event" to ""),
                ))

    assertEquals(guide.timeline, GuideFirestoreMapper.fromFields(guide.id, fields).timeline)
  }

  @Test
  fun overviewAndTimelineChangesAffectSavedGuideEquality() {
    assertNotEquals(guide, guide.copy(overview = "A revised introduction."))
    assertNotEquals(guide, guide.copy(timeline = emptyList()))
  }

  @Test
  fun favoriteStateRoundTrips() {
    val favorite = guide.copy(isFavorite = true)

    val fields = GuideFirestoreMapper.toFields(favorite)

    assertEquals(true, fields.getValue(GuideFirestoreMapper.FAVORITE_FIELD))
    assertEquals(favorite, GuideFirestoreMapper.fromFields(guide.id, fields))
  }

  @Test
  fun fromFieldsTreatsLegacyGuidesWithoutFavoriteFlagAsNotFavorite() {
    val fields = GuideFirestoreMapper.toFields(guide.copy(isFavorite = true)).toMutableMap()
    fields.remove(GuideFirestoreMapper.FAVORITE_FIELD)

    assertEquals(false, GuideFirestoreMapper.fromFields(guide.id, fields).isFavorite)
  }

  @Test
  fun sharingSettingsRoundTripWithExplicitRecipientsAndDismissals() {
    val customSharingGuide =
        guide.copy(
            shareSettings =
                MonumentShareSettings(
                    audience = ShareAudience.SELECTED_FRIENDS,
                    sharedWithUids = setOf("uid-b", "uid-a"),
                    dismissedByUids = setOf("uid-z", "uid-y"),
                ),
        )

    val fields = GuideFirestoreMapper.toFields(customSharingGuide)

    assertEquals(
        ShareAudience.SELECTED_FRIENDS.name,
        fields[GuideFirestoreMapper.SHARE_AUDIENCE_FIELD],
    )
    assertEquals(listOf("uid-a", "uid-b"), fields[GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD])
    assertEquals(listOf("uid-y", "uid-z"), fields[GuideFirestoreMapper.DISMISSED_BY_UIDS_FIELD])

    val restored = GuideFirestoreMapper.fromFields(customSharingGuide.id, fields)
    assertEquals(customSharingGuide, restored)
  }

  @Test
  fun toFieldsAlwaysWritesAudienceAndOmitsEmptyRecipientSets() {
    val fields = GuideFirestoreMapper.toFields(guide)

    assertEquals(ShareAudience.NONE.name, fields[GuideFirestoreMapper.SHARE_AUDIENCE_FIELD])
    assertFalse(fields.containsKey(GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD))
    assertFalse(fields.containsKey(GuideFirestoreMapper.DISMISSED_BY_UIDS_FIELD))
  }

  @Test
  fun fromFieldsTreatsLegacyDocumentWithoutAudienceAsPrivate() {
    val fields = GuideFirestoreMapper.toFields(guide).toMutableMap()
    fields.remove(GuideFirestoreMapper.SHARE_AUDIENCE_FIELD)

    val restored = GuideFirestoreMapper.fromFields(guide.id, fields)
    assertEquals(ShareAudience.NONE, restored.shareSettings.audience)
  }

  @Test
  fun fromFieldsTreatsLegacyExploreGuidesAsAllowed() {
    val fields = GuideFirestoreMapper.toFields(guide).toMutableMap()
    fields.remove(GuideFirestoreMapper.SHARING_ELIGIBILITY_FIELD)

    assertEquals(
        SharingEligibility.ALLOWED,
        GuideFirestoreMapper.fromFields(guide.id, fields).sharingEligibility,
    )
  }

  @Test
  fun fromFieldsTreatsLegacyGuidesWithoutLocationAsUnknownEligibility() {
    val fields = GuideFirestoreMapper.toFields(guide).toMutableMap()
    fields.remove(GuideFirestoreMapper.SHARING_ELIGIBILITY_FIELD)
    fields.remove(GuideFirestoreMapper.LOCATION_FIELD)

    assertEquals(
        SharingEligibility.UNKNOWN,
        GuideFirestoreMapper.fromFields(guide.id, fields).sharingEligibility,
    )
  }

  @Test
  fun sharingEligibilityRoundTrips() {
    val tooFar = guide.copy(sharingEligibility = SharingEligibility.TOO_FAR)

    assertEquals(
        tooFar,
        GuideFirestoreMapper.fromFields(tooFar.id, GuideFirestoreMapper.toFields(tooFar)),
    )
  }

  @Test
  fun fromFieldsFallsBackToUnknownForUnrecognizedOrInvalidEligibilityType() {
    val unknownFields = GuideFirestoreMapper.toFields(guide).toMutableMap()
    unknownFields[GuideFirestoreMapper.SHARING_ELIGIBILITY_FIELD] =
        "FUTURE_UNRECOGNIZED_ELIGIBILITY"

    val restoredUnknown = GuideFirestoreMapper.fromFields(guide.id, unknownFields)
    assertEquals(SharingEligibility.UNKNOWN, restoredUnknown.sharingEligibility)

    val invalidTypeFields = GuideFirestoreMapper.toFields(guide).toMutableMap()
    invalidTypeFields[GuideFirestoreMapper.SHARING_ELIGIBILITY_FIELD] = 12345

    val restoredInvalidType = GuideFirestoreMapper.fromFields(guide.id, invalidTypeFields)
    assertEquals(SharingEligibility.UNKNOWN, restoredInvalidType.sharingEligibility)
  }

  @Test
  fun fromFieldsFallsBackToNoneForUnrecognizedOrInvalidAudienceType() {
    val unknownFields = GuideFirestoreMapper.toFields(guide).toMutableMap()
    unknownFields[GuideFirestoreMapper.SHARE_AUDIENCE_FIELD] = "FUTURE_UNRECOGNIZED_AUDIENCE"

    val restoredUnknown = GuideFirestoreMapper.fromFields(guide.id, unknownFields)
    assertEquals(ShareAudience.NONE, restoredUnknown.shareSettings.audience)

    val invalidTypeFields = GuideFirestoreMapper.toFields(guide).toMutableMap()
    invalidTypeFields[GuideFirestoreMapper.SHARE_AUDIENCE_FIELD] = 12345

    val restoredInvalidType = GuideFirestoreMapper.fromFields(guide.id, invalidTypeFields)
    assertEquals(ShareAudience.NONE, restoredInvalidType.shareSettings.audience)
  }
}
