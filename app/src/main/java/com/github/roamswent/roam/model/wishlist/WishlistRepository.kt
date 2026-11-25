// Co-authored-by: Gemini 4 Neon <noreply@google.com>

package com.github.roamswent.roam.model.wishlist

import com.github.roamswent.roam.model.profile.PublicProfile
import kotlinx.coroutines.flow.Flow

/** Manages the signed-in user's personal monument wishlist and social wishlist aggregation. */
interface WishlistRepository {
  /**
   * Adds or updates [item] in the signed-in user's wishlist, keyed idempotently by
   * [WishlistItem.monumentKey]. Supports monuments shared by friends or public accounts.
   *
   * @param item The wishlist reference to store.
   * @return The stored [WishlistItem].
   */
  suspend fun addToWishlist(item: WishlistItem): WishlistItem

  /**
   * Removes the entry identified by [monumentKey] from the signed-in user's wishlist. Idempotent if
   * the entry does not exist.
   *
   * @param monumentKey The canonical key of the monument to remove.
   */
  suspend fun removeFromWishlist(monumentKey: String)

  /**
   * Checks whether [monumentKey] is currently in the signed-in user's wishlist.
   *
   * @param monumentKey The canonical key of the monument to check.
   * @return `true` if wishlisted, `false` otherwise.
   */
  suspend fun isWishlisted(monumentKey: String): Boolean

  /**
   * Performs a one-shot read of the signed-in user's wishlist, supporting offline persistence.
   *
   * @return The list of [WishlistItem] entries in the user's wishlist.
   */
  suspend fun getWishlist(): List<WishlistItem>

  /**
   * Observes the signed-in user's wishlist as a real-time stream backed by the offline cache.
   *
   * @return A [Flow] emitting updated lists of [WishlistItem] entries.
   */
  fun observeWishlist(): Flow<List<WishlistItem>>

  /**
   * Loads the signed-in user's wishlist and resolves each referenced guide. Individual revocation
   * or deletion failures degrade to [ResolvedWishlistItem.Unavailable] rather than failing the
   * entire list.
   *
   * @return The list of resolved [ResolvedWishlistItem] entries.
   */
  suspend fun getResolvedWishlist(): List<ResolvedWishlistItem>

  /**
   * Returns the public profiles of accepted friends who have wishlisted [monumentKey].
   *
   * @param monumentKey The canonical monument key to check across accepted friends.
   * @return Profiles of accepted friends who wishlisted [monumentKey].
   */
  suspend fun getFriendsWhoWishlisted(monumentKey: String): List<PublicProfile>

  /**
   * Returns the public profiles of users in [candidateUids] (such as friends, followed public
   * accounts, or followers) who have wishlisted [monumentKey]. Per-user permission denials are
   * ignored.
   *
   * @param monumentKey The canonical monument key to check.
   * @param candidateUids The set of user UIDs to inspect.
   * @return Profiles of accessible users in [candidateUids] who wishlisted [monumentKey].
   */
  suspend fun getUsersWhoWishlisted(
      monumentKey: String,
      candidateUids: Set<String>,
  ): List<PublicProfile>
}

/** Provides access to a [WishlistRepository] instance. */
fun interface WishlistRepositoryProvider {
  /**
   * Returns the [WishlistRepository] implementation to use.
   *
   * @return A [WishlistRepository] instance.
   */
  fun repository(): WishlistRepository
}
