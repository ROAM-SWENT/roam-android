/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.roamswent.roam.resources.C
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun startsAtSignInBeforeHomeOrScan() {
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.sign_in_title))
        .assertIsDisplayed()
    composeTestRule.waitUntil {
      composeTestRule.onAllNodesWithTag(C.Tag.home_screen_container).fetchSemanticsNodes().isEmpty()
    }
    composeTestRule.waitUntil {
      composeTestRule.onAllNodesWithTag(C.Tag.scan_camera_screen).fetchSemanticsNodes().isEmpty()
    }
  }
}
