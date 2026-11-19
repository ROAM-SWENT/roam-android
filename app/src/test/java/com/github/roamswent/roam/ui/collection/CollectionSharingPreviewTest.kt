// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.github.roamswent.roam.resources.C
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollectionSharingPreviewTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun selectionPreviewShowsPreselectedFriend() {
    composeTestRule.setContent { PreviewCollectionSharingSelection() }

    composeTestRule.onAllNodesWithTag(C.Tag.collection_share_friend).assertCountEquals(3)
    composeTestRule.onAllNodesWithTag(C.Tag.collection_share_friend)[0].assertIsOn()
    composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
  }

  @Test
  fun loadingPreviewDisablesSaving() {
    composeTestRule.setContent { PreviewCollectionSharingLoading() }

    composeTestRule.onNodeWithTag(C.Tag.collection_share_dialog).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_share_confirm).assertIsNotEnabled()
  }

  @Test
  fun errorPreviewShowsRetryAndDisablesSaving() {
    composeTestRule.setContent { PreviewCollectionSharingFriendsError() }

    composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_share_confirm).assertIsNotEnabled()
  }

  @Test
  fun emptyPreviewExplainsWhyNoFriendsCanBeSelected() {
    composeTestRule.setContent { PreviewCollectionSharingNoFriends() }

    composeTestRule.onNodeWithText("Add friends before sharing a guide.").assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.collection_share_friend).assertCountEquals(0)
  }

  @Test
  fun savedPreviewShowsTheNumberOfRecipients() {
    composeTestRule.setContent { PreviewCollectionSharingSaved() }

    composeTestRule
        .onNodeWithTag(C.Tag.collection_share_status, true)
        .assertTextEquals("Shared with 2 friends")
  }
}
