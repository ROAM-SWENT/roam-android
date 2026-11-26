// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.model.wishlist

import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.guide.MonumentSharingRepository
import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Monument scanned by accepted friends, derived from guides they share with the signed-in user.
 *
 * @property key Normalized name: trimmed, whitespace-collapsed and lowercased. Use it to match.
 * @property displayName Name as first seen (trimmed, whitespace-collapsed), ordered by friend UID.
 * @property friends Unique friends who scanned it, ordered by UID; a friend without a public
 *   profile is kept with a null [FriendScanner.profile].
 */
data class FriendScannedMonument(
    val key: String,
    val displayName: String,
    val friends: List<FriendScanner>,
) {
  val friendCount: Int
    get() = friends.size
}

/** A friend who scanned a monument, with their public profile when one exists. */
data class FriendScanner(val uid: String, val profile: PublicProfile?)

fun normalizeSharedMonumentName(name: String): String =
    name.trim().replace(Regex("\\s+"), " ").lowercase()

/** Supplies live friend-scanned monuments for wishlist "scanned by" aggregation. */
class FriendScannedMonumentsSource(
    private val friendRepository: FriendRepository,
    private val monumentSharingRepository: MonumentSharingRepository,
    private val publicProfileRepository: PublicProfileRepository,
) {
  /**
   * Emits the aggregated monuments whenever any friend's shared guides change. Emits an empty list
   * when there are no friends. A friend whose guides cannot be observed counts as sharing none, and
   * a failing profile counts as missing; only a failure to load the friend list is propagated to
   * the collector. Profiles are loaded once per friend, only when that friend shares a named guide.
   */
  fun observe(): Flow<List<FriendScannedMonument>> = flow {
    val friendUids = friendRepository.getFriends().map { it.friendUid }.distinct().sorted()
    if (friendUids.isEmpty()) {
      emit(emptyList())
      return@flow
    }
    val perFriendNames = friendUids.map { uid ->
      monumentSharingRepository
          .observeSharedGuidesFrom(uid)
          .map { guides -> guides.map { it.monumentName.collapse() }.filter { it.isNotEmpty() } }
          .catch { emit(emptyList()) }
    }
    emitAll(
        combine(perFriendNames) { namesByFriend ->
          val scanners = linkedMapOf<String, Pair<String, MutableSet<String>>>()
          namesByFriend.forEachIndexed { index, names ->
            val uid = friendUids[index]
            for (name in names) {
              scanners
                  .getOrPut(normalizeSharedMonumentName(name)) { name to linkedSetOf() }
                  .second
                  .add(uid)
            }
          }
          scanners.map { (key, entry) ->
            val (displayName, uids) = entry
            FriendScannedMonument(
                key = key,
                displayName = displayName,
                friends = uids.map { uid -> FriendScanner(uid, profileOrNull(uid)) },
            )
          }
        }
    )
  }
      .distinctUntilChanged()

  // Kept across observations so reopening the wishlist does not reload every profile.
  private val profiles = mutableMapOf<String, PublicProfile?>()

  private suspend fun profileOrNull(uid: String): PublicProfile? =
      if (uid in profiles) profiles[uid]
      else
          try {
            publicProfileRepository.getProfile(uid).also { profiles[uid] = it }
          } catch (exception: CancellationException) {
            throw exception
          } catch (_: Exception) {
            null
          }

  private fun String.collapse(): String = trim().replace(WHITESPACE, " ")

  private companion object {
    val WHITESPACE = Regex("\\s+")
  }
}
