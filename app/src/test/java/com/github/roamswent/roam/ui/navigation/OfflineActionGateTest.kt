/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.navigation

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OfflineActionGateTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun scanActionIsBlockedOfflineUntilDialogIsDismissed() {
    val proceedCount = mutableIntStateOf(0)
    composeTestRule.setContent {
      SampleAppTheme {
        val runAction = rememberOfflineActionGate(isOnline = false)
        Button(onClick = { runAction(OfflineAction.OPEN_SCAN) { proceedCount.intValue++ } }) {
          Text("Open scan")
        }
        Button(onClick = { runAction(OfflineAction.START_DISCOVERY) { proceedCount.intValue++ } }) {
          Text("Discover")
        }
      }
    }

    composeTestRule.onNodeWithText("Open scan").performClick()
    composeTestRule.onNodeWithTag(C.Tag.offline_action_dialog).assertIsDisplayed()
    composeTestRule
        .onNodeWithText("An internet connection is required to perform this action.")
        .assertIsDisplayed()
    assertEquals(0, proceedCount.intValue)

    composeTestRule.onNodeWithTag(C.Tag.offline_action_dialog_dismiss).performClick()

    composeTestRule.onAllNodesWithTag(C.Tag.offline_action_dialog).assertCountEquals(0)
    assertEquals(0, proceedCount.intValue)

    composeTestRule.onNodeWithText("Discover").performClick()
    composeTestRule.onNodeWithTag(C.Tag.offline_action_dialog).assertIsDisplayed()
    assertEquals(0, proceedCount.intValue)
  }

  @Test
  fun discoveryActionIsAllowedWhenOnline() {
    val proceedCount = mutableIntStateOf(0)
    composeTestRule.setContent {
      SampleAppTheme {
        val runAction = rememberOfflineActionGate(isOnline = true)
        Button(onClick = { runAction(OfflineAction.START_DISCOVERY) { proceedCount.intValue++ } }) {
          Text("Discover")
        }
      }
    }

    composeTestRule.onNodeWithText("Discover").performClick()

    assertEquals(1, proceedCount.intValue)
    composeTestRule.onAllNodesWithTag(C.Tag.offline_action_dialog).assertCountEquals(0)
  }

  @Test
  fun profileNavigationRemainsAvailableOffline() {
    val proceedCount = mutableIntStateOf(0)
    composeTestRule.setContent {
      SampleAppTheme {
        val runAction = rememberOfflineActionGate(isOnline = false)
        Button(onClick = { runAction(OfflineAction.OPEN_PROFILE) { proceedCount.intValue++ } }) {
          Text("Open profile")
        }
      }
    }

    composeTestRule.onNodeWithText("Open profile").performClick()

    assertEquals(1, proceedCount.intValue)
    composeTestRule.onAllNodesWithTag(C.Tag.offline_action_dialog).assertCountEquals(0)
  }
}
