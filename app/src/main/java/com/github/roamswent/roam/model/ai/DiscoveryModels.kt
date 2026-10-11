package com.github.roamswent.roam.model.ai

import com.github.roamswent.roam.model.location.Coordinates

/** Image bytes with their MIME type. In a [DiscoveryRequest] these are the raw capture. */
class ImagePayload(val bytes: ByteArray, val mimeType: String) {

  override fun equals(other: Any?): Boolean =
      other is ImagePayload && mimeType == other.mimeType && bytes.contentEquals(other.bytes)

  override fun hashCode(): Int = 31 * mimeType.hashCode() + bytes.contentHashCode()
}

/** Traveler settings that shape the guide. Only the response language for now. */
data class UserPreferences(val language: String)

/** Everything one scan needs. [location] is null when unavailable or denied. */
data class DiscoveryRequest(
    val image: ImagePayload,
    val location: Coordinates?,
    val preferences: UserPreferences,
)

/** The structured guide returned only when a monument was identified. */
data class MonumentGuide(
    val monumentName: String,
    val confidence: Double,
    val history: String,
    val facts: List<String>,
    val culture: String,
    val language: String,
)

/** Result of the cloud pre-screen on the photo. */
data class ScreenVerdict(
    val isMonument: Boolean,
    val injectionSuspected: Boolean,
    val reason: String,
)
