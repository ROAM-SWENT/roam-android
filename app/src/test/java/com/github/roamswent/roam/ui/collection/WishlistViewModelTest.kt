// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WishlistViewModelTest {
  private val viewModel = WishlistViewModel()

  @Test
  fun setWishlistedKeepsTheItemInTheList() {
    viewModel.showMonuments(listOf(WishlistMonument("a", "A"), WishlistMonument("b", "B")))
    viewModel.setWishlisted("a", false)
    val monuments = viewModel.uiState.value.monuments
    assertEquals(listOf("a", "b"), monuments.map { it.id })
    assertFalse(monuments[0].isWishlisted)
    assertTrue(monuments[1].isWishlisted)
    viewModel.setWishlisted("a", true)
    assertTrue(viewModel.uiState.value.monuments[0].isWishlisted)
  }

  @Test
  fun monumentsAreSortedByNameAndBumpTheRefreshVersion() {
    viewModel.showMonuments(listOf(WishlistMonument("z", "zebra"), WishlistMonument("a", "Apple")))
    assertEquals(listOf("a", "z"), viewModel.uiState.value.monuments.map { it.id })
    assertEquals(1, viewModel.uiState.value.refreshVersion)
  }

  @Test
  fun loadingAndErrorStates() {
    viewModel.showLoading()
    assertTrue(viewModel.uiState.value.isLoading)
    viewModel.showError(Exception("boom"))
    assertFalse(viewModel.uiState.value.isLoading)
    assertNotNull(viewModel.uiState.value.error)
    viewModel.showLoading()
    assertNull(viewModel.uiState.value.error)
  }
}
