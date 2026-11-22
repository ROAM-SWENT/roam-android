// Co-authored-by: Codex <codex@openai.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Gemini 3.8 Flash <noreply@google.com>
package com.github.roamswent.roam.model.guide

import com.github.roamswent.roam.model.ai.GuideMilestone
import com.github.roamswent.roam.model.ai.GuideSection
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
    val sections: List<GuideSection> = emptyList(),
    val interestCount: Int = 0,
    /** Coordinates where the guide was discovered, if location was available. */
    val location: Coordinates? = null,
    /** Discovery time as Unix epoch milliseconds; null if not available. */
    val discoveryDate: Long? = System.currentTimeMillis(),
    /** Whether the user marked this guide as a favourite. */
    val isFavorite: Boolean = false,
    val shareSettings: MonumentShareSettings = MonumentShareSettings(),
    val overview: String? = null,
    val timeline: List<GuideMilestone> = emptyList(),
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
          sections == other.sections &&
          interestCount == other.interestCount &&
          location == other.location &&
          discoveryDate == other.discoveryDate &&
          isFavorite == other.isFavorite &&
          shareSettings == other.shareSettings &&
          overview == other.overview &&
          timeline == other.timeline

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + photoBytes.contentHashCode()
    result = 31 * result + monumentName.hashCode()
    result = 31 * result + confidence.hashCode()
    result = 31 * result + history.hashCode()
    result = 31 * result + facts.hashCode()
    result = 31 * result + culture.hashCode()
    result = 31 * result + sections.hashCode()
    result = 31 * result + interestCount
    result = 31 * result + (location?.hashCode() ?: 0)
    result = 31 * result + (discoveryDate?.hashCode() ?: 0)
    result = 31 * result + isFavorite.hashCode()
    result = 31 * result + shareSettings.hashCode()
    result = 31 * result + (overview?.hashCode() ?: 0)
    result = 31 * result + timeline.hashCode()
    return result
  }
}
