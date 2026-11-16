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

/**
 * Everything one scan needs. [location] is the observed position, or null when it is unknown.
 *
 * When [location] is null and [useDeviceLocation] is true, the pipeline asks the device for its
 * current position, as for a camera scan taken on the spot. When [useDeviceLocation] is false, a
 * null [location] means no location at all and the device is never asked, as for a gallery photo
 * whose only source is its own metadata.
 */
data class DiscoveryRequest(
    val image: ImagePayload,
    val location: Coordinates?,
    val preferences: UserPreferences,
    val useDeviceLocation: Boolean = true,
)

/**
 * The structured guide returned only when a monument was identified. [location] is the position the
 * discovery pipeline already resolved for the prompt, or null if it was unavailable. [photo] is the
 * downscaled JPEG the pipeline sent to the AI, so callers can store it without preparing it again.
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
)

/** Result of the cloud pre-screen on the photo. */
data class ScreenVerdict(
    val isMonument: Boolean,
    val injectionSuspected: Boolean,
    val reason: String,
)
