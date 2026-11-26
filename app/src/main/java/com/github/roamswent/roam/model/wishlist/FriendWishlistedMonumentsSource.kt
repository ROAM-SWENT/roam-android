// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.model.wishlist

import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Monument present in accepted friends' wishlists.
 *
 * @property key Normalized name (see [normalizeSharedMonumentName]). Use it to match.
 * @property displayName Name as first seen (trimmed, whitespace-collapsed), ordered by friend UID.
 * @property friends Unique friends who wishlisted it, ordered by UID; a friend without a public
 *   profile is kept with a null [FriendWishlister.profile].
 */
data class FriendWishlistedMonument(
    val key: String,
    val displayName: String,
    val friends: List<FriendWishlister>,
) {
  val friendCount: Int
    get() = friends.size
}

/** A friend who wishlisted a monument, with their public profile when one exists. */
data class FriendWishlister(val uid: String, val profile: PublicProfile?)

/** Supplies live friend-wishlisted monuments, matched by normalized monument name. */
class FriendWishlistedMonumentsSource(
    private val friendRepository: FriendRepository,
    private val friendWishlistRepository: FriendWishlistRepository,
    private val publicProfileRepository: PublicProfileRepository,
) {
  /**
   * Emits the aggregated monuments whenever any friend's wishlist changes. Emits an empty list when
   * there are no friends. Friend, wishlist and profile errors are propagated to the collector.
   * Profiles are loaded once per friend, only when that friend wishlists a named monument.
   */
  fun observe(): Flow<List<FriendWishlistedMonument>> = flow {
    val friendUids = friendRepository.getFriends().map { it.friendUid }.distinct().sorted()
    if (friendUids.isEmpty()) {
      emit(emptyList())
      return@flow
    }
    val profiles = mutableMapOf<String, PublicProfile?>()
    val perFriendNames = friendUids.map { uid ->
      friendWishlistRepository.observeWishlistedMonumentNames(uid)
    }
    emitAll(
        combine(perFriendNames) { namesByFriend ->
          val wishers = linkedMapOf<String, Pair<String, MutableSet<String>>>()
          namesByFriend.forEachIndexed { index, rawNames ->
            val uid = friendUids[index]
            for (name in rawNames.map { it.collapse() }.filter { it.isNotEmpty() }) {
              wishers
                  .getOrPut(normalizeSharedMonumentName(name)) { name to linkedSetOf() }
                  .second
                  .add(uid)
            }
          }
          wishers.map { (key, entry) ->
            val (displayName, uids) = entry
            FriendWishlistedMonument(
                key = key,
                displayName = displayName,
                friends =
                    uids.map { uid ->
                      if (uid !in profiles) {
                        profiles[uid] = publicProfileRepository.getProfile(uid)
                      }
                      FriendWishlister(uid, profiles[uid])
                    },
            )
          }
        }
    )
  }
      .distinctUntilChanged()

  private fun String.collapse(): String = trim().replace(WHITESPACE, " ")

  private companion object {
    val WHITESPACE = Regex("\\s+")
  }
}
