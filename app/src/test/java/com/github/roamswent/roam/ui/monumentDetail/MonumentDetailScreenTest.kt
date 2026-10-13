package com.github.roamswent.roam.ui.monumentDetail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.resources.C
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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

    composeTestRule.setContent { MonumentDetailScreen(image = falseImage, guide = guide) }

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
}
