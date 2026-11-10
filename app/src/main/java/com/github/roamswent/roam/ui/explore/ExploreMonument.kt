// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.explore

import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.location.Coordinates

/** A saved monument shown on the Explore map, holding only the data the map needs. */
data class ExploreMonument(
    /** Identifier of the saved guide this monument comes from. */
    val guideId: String,
    /** Monument name, used as the marker title. */
    val name: String,
    /** Where the marker is placed. */
    val coordinates: Coordinates,
)

/**
 * Keeps the guides that are on Explore, which are the ones with a location, in their original
 * order. Guides without a location stay in the collection only.
 */
fun List<Guide>.toExploreMonuments(): List<ExploreMonument> = mapNotNull { guide ->
  guide.location?.let { ExploreMonument(guide.id, guide.monumentName, it) }
}
