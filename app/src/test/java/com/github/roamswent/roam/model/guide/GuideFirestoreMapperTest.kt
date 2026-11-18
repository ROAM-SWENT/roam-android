// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Gemini 3.8 Flash <noreply@google.com>
package com.github.roamswent.roam.model.guide

import com.github.roamswent.roam.model.ai.GuideSection
import com.github.roamswent.roam.model.location.Coordinates
import com.google.firebase.firestore.Blob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
          location = Coordinates(48.8584, 2.2945),
          discoveryDate = 1_798_000_000_000,
      )

  @Test
  fun fieldsRoundTripAllGuideInformationAndPhotoBytes() {
    val fields = GuideFirestoreMapper.toFields(guide)
    val restored = GuideFirestoreMapper.fromFields(guide.id, fields)

    assertEquals(guide, restored)
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

    assertEquals(ShareAudience.ALL_FRIENDS.name, fields[GuideFirestoreMapper.SHARE_AUDIENCE_FIELD])
    assertFalse(fields.containsKey(GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD))
    assertFalse(fields.containsKey(GuideFirestoreMapper.DISMISSED_BY_UIDS_FIELD))
  }

  @Test
  fun fromFieldsTreatsLegacyDocumentWithoutAudienceAsAllFriends() {
    val fields = GuideFirestoreMapper.toFields(guide).toMutableMap()
    fields.remove(GuideFirestoreMapper.SHARE_AUDIENCE_FIELD)

    val restored = GuideFirestoreMapper.fromFields(guide.id, fields)
    assertEquals(ShareAudience.ALL_FRIENDS, restored.shareSettings.audience)
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
