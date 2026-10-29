// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.collection

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.github.roamswent.roam.model.guide.PlaceholderSavedGuideRepository
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollectionRouteTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun showsPlaceholderGuidesByDefault() {
    composeTestRule.setContent { SampleAppTheme { CollectionRoute() } }

    composeTestRule.onNodeWithTag(C.Tag.collection_grid).assertIsDisplayed()
    composeTestRule
        .onAllNodesWithTag(C.Tag.collection_card_name, useUnmergedTree = true)[0]
        .assertTextEquals("Eiffel Tower")
    composeTestRule
        .onAllNodesWithTag(C.Tag.collection_card_photo_placeholder, useUnmergedTree = true)[0]
        .assertIsDisplayed()
  }

  @Test
  fun showsEmptyStateWhenRepositoryHasNoGuides() {
    composeTestRule.setContent {
      SampleAppTheme { CollectionRoute(repository = PlaceholderSavedGuideRepository(emptyList())) }
    }

    composeTestRule.onNodeWithTag(C.Tag.collection_empty_state).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_grid).assertDoesNotExist()
  }
}
