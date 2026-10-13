// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.monumentDetail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.SavedStateHandle
import com.github.roamswent.roam.resources.C
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MonumentDetailScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val monument = Monument("id-1", "Monument", "image.jpg", "Monument description")

  @Test
  fun monumentDetailScreenDisplaysImageAndTextAndHandlesDragging() {
    composeTestRule.setContent {
      MonumentDetailScreen(
          image = ColorPainter(Color.LightGray),
          text = monument.description,
      )
    }

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_screen_container).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_picture).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_text).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_text)
        .assertIsDisplayed()
        .assertTextEquals(monument.description)
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_drag_handle).performTouchInput {
      swipeUp()
      swipeDown()
      swipeUp()
    }
  }

  @Test
  fun standaloneScreenBackButtonCallsCallback() {
    var backClicks = 0
    composeTestRule.setContent {
      MonumentDetailScreen(
          image = ColorPainter(Color.LightGray),
          text = monument.description,
          onBackClick = { backClicks++ },
      )
    }

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).performClick()

    assertEquals(1, backClicks)
  }

  @Test
  fun loadingRouteShowsProgressAndBackNavigatesHome() {
    val pendingMonument = CompletableDeferred<Monument>()
    val repository = mockk<MonumentDetailRepository>()
    coEvery { repository.getMonument("id-1") } coAnswers { pendingMonument.await() }
    var homeNavigations = 0
    val viewModel =
        MonumentDetailViewModel(SavedStateHandle(mapOf("monumentId" to "id-1")), repository)

    composeTestRule.setContent {
      MonumentDetailRoute(viewModel = viewModel, onNavigateHome = { homeNavigations++ })
    }

    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_back_button)
        .assertIsDisplayed()
        .performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, homeNavigations)
    pendingMonument.cancel()
  }

  @Test
  fun errorRouteCanRetryAndSuccessBackNavigatesHome() {
    val repository = mockk<MonumentDetailRepository>()
    var calls = 0
    coEvery { repository.getMonument("id-1") } coAnswers
        {
          if (calls++ == 0) error("Unable to load monument")
          monument
        }
    var homeNavigations = 0
    val viewModel =
        MonumentDetailViewModel(SavedStateHandle(mapOf("monumentId" to "id-1")), repository)

    composeTestRule.setContent {
      MonumentDetailRoute(viewModel = viewModel, onNavigateHome = { homeNavigations++ })
    }
    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_error_message)
        .assertIsDisplayed()
        .assertTextEquals("Unable to load monument")
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_retry_button).performClick()
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_text)
        .assertIsDisplayed()
        .assertTextEquals(monument.description)
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, homeNavigations)
  }
}
