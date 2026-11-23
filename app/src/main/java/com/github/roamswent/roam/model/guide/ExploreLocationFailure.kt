// Co-authored-by: Codex <codex@openai.com>
package com.github.roamswent.roam.model.guide

/** Why a discovery failed the location check for Explore. */
enum class ExploreUnavailableReason {
  NO_OBSERVED_LOCATION,
  NO_MONUMENT_LOCATION,
  TOO_FAR,
}

/** Retains the warning shown when reopening a guide, independently of its opt-in pin. */
data class ExploreLocationFailure(
    val reason: ExploreUnavailableReason,
    val isGalleryImage: Boolean,
)
