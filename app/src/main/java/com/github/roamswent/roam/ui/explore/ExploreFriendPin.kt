// Co-authored-by: Codex <codex@openai.com>
package com.github.roamswent.roam.ui.explore

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
  for (friend in this) {
    for (monument in friend.monuments) {
      val pin = ExploreSharedPin(friend.ownerUid, monument)
      val name = monument.name.trim().lowercase(Locale.ROOT)
      val candidates = groupsByName.getOrPut(name) { mutableListOf() }
      // Compare with each group's first scan so a chain of nearby pins cannot merge distant sites.
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
  }
  return groups.map(::ExploreFriendPinGroup)
}

private const val SAME_MONUMENT_METRES = 50.0
internal const val MAX_STACKED_AVATARS = 3
