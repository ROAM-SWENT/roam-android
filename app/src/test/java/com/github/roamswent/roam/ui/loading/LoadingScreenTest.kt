package com.github.roamswent.roam.ui.loading

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.roamswent.roam.resources.C
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoadingScreenTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun displaysTheImageProgressAndDefaultCopy() {
    composeTestRule.setContent {
      LoadingScreen(image = ColorPainter(Color.LightGray), onCancel = {})
    }

    composeTestRule.onNodeWithTag(C.Tag.loading_screen_image).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.loading_screen_progress).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.loading_screen_title)
        .assertIsDisplayed()
        .assertTextEquals("Analyzing…")
    composeTestRule
        .onNodeWithTag(C.Tag.loading_screen_subtitle)
        .assertIsDisplayed()
        .assertTextEquals("Identifying your monument.")
    composeTestRule
        .onNodeWithTag(C.Tag.loading_screen_cancel_button)
        .assertIsDisplayed()
        .assertTextEquals("Cancel")
  }

  @Test
  fun displaysCustomCopyAndCallsCancel() {
    var cancelCount = 0
    composeTestRule.setContent {
      LoadingScreen(
          image = ColorPainter(Color.LightGray),
          onCancel = { cancelCount++ },
          title = "Finding a landmark",
          subtitle = "This can take a moment.",
      )
    }

    composeTestRule
        .onNodeWithTag(C.Tag.loading_screen_title)
        .assertIsDisplayed()
        .assertTextEquals("Finding a landmark")
    composeTestRule
        .onNodeWithTag(C.Tag.loading_screen_subtitle)
        .assertIsDisplayed()
        .assertTextEquals("This can take a moment.")
    composeTestRule.onNodeWithTag(C.Tag.loading_screen_cancel_button).performClick()

    assertEquals(1, cancelCount)
  }

  @Test
  fun displaysErrorMessageInPlaceOfSubtitle() {
    composeTestRule.activity.setContent {
      LoadingScreen(
          image = ColorPainter(Color.LightGray),
          onCancel = {},
          subtitle = "Identifying your monument.",
          errorMessage = "Gemini is unavailable",
      )
    }

    composeTestRule
        .onNodeWithTag(C.Tag.loading_screen_subtitle)
        .assertIsDisplayed()
        .assertTextEquals("Gemini is unavailable")
    composeTestRule.onNodeWithTag(C.Tag.loading_screen_error_icon).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.loading_screen_progress).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(C.Tag.loading_screen_title).assertCountEquals(0)
  }

  @Test
  fun systemBackInvokesCancel() {
    var cancelCount = 0
    composeTestRule.activity.setContent {
      LoadingScreen(
          image = ColorPainter(Color.LightGray),
          onCancel = { cancelCount++ },
      )
    }

    composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

    assertEquals(1, cancelCount)
  }
}
