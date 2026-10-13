// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.loading

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.roamswent.roam.model.ai.ImagePayload
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.ai.MonumentGuideRepository
import com.github.roamswent.roam.resources.C
import io.mockk.coEvery
import io.mockk.mockk
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
      AnalyzingScreen(image = ColorPainter(Color.LightGray), onCancel = {})
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
    composeTestRule.onNodeWithTag(C.Tag.loading_screen_cancel_button).assertIsDisplayed()
  }

  @Test
  fun displaysCustomCopyAndCallsCancel() {
    var cancelCount = 0
    composeTestRule.setContent {
      AnalyzingScreen(
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
  fun discoveryErrorIsDisplayedAndReportedToHost() {
    val uri = Uri.parse("content://capture/photo.jpg")
    val repository = mockk<MonumentGuideRepository>()
    coEvery { repository.guideFor(any()) } throws IllegalStateException("Gemini is unavailable")
    var reportedError: String? = null

    composeTestRule.activity.setContent {
      LoadingRoute(
          imageUri = uri,
          onCancel = {},
          onDiscoverySuccess = { _, _ -> },
          onDiscoveryError = { reportedError = it },
          repository = repository,
          readImage = { ImagePayload(byteArrayOf(1), "image/jpeg") },
      )
    }

    composeTestRule.waitUntil { reportedError != null }
    composeTestRule
        .onNodeWithTag(C.Tag.loading_screen_subtitle)
        .assertIsDisplayed()
        .assertTextEquals("Gemini is unavailable")
  }

  @Test
  fun successfulDiscoveryPassesGuideAndPhotoToHost() {
    val uri = Uri.parse("content://capture/photo.jpg")
    val repository = mockk<MonumentGuideRepository>()
    val guide =
        MonumentGuide(
            monumentName = "The Colosseum",
            confidence = 0.98,
            history = "Built in ancient Rome.",
            facts = listOf("It hosted gladiatorial contests."),
            culture = "An enduring symbol of Rome.",
            language = "en",
        )
    coEvery { repository.guideFor(any()) } returns guide
    var result: Pair<MonumentGuide, Uri>? = null

    composeTestRule.activity.setContent {
      LoadingRoute(
          imageUri = uri,
          onCancel = {},
          onDiscoverySuccess = { returnedGuide, returnedUri ->
            result = returnedGuide to returnedUri
          },
          onDiscoveryError = {},
          repository = repository,
          readImage = { ImagePayload(byteArrayOf(1), "image/jpeg") },
      )
    }

    composeTestRule.waitUntil { result != null }
    assertEquals(guide to uri, result)
  }
}
