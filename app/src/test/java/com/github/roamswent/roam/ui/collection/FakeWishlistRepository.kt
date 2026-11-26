// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.model.wishlist.ResolvedWishlistItem
import com.github.roamswent.roam.model.wishlist.WishlistItem
import com.github.roamswent.roam.model.wishlist.WishlistRepository
import com.github.roamswent.roam.model.wishlist.WishlistUnavailableReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

class FakeWishlistRepository : WishlistRepository {
  val items = MutableStateFlow<List<WishlistItem>>(emptyList())
  val guides = mutableMapOf<String, Guide>()
  var loadError: Exception? = null
  var writeError: Exception? = null

  override fun observeWishlist(): Flow<List<WishlistItem>> = flow {
    loadError?.let { throw it }
    items.collect { emit(it) }
  }

  override suspend fun getResolvedWishlist(): List<ResolvedWishlistItem> {
    loadError?.let { throw it }
    return items.value.map { item ->
      guides[item.monumentKey]?.let { ResolvedWishlistItem.Available(item, it) }
          ?: ResolvedWishlistItem.Unavailable(item, WishlistUnavailableReason.GUIDE_DELETED)
    }
  }

  override suspend fun addToWishlist(item: WishlistItem): WishlistItem {
    writeError?.let { throw it }
    items.value = items.value.filterNot { it.monumentKey == item.monumentKey } + item
    return item
  }

  override suspend fun removeFromWishlist(monumentKey: String) {
    writeError?.let { throw it }
    items.value = items.value.filterNot { it.monumentKey == monumentKey }
  }

  override suspend fun isWishlisted(monumentKey: String) =
      items.value.any { it.monumentKey == monumentKey }

  override suspend fun getWishlist() = items.value

  override suspend fun getFriendsWhoWishlisted(monumentKey: String): List<PublicProfile> =
      emptyList()

  override suspend fun getUsersWhoWishlisted(
      monumentKey: String,
      candidateUids: Set<String>,
  ): List<PublicProfile> = emptyList()
}
