// Co-authored-by: Gemini 4 Neon <noreply@google.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.model.wishlist

import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.location.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WishlistMonumentKeyTest {

  @Test
  fun sharedGuideKeysDistinguishOwnersAndSupportFirestoreIds() {
    val first = WishlistMonumentKey.forSharedGuide("alice", "guide.with spaces")
    assertTrue(WishlistMonumentKey.isValidKey(first))
    assertEquals(first, WishlistMonumentKey.forSharedGuide("alice", "guide.with spaces"))
    assertNotEquals(first, WishlistMonumentKey.forSharedGuide("bob", "guide.with spaces"))
    assertNotEquals(first, WishlistMonumentKey.forSharedGuide("alice", "guide.with space"))
    assertEquals("ref_alice~guide", WishlistMonumentKey.forSharedGuide("alice", "guide"))
    assertThrowsIllegalArgument("slash") { WishlistMonumentKey.forSharedGuide("alice", "a/b") }
  }

  @Test
  fun fromCoordinates_coarsensWithinSameCellAndDistinguishesDistinctCells() {
    val firstScan = Coordinates(48.8581, 2.2941)
    val nearbyScan = Coordinates(48.8584, 2.2944)
    val distinctCellScan = Coordinates(48.8594, 2.2954)

    val firstKey = WishlistMonumentKey.fromCoordinates(firstScan)
    val nearbyKey = WishlistMonumentKey.fromCoordinates(nearbyScan)
    val distinctKey = WishlistMonumentKey.fromCoordinates(distinctCellScan)

    assertEquals(firstKey, nearbyKey)
    assertNotEquals(firstKey, distinctKey)
  }

  @Test
  fun fromCoordinates_matchesWireCompatibleMonumentKeyFormat() {
    assertEquals("48858_2294", WishlistMonumentKey.fromCoordinates(Coordinates(48.8584, 2.2945)))
    assertEquals(
        "-33857_151215",
        WishlistMonumentKey.fromCoordinates(Coordinates(-33.8568, 151.2153)),
    )
    assertEquals("90000_0", WishlistMonumentKey.fromCoordinates(Coordinates(90.0, 0.0)))
    assertEquals("-90000_0", WishlistMonumentKey.fromCoordinates(Coordinates(-90.0, 0.0)))
  }

  @Test
  fun fromCoordinates_wrapsAntimeridianConsistently() {
    assertEquals("0_-180000", WishlistMonumentKey.fromCoordinates(Coordinates(0.0, 180.0)))
    assertEquals("0_-180000", WishlistMonumentKey.fromCoordinates(Coordinates(0.0, -180.0)))
  }

  @Test
  fun forGuide_usesCoordinateKeyWhenLocationPresentAndFallbackWhenAbsent() {
    val location = Coordinates(48.8584, 2.2945)
    val guideA = sampleGuide(id = "guide-alpha", location = location)
    val guideB = sampleGuide(id = "guide-beta", location = location)
    val unlocatedGuide = sampleGuide(id = "guide-no-loc", location = null)

    assertEquals("48858_2294", WishlistMonumentKey.forGuide("owner-1", guideA))
    assertEquals(
        WishlistMonumentKey.forGuide("owner-1", guideA),
        WishlistMonumentKey.forGuide("owner-2", guideB),
    )
    assertEquals(
        "ref_owner-1~guide-no-loc",
        WishlistMonumentKey.forGuide("owner-1", unlocatedGuide),
    )
  }

  @Test
  fun fromFallback_acceptsMaxLengthSegmentsAndRejectsInvalidInputs() {
    val maxSegment = "a".repeat(128)
    assertEquals(
        "ref_${maxSegment}~$maxSegment",
        WishlistMonumentKey.fromFallback(maxSegment, maxSegment),
    )

    val invalidSegments =
        listOf(
            "",
            "   ",
            "a/b",
            "../x",
            "a b",
            "a~b",
            "a.b",
            "a".repeat(129),
        )

    for (invalid in invalidSegments) {
      assertThrowsIllegalArgument("owner='$invalid'") {
        WishlistMonumentKey.fromFallback(invalid, "valid-guide")
      }
      assertThrowsIllegalArgument("guide='$invalid'") {
        WishlistMonumentKey.fromFallback("valid-owner", invalid)
      }
    }
  }

  @Test
  fun fromFallback_generatedKeyShouldBeValid() {
    val ownerUid = "a".repeat(128)
    val guideId = "b".repeat(128)

    val key = WishlistMonumentKey.fromFallback(ownerUid, guideId)

    assertEquals(WishlistMonumentKey.MAX_KEY_LENGTH, key.length)
    assertTrue(WishlistMonumentKey.isValidKey(key))
  }

  @Test
  fun isValidKey_enforcesCharacterSetAndLengthBoundaries() {
    assertTrue(WishlistMonumentKey.isValidKey("48858_2294"))
    assertTrue(WishlistMonumentKey.isValidKey("-33857_151215"))
    assertTrue(WishlistMonumentKey.isValidKey("ref_owner-1~guide_2"))
    assertTrue(WishlistMonumentKey.isValidKey("k".repeat(WishlistMonumentKey.MAX_KEY_LENGTH)))

    val invalidKeys =
        listOf(
            "",
            "   ",
            "k".repeat(WishlistMonumentKey.MAX_KEY_LENGTH + 1),
            "48858 2294",
            "48858/2294",
            "48858.2294",
        )
    for (invalid in invalidKeys) {
      assertFalse("Expected invalid key: '$invalid'", WishlistMonumentKey.isValidKey(invalid))
    }
  }

  @Test
  fun wishlistModelsAndExceptionsPreserveFieldsAndMessages() {
    val defaultLocationItem =
        WishlistItem(
            monumentKey = "ref_owner-1~guide-1",
            sourceOwnerUid = "owner-1",
            sourceGuideId = "guide-1",
            monumentName = "Eiffel Tower",
            addedAt = 1_700_000_000_000L,
        )
    assertNull(defaultLocationItem.location)
    assertEquals("ref_owner-1~guide-1", defaultLocationItem.monumentKey)
    assertEquals("owner-1", defaultLocationItem.sourceOwnerUid)
    assertEquals("guide-1", defaultLocationItem.sourceGuideId)
    assertEquals("Eiffel Tower", defaultLocationItem.monumentName)
    assertEquals(1_700_000_000_000L, defaultLocationItem.addedAt)

    val locatedItem = defaultLocationItem.copy(location = Coordinates(48.8584, 2.2945))
    assertEquals(Coordinates(48.8584, 2.2945), locatedItem.location)

    val guide = sampleGuide(id = "guide-1", location = locatedItem.location)
    val available: ResolvedWishlistItem = ResolvedWishlistItem.Available(locatedItem, guide)
    assertEquals(locatedItem, available.item)
    assertEquals(guide, (available as ResolvedWishlistItem.Available).guide)

    assertEquals(
        listOf(
            WishlistUnavailableReason.ACCESS_REVOKED,
            WishlistUnavailableReason.GUIDE_DELETED,
            WishlistUnavailableReason.UNKNOWN,
        ),
        WishlistUnavailableReason.entries,
    )
    for (reason in WishlistUnavailableReason.entries) {
      val unavailable: ResolvedWishlistItem = ResolvedWishlistItem.Unavailable(locatedItem, reason)
      assertEquals(locatedItem, unavailable.item)
      assertEquals(reason, (unavailable as ResolvedWishlistItem.Unavailable).reason)
    }

    val cause = RuntimeException("firestore boom")
    val exceptions =
        listOf(
            WishlistRepositoryException.UnauthenticatedUser(),
            WishlistRepositoryException.InvalidUid(),
            WishlistRepositoryException.InvalidMonumentKey(),
            WishlistRepositoryException.InvalidGuideId(),
            WishlistRepositoryException.InvalidMonumentName(),
            WishlistRepositoryException.FirestoreFailure(cause),
        )
    for (exception in exceptions) {
      assertTrue(exception.message?.isNotBlank() == true)
    }
    assertSame(cause, exceptions.last().cause)
  }

  private fun sampleGuide(id: String, location: Coordinates?): Guide =
      Guide(
          id = id,
          photoBytes = byteArrayOf(1, 2, 3),
          monumentName = "Eiffel Tower",
          confidence = 0.95,
          history = "Built in 1889.",
          facts = listOf("330m tall"),
          culture = "Icon of Paris.",
          location = location,
      )

  private fun assertThrowsIllegalArgument(label: String, action: () -> Unit) {
    try {
      action()
    } catch (_: IllegalArgumentException) {
      return
    }
    throw AssertionError("Expected IllegalArgumentException for $label")
  }
}
