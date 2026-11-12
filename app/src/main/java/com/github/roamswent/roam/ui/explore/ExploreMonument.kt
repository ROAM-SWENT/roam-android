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
    /** Existing description shown when the monument marker is selected. */
    val description: String = "",
    /** Existing photo shown when the monument marker is selected. */
    val photoBytes: ByteArray = ByteArray(0),
) {
  /**
   * Marker identity remains based on the fields that determine its map position and label.
   * Description and photo are display payload and do not affect marker-list change detection.
   */
  override fun equals(other: Any?): Boolean =
      other is ExploreMonument &&
          guideId == other.guideId &&
          name == other.name &&
          coordinates == other.coordinates

  override fun hashCode(): Int {
    var result = guideId.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + coordinates.hashCode()
    return result
  }
}

/**
 * Keeps the guides that are on Explore, which are the ones with a location, in their original
 * order. Guides without a location stay in the collection only.
 */
fun List<Guide>.toExploreMonuments(): List<ExploreMonument> = mapNotNull { guide ->
  guide.location?.let {
    ExploreMonument(
        guideId = guide.id,
        name = guide.monumentName,
        description = guide.history,
        photoBytes = guide.photoBytes,
        coordinates = it,
    )
  }
}
