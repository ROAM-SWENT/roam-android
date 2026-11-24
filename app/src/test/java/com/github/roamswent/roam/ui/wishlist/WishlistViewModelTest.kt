// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.wishlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WishlistViewModelTest {
  @Test
  fun startsEmpty() {
    assertTrue(WishlistViewModel().wishlistedIds.value.isEmpty())
  }

  @Test
  fun toggleAddsThenRemoves() {
    val viewModel = WishlistViewModel()
    viewModel.toggleWishlist("g1")
    viewModel.toggleWishlist("g2")
    assertEquals(setOf("g1", "g2"), viewModel.wishlistedIds.value)
    viewModel.toggleWishlist("g1")
    assertEquals(setOf("g2"), viewModel.wishlistedIds.value)
  }
}
