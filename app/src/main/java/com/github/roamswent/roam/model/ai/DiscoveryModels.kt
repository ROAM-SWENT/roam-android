/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 * Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
 */

package com.github.roamswent.roam.model.ai

import com.github.roamswent.roam.model.location.Coordinates

/** Image bytes with their MIME type. In a [DiscoveryRequest] these are the raw capture. */
class ImagePayload(val bytes: ByteArray, val mimeType: String) {

  override fun equals(other: Any?): Boolean =
      other is ImagePayload && mimeType == other.mimeType && bytes.contentEquals(other.bytes)

  override fun hashCode(): Int = 31 * mimeType.hashCode() + bytes.contentHashCode()
}

/** Traveler settings that shape the guide. */
data class UserPreferences(val language: String, val interests: List<String> = emptyList())

/** Everything one scan needs. [location] is null when unavailable or denied. */
data class DiscoveryRequest(
    val image: ImagePayload,
    val location: Coordinates?,
    val preferences: UserPreferences,
)

/**
 * The structured guide returned only when a monument was identified.
 *
 * Two positions are kept apart on purpose:
 * - [location] is the observed position, where the user was when the pipeline resolved it for the
 *   prompt, or null if it was unavailable. It is the only position ever pinned on Explore.
 * - [monumentLocation] is where the monument itself stands, according to the AI, or null when it
 *   gave no trustworthy position. It is only used to check the observed position is near the
 *   monument, and is never stored.
 *
 * [photo] is the downscaled JPEG the pipeline sent to the AI, so callers can store it without
 * preparing it again.
 */
data class MonumentGuide(
    val monumentName: String,
    val confidence: Double,
    val history: String,
    val facts: List<String>,
    val culture: String,
    val language: String,
    val location: Coordinates? = null,
    val photo: ImagePayload? = null,
    val monumentLocation: Coordinates? = null,
)

/** Result of the cloud pre-screen on the photo. */
data class ScreenVerdict(
    val isMonument: Boolean,
    val injectionSuspected: Boolean,
    val reason: String,
)
