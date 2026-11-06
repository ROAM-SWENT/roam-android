/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.components.bottomdock.DockDestination
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import com.github.roamswent.roam.ui.theme.ScreenBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun homeScreenDisplaysScanEntry() {
    composeTestRule.setContent {
      SampleAppTheme {
        HomeScreen(onScanClicked = {}, isConnectedToInternet = true, isDarkTheme = false)
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.home_screen_container).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dock_scan_button).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dock_scan_label).assertIsDisplayed()
  }

  @Test
  fun scanButtonInvokesCallbackOncePerClick() {
    var clicks = 0
    composeTestRule.setContent {
      SampleAppTheme { HomeScreen(onScanClicked = { clicks++ }, isDarkTheme = false) }
    }

    val button = composeTestRule.onNodeWithTag(C.Tag.dock_scan_button)
    button.performClick()
    assertEquals(1, clicks)
    button.performClick()
    assertEquals(2, clicks)
  }

  @Test
  fun unavailableCameraDisablesScanEntryAndShowsUnavailableLabel() {
    composeTestRule.setContent {
      SampleAppTheme {
        HomeScreen(onScanClicked = {}, cameraAvailable = false, isDarkTheme = false)
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.dock_scan_button).assertIsNotEnabled()
    composeTestRule
        .onNodeWithTag(C.Tag.dock_scan_label)
        .assertIsDisplayed()
        .assertTextEquals("No camera detected on this device")
  }

  @Test
  fun homeScreenShowsDock() {
    composeTestRule.setContent {
      SampleAppTheme { HomeScreen(onScanClicked = {}, isDarkTheme = false) }
    }

    composeTestRule.onNodeWithTag(C.Tag.dock_container).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).assertIsDisplayed()
  }

  @Test
  fun homeScreenShowsGreetingWithUserName() {
    composeTestRule.setContent {
      SampleAppTheme {
        HomeScreen(onScanClicked = {}, userName = "Ada Lovelace", isDarkTheme = false)
      }
    }

    composeTestRule
        .onNodeWithTag(C.Tag.home_greeting_subtitle, useUnmergedTree = true)
        .assertTextEquals("Hello Ada Lovelace,")
  }

  @Test
  fun profileBubbleShowsInitialAndOpensProfileDestination() {
    var selectedDestination: DockDestination? = null
    composeTestRule.setContent {
      SampleAppTheme {
        HomeScreen(
            onScanClicked = {},
            userName = "Ada Lovelace",
            onDockDestinationSelected = { selectedDestination = it },
            isDarkTheme = false,
        )
      }
    }

    val profileBubble = composeTestRule.onNodeWithTag(C.Tag.home_profile_button)
    profileBubble.assertIsDisplayed().assertTextEquals("A")
    profileBubble.performClick()

    assertEquals(DockDestination.SETTINGS, selectedDestination)
  }

  @Test
  fun homeScreenShowsSignedOutGreeting() {
    composeTestRule.setContent {
      SampleAppTheme {
        HomeScreen(
            onScanClicked = {},
            userName = "",
            isConnectedToInternet = true,
            isDarkTheme = false,
        )
      }
    }

    composeTestRule
        .onNodeWithTag(C.Tag.home_greeting_subtitle, useUnmergedTree = true)
        .assertTextEquals("Hello,")
  }

  @Test
  fun homeScreenShowsOfflineNoticeWhenInternetIsUnavailable() {
    composeTestRule.setContent {
      SampleAppTheme {
        HomeScreen(onScanClicked = {}, isConnectedToInternet = false, isDarkTheme = false)
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.home_offline_card).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.home_offline_card_message, useUnmergedTree = true)
        .assertTextEquals("You are not connected to the internet.")
    composeTestRule
        .onNodeWithTag(C.Tag.home_offline_card_icon, useUnmergedTree = true)
        .assertIsDisplayed()
  }

  @Test
  fun homeScreenHidesOfflineNoticeWhenInternetIsAvailable() {
    composeTestRule.setContent {
      SampleAppTheme {
        HomeScreen(onScanClicked = {}, isConnectedToInternet = true, isDarkTheme = false)
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.home_offline_card).assertDoesNotExist()
  }

  @Test
  fun darkThemeUsesDarkSurfaceAndReadableContentForOfflineNotice() {
    var expectedCardColor = 0
    var expectedContentColor = 0
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = true, dynamicColor = false) {
        expectedCardColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
        expectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
        HomeScreen(
            onScanClicked = {},
            isConnectedToInternet = false,
            isDarkTheme = true,
        )
      }
    }

    val card =
        composeTestRule.onNodeWithTag(C.Tag.home_offline_card).captureToImage().asAndroidBitmap()
    val message =
        composeTestRule
            .onNodeWithTag(C.Tag.home_offline_card_message, useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()
    val icon =
        composeTestRule
            .onNodeWithTag(C.Tag.home_offline_card_icon, useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()

    assertEquals(expectedCardColor, card.getPixel(0, 0))
    assertNotEquals(Color(0xFFFFE9E6).toArgb(), card.getPixel(0, 0))
    assertTrue(containsColor(message, expectedContentColor))
    assertTrue(containsColor(icon, expectedContentColor))
  }

  @Test
  fun darkThemeUsesThemeBackground() {
    var expectedBackground = 0

    composeTestRule.setContent {
      SampleAppTheme(darkTheme = true, dynamicColor = false) {
        expectedBackground = MaterialTheme.colorScheme.background.toArgb()
        HomeScreen(
            onScanClicked = {},
            isConnectedToInternet = true,
            isDarkTheme = true,
        )
      }
    }

    val screen =
        composeTestRule
            .onNodeWithTag(C.Tag.home_screen_container)
            .captureToImage()
            .asAndroidBitmap()

    assertEquals(expectedBackground, screen.getPixel(0, 0))
    assertNotEquals(ScreenBackground.toArgb(), screen.getPixel(0, 0))
  }

  private fun containsColor(bitmap: android.graphics.Bitmap, color: Int): Boolean {
    for (y in 0 until bitmap.height) {
      for (x in 0 until bitmap.width) {
        if (bitmap.getPixel(x, y) == color) return true
      }
    }
    return false
  }
}
