package com.github.roamswent.roam

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.home.HomeScreen
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun homeScreenShowsScanEntry() {
    composeTestRule.setContent { SampleAppTheme { HomeScreen(onScanClicked = {}) } }

    composeTestRule.onNodeWithTag(C.Tag.home_screen_container).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.home_scan_label, useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun scanButtonIsEnabledAndClickable() {
    composeTestRule.setContent { SampleAppTheme { HomeScreen(onScanClicked = {}) } }

    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).assertIsEnabled().assertHasClickAction()
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
}
