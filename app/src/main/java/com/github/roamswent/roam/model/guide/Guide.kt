// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.model.guide

import com.github.roamswent.roam.model.location.Coordinates

/** A saved guide, including the photo and all information shown on the detail screen. */
data class Guide(
    val id: String = "",
    val photoBytes: ByteArray,
    val monumentName: String,
    val confidence: Double,
    val history: String,
    val facts: List<String>,
    val culture: String,
    /** Coordinates where the guide was discovered, if location was available. */
    val location: Coordinates? = null,
    /** Discovery time as Unix epoch milliseconds; null if not available. */
    val discoveryDate: Long? = System.currentTimeMillis(),
) {
  override fun equals(other: Any?): Boolean =
      other is Guide &&
          id == other.id &&
          photoBytes.contentEquals(other.photoBytes) &&
          monumentName == other.monumentName &&
          confidence == other.confidence &&
          history == other.history &&
          facts == other.facts &&
          culture == other.culture &&
          location == other.location &&
          discoveryDate == other.discoveryDate

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + photoBytes.contentHashCode()
    result = 31 * result + monumentName.hashCode()
    result = 31 * result + confidence.hashCode()
    result = 31 * result + history.hashCode()
    result = 31 * result + facts.hashCode()
    result = 31 * result + culture.hashCode()
    result = 31 * result + (location?.hashCode() ?: 0)
    result = 31 * result + (discoveryDate?.hashCode() ?: 0)
    return result
  }
}
