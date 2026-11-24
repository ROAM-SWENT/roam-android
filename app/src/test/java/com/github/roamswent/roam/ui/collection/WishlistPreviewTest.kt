// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.github.roamswent.roam.resources.C
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WishlistPreviewTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun fullPreviewCoversAllRequestedFriendCounts() {
    assertEquals(listOf(1, 2, 3, 5), previewWishlistMonuments.map { it.scannedBy.size })
  }

  @Test
  fun emptyLightPreviewShowsWishlist() {
    composeTestRule.setContent { PreviewWishlistEmptyLight() }
    assertEmptyPreview()
  }

  @Test
  fun emptyDarkPreviewShowsWishlist() {
    composeTestRule.setContent { PreviewWishlistEmptyDark() }
    assertEmptyPreview()
  }

  @Test
  fun fullLightPreviewShowsWishlist() {
    composeTestRule.setContent { PreviewWishlistFullLight() }
    assertFullPreview()
  }

  @Test
  fun fullDarkPreviewShowsWishlist() {
    composeTestRule.setContent { PreviewWishlistFullDark() }
    assertFullPreview()
  }

  private fun assertEmptyPreview() {
    composeTestRule.onNodeWithTag(C.Tag.collection_filter_wishlist).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_empty).assertIsDisplayed()
  }

  private fun assertFullPreview() {
    composeTestRule.onNodeWithTag(C.Tag.collection_filter_wishlist).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_info).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.collection_wishlist_card)[0].assertIsDisplayed()
  }
}
