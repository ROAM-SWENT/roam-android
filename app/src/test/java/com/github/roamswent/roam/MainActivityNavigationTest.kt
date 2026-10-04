package com.github.roamswent.roam

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.roamswent.roam.resources.C
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityNavigationTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun startsAtHome() {
    waitFor(C.Tag.home_screen_container)
    composeTestRule.onNodeWithTag(C.Tag.home_screen_container).assertIsDisplayed()
  }

  @Test
  fun scanEntryNavigatesToScanDestination() {
    waitFor(C.Tag.home_scan_button)
    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).performClick()

    waitFor(C.Tag.scan_root_stub)
    composeTestRule.onNodeWithTag(C.Tag.scan_root_stub).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.home_screen_container).assertCountEquals(0)
  }

  private fun waitFor(tag: String) {
    composeTestRule.waitUntil {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }
}
