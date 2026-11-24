// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WishlistMonument(
    val id: String,
    val name: String,
    val city: String = "",
    val country: String = "",
    val photoUrl: String? = null,
    val scannedBy: List<WishlistFriend> = emptyList(),
    val isWishlisted: Boolean = true,
)

data class WishlistFriend(val name: String, val avatarUrl: String? = null)

/**
 * [monuments] is a display snapshot: keep removed items in it until an explicit refresh, and
 * increment [refreshVersion] when replacing that snapshot.
 */
data class WishlistUiState(
    val monuments: List<WishlistMonument> = emptyList(),
    val refreshVersion: Long = 0,
)

/** Integration points for the future wishlist backend. */
class WishlistViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(WishlistUiState())
  val uiState: StateFlow<WishlistUiState> = _uiState.asStateFlow()

  fun loadWishlist() = Unit

  @Suppress("UNUSED_PARAMETER") fun setWishlisted(monumentId: String, isWishlisted: Boolean) = Unit
}
