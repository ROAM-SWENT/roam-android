// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.model.guide

import com.github.roamswent.roam.model.location.Coordinates
import com.google.firebase.firestore.Blob
import org.junit.Assert.assertEquals
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
}
