package com.github.roamswent.roam

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.navigation.Routes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
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

  @Test
  fun backFromScanReturnsToHome() {
    waitFor(C.Tag.home_scan_button)
    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).performClick()

    waitFor(C.Tag.scan_root_stub)
    composeTestRule.onNodeWithTag(C.Tag.scan_root_stub).assertIsDisplayed()
    pressBack()
    waitFor(C.Tag.home_screen_container)
    composeTestRule.onNodeWithTag(C.Tag.home_screen_container).assertIsDisplayed()
  }

  @Test
  fun routesMatchNavigationContract() {
    org.junit.Assert.assertEquals("home", Routes.Home)
    org.junit.Assert.assertEquals("scan", Routes.Scan)
  }

  private fun waitFor(tag: String) {
    composeTestRule.waitUntil {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }
}
