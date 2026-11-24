// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.wishlist

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Tracks which monuments are in the wishlist.
 *
 * The wishlist backend is not available yet, so the state is only kept in memory; persistence will
 * be plugged in here once it lands.
 */
class WishlistViewModel : ViewModel() {
  private val _wishlistedIds = MutableStateFlow<Set<String>>(emptySet())
  val wishlistedIds: StateFlow<Set<String>> = _wishlistedIds.asStateFlow()

  /** Adds the monument to the wishlist, or removes it if it is already there. */
  fun toggleWishlist(monumentId: String) {
    _wishlistedIds.update { if (monumentId in it) it - monumentId else it + monumentId }
  }
}
