package com.github.roamswent.roam.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun homeScreenDisplaysScanEntry() {
    composeTestRule.setContent { SampleAppTheme { HomeScreen(onScanClicked = {}) } }

    composeTestRule.onNodeWithTag(C.Tag.home_screen_container).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.home_scan_label, useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun scanButtonInvokesCallbackOncePerClick() {
    var clicks = 0
    composeTestRule.setContent { SampleAppTheme { HomeScreen(onScanClicked = { clicks++ }) } }

    val button = composeTestRule.onNodeWithTag(C.Tag.home_scan_button)
    button.performClick()
    assertEquals(1, clicks)
    button.performClick()
    assertEquals(2, clicks)
  }

  @Test
  fun unavailableCameraDisablesScanEntryAndShowsUnavailableLabel() {
    composeTestRule.setContent {
      SampleAppTheme { HomeScreen(onScanClicked = {}, cameraAvailable = false) }
    }

    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).assertIsNotEnabled()
    composeTestRule
        .onNodeWithTag(C.Tag.home_scan_label, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals("No camera detected on this device")
  }
}
