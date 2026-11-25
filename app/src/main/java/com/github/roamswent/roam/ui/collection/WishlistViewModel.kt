// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
 * [monuments] is a display snapshot: removed items stay in it (with [WishlistMonument.isWishlisted]
 * false) until an explicit refresh, and [refreshVersion] is incremented when the snapshot is
 * replaced.
 */
data class WishlistUiState(
    val monuments: List<WishlistMonument> = emptyList(),
    val refreshVersion: Long = 0,
    val isLoading: Boolean = false,
    val error: Exception? = null,
)

/** Integration points for the future wishlist backend. */
class WishlistViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(WishlistUiState())
  val uiState: StateFlow<WishlistUiState> = _uiState.asStateFlow()

  fun loadWishlist() = Unit

  fun retryLoadingWishlist() = loadWishlist()

  /** Replaces the snapshot, in the default order: alphabetical by name. */
  internal fun showMonuments(monuments: List<WishlistMonument>) {
    _uiState.update {
      it.copy(
          monuments = monuments.sortedBy { monument -> monument.name.lowercase() },
          refreshVersion = it.refreshVersion + 1,
          isLoading = false,
          error = null,
      )
    }
  }

  internal fun showLoading() {
    _uiState.update { it.copy(isLoading = true, error = null) }
  }

  internal fun showError(error: Exception) {
    _uiState.update { it.copy(isLoading = false, error = error) }
  }

  /** Updates the item in place so it stays in the list until the next refresh. */
  fun setWishlisted(monumentId: String, isWishlisted: Boolean) {
    _uiState.update { state ->
      state.copy(
          monuments =
              state.monuments.map {
                if (it.id == monumentId) it.copy(isWishlisted = isWishlisted) else it
              }
      )
    }
  }
}
