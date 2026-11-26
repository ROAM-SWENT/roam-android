// Co-authored-by: Codex <codex@openai.com>
package com.github.roamswent.roam.ui.explore

import java.text.Normalizer
import java.util.Locale

internal data class ExploreSharedPin(val ownerUid: String, val monument: ExploreMonument) {
  val key = ownerUid to monument.guideId
}

internal class ExploreFriendPinGroup(val pins: List<ExploreSharedPin>) {
  val first = pins.first()
  val ownerUids = pins.map { it.ownerUid }.distinct()
  val visibleOwnerUids = ownerUids.take(MAX_STACKED_AVATARS)
  val additionalOwnerCount = ownerUids.size - visibleOwnerUids.size
}

/** Saved guides have scan coordinates and owner-scoped IDs, with no canonical monument ID. */
internal fun List<ExploreFriendMonuments>.groupFriendPins(): List<ExploreFriendPinGroup> {
  val groupsByName = mutableMapOf<String, MutableList<MutableList<ExploreSharedPin>>>()
  val groups = mutableListOf<List<ExploreSharedPin>>()
  val pins = flatMap { friend -> friend.monuments.map { ExploreSharedPin(friend.ownerUid, it) } }
  for (pin in pins.sortedWith(friendPinOrder)) {
    val monument = pin.monument
    val name =
        Normalizer.normalize(monument.name.trim().lowercase(Locale.ROOT), Normalizer.Form.NFC)
    val candidates = groupsByName.getOrPut(name) { mutableListOf() }
    // Compare with a stable first scan so nearby pins cannot chain together distant sites.
    val group = candidates.firstOrNull {
      it.first().monument.coordinates.distanceTo(monument.coordinates) <= SAME_MONUMENT_METRES
    }
    if (group == null) {
      val newGroup = mutableListOf(pin)
      candidates.add(newGroup)
      groups.add(newGroup)
    } else {
      group.add(pin)
    }
  }
  return groups.map(::ExploreFriendPinGroup)
}

private val friendPinOrder =
    compareBy<ExploreSharedPin>(
        { it.monument.coordinates.latitude },
        { it.monument.coordinates.longitude },
        { it.ownerUid },
        { it.monument.guideId },
    )

private const val SAME_MONUMENT_METRES = 50.0
private const val MAX_STACKED_AVATARS = 3
