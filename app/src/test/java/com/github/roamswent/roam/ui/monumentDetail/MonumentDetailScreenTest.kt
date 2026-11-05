package com.github.roamswent.roam.ui.monumentDetail

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.resources.C
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
class MonumentDetailScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val guide =
      MonumentGuide(
          monumentName = "Eiffel Tower",
          confidence = 0.92,
          history = "Built in 1889 as the entrance arch for the World's Fair.",
          facts = listOf("330 meters tall", "Repainted every seven years"),
          culture = "The globally recognized symbol of Paris and France.",
          language = "en",
      )

  @Test
  fun monumentDetailScreenDisplaysImageAndStructuredGuideCorrectly() {
    val falseImage = ColorPainter(Color.LightGray)

    composeTestRule.setContent {
      SampleAppTheme {
        MonumentDetailScreen(
            image = falseImage,
            guide = guide,
            onBackClick = {},
            isDarkTheme = false,
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_screen_container).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_picture).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_text).assertIsDisplayed()

    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_title)
        .assertIsDisplayed()
        .assertTextEquals(guide.monumentName)
    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_confidence)
        .assertIsDisplayed()
        .assertTextEquals("92% match")
    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_history)
        .assertIsDisplayed()
        .assertTextEquals(guide.history)
    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_culture)
        .assertIsDisplayed()
        .assertTextEquals(guide.culture)
    composeTestRule
        .onAllNodesWithTag(C.Tag.monument_detail_fact_item)
        .assertCountEquals(guide.facts.size)

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_drag_handle).performTouchInput {
      swipeUp()
      swipeDown()
    }
  }

  @Test
  fun backButtonInvokesCallback() {
    var backClicked = false
    composeTestRule.setContent {
      MonumentDetailScreen(
          image = ColorPainter(Color.LightGray),
          guide = guide,
          onBackClick = { backClicked = true },
          isDarkTheme = false,
      )
    }

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).performClick()

    assertTrue(backClicked)
  }

  @Test
  fun darkThemeUsesThemeTextColorsOnDarkPanelAndKeepsLightPanelUnchanged() {
    val image = ColorPainter(Color.LightGray)
    var isDarkTheme by mutableStateOf(false)
    var darkSurface = 0
    var darkOnSurface = 0
    var darkOnSurfaceVariant = 0

    composeTestRule.setContent {
      SampleAppTheme(darkTheme = isDarkTheme, dynamicColor = false) {
        if (isDarkTheme) {
          darkSurface = MaterialTheme.colorScheme.surface.toArgb()
          darkOnSurface = MaterialTheme.colorScheme.onSurface.toArgb()
          darkOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
        }
        MonumentDetailScreen(
            image = image,
            guide = guide,
            onBackClick = {},
            isDarkTheme = isDarkTheme,
        )
      }
    }

    val lightPanel =
        composeTestRule
            .onNodeWithTag(C.Tag.monument_detail_panel)
            .captureToImage()
            .asAndroidBitmap()
    assertEquals(ScreenBackground.toArgb(), lightPanel.getPixel(0, lightPanel.height / 2))

    composeTestRule.runOnIdle { isDarkTheme = true }

    val darkPanel =
        composeTestRule
            .onNodeWithTag(C.Tag.monument_detail_panel)
            .captureToImage()
            .asAndroidBitmap()
    assertEquals(darkSurface, darkPanel.getPixel(0, darkPanel.height / 2))
    assertNotEquals(Color.White.toArgb(), darkPanel.getPixel(0, darkPanel.height / 2))
    assertTrue(containsColor(darkPanel, darkOnSurface))
    assertTrue(containsColor(darkPanel, darkOnSurfaceVariant))
  }

  private fun containsColor(bitmap: Bitmap, color: Int): Boolean {
    for (y in 0 until bitmap.height) {
      for (x in 0 until bitmap.width) {
        if (bitmap.getPixel(x, y) == color) return true
      }
    }
    return false
  }
}
