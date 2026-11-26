// Co-authored-by: Gemini 4 Neon <noreply@google.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.model.wishlist

import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.location.Coordinates
import java.security.MessageDigest
import kotlin.math.roundToInt

/**
 * Deterministic canonical monument key generator and validator for wishlist entries.
 *
 * Coordinate-backed keys match the coarsened grid cell representation used across monument records,
 * while guides without coordinates fall back to a reference key derived from their owner UID and
 * guide ID.
 */
object WishlistMonumentKey {
  /**
   * Maximum length permitted for a wishlist monument key document ID, accommodating the maximum
   * `ref_<128>~<128>` fallback key (4 + 128 + 1 + 128 = 261).
   */
  const val MAX_KEY_LENGTH: Int = 261

  private const val HALF_TURN_E3 = 180_000
  private const val FULL_TURN_E3 = 360_000
  private val SEGMENT_REGEX = Regex("^[A-Za-z0-9_-]{1,128}$")
  private val VALID_KEY_REGEX = Regex("^[A-Za-z0-9_~-]{1,$MAX_KEY_LENGTH}$")

  /**
   * Computes a canonical coordinate-based monument key from [location].
   *
   * @param location The monument's GPS coordinates.
   * @return A deterministic key in the form `"${latitudeE3}_$longitudeE3"`.
   */
  fun fromCoordinates(location: Coordinates): String {
    val cell = location.coarsened()
    val latitudeE3 = (cell.latitude * 1000).roundToInt()
    val longitudeE3 = wrapLongitude((cell.longitude * 1000).roundToInt())
    return "${latitudeE3}_$longitudeE3"
  }

  /**
   * Computes a fallback monument key when coordinates are unavailable.
   *
   * @param sourceOwnerUid The UID of the user who owns the referenced guide.
   * @param sourceGuideId The Firestore document ID of the referenced guide.
   * @return A deterministic fallback key in the form `"ref_${sourceOwnerUid}~$sourceGuideId"`.
   */
  fun fromFallback(sourceOwnerUid: String, sourceGuideId: String): String {
    require(SEGMENT_REGEX.matches(sourceOwnerUid)) {
      "Invalid sourceOwnerUid for fallback monument key: $sourceOwnerUid"
    }

    require(SEGMENT_REGEX.matches(sourceGuideId)) {
      "Invalid sourceGuideId for fallback monument key: $sourceGuideId"
    }
    return "ref_${sourceOwnerUid}~$sourceGuideId"
  }

  /**
   * Identifies an individual shared guide independently of its monument's coordinates, allowing
   * guides from different owners to coexist in a wishlist.
   */
  fun forSharedGuide(sourceOwnerUid: String, sourceGuideId: String): String {
    require(SEGMENT_REGEX.matches(sourceOwnerUid))
    require(sourceGuideId.isNotBlank() && sourceGuideId.length <= 128 && '/' !in sourceGuideId)
    if (SEGMENT_REGEX.matches(sourceGuideId)) return fromFallback(sourceOwnerUid, sourceGuideId)
    val digest =
        MessageDigest.getInstance("SHA-256")
            .digest("$sourceOwnerUid\u0000$sourceGuideId".toByteArray(Charsets.UTF_8))
    return "guide_" + digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
  }

  /**
   * Derives the canonical monument key for [guide], using [fromCoordinates] when [Guide.location]
   * is present and [fromFallback] otherwise.
   *
   * @param sourceOwnerUid The UID of the user who owns [guide].
   * @param guide The guide being wishlisted.
   * @return The canonical monument key for [guide].
   */
  fun forGuide(sourceOwnerUid: String, guide: Guide): String =
      if (guide.location != null) {
        fromCoordinates(guide.location)
      } else {
        fromFallback(sourceOwnerUid, guide.id)
      }

  /**
   * Checks whether [key] conforms to the wishlist monument key format enforced by Firestore rules.
   *
   * @param key The candidate monument key string.
   * @return `true` if [key] is a valid monument key, `false` otherwise.
   */
  fun isValidKey(key: String): Boolean = key.matches(VALID_KEY_REGEX)

  private fun wrapLongitude(longitudeE3: Int): Int =
      Math.floorMod(longitudeE3 + HALF_TURN_E3, FULL_TURN_E3) - HALF_TURN_E3
}
