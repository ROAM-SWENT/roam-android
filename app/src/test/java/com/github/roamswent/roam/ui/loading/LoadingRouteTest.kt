// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.ui.loading

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.roamswent.roam.model.ai.ImagePayload
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.ai.MonumentGuideRepository
import com.github.roamswent.roam.resources.C
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoadingRouteTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val uri = Uri.parse("content://capture/photo.jpg")

  @Test
  fun successfulDiscoveryPassesGuideAndPhotoToHost() {
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

    setContent(
        repository,
        onSuccess = { returnedGuide, returnedUri -> result = returnedGuide to returnedUri },
    )

    composeTestRule.waitUntil { result != null }

    assertEquals(guide to uri, result)
  }

  @Test
  fun discoveryFailureDisplaysErrorAndStaysOnLoadingScreen() {
    val repository = mockk<MonumentGuideRepository>()
    coEvery { repository.guideFor(any()) } throws IllegalStateException("Gemini is unavailable")

    setContent(repository)

    composeTestRule.waitUntil {
      composeTestRule.onAllNodesWithTag(C.Tag.loading_screen_subtitle).fetchSemanticsNodes().any {
          node ->
        SemanticsProperties.Text in node.config &&
            node.config[SemanticsProperties.Text].any { it.text == "Something went wrong." }
      }
    }
    composeTestRule
        .onNodeWithTag(C.Tag.loading_screen_subtitle)
        .assertTextEquals("Something went wrong.")
    composeTestRule.onNodeWithTag(C.Tag.loading_screen_title).assertTextEquals("Analyzing…")
  }

  @Test
  fun cancelRequestsReturnHomeAndStopsDiscovery() {
    val repository = mockk<MonumentGuideRepository>()
    var discoveryStarted = false
    coEvery { repository.guideFor(any()) } coAnswers
        {
          discoveryStarted = true
          awaitCancellation()
        }
    var cancelCount = 0

    setContent(repository, onCancel = { cancelCount++ })
    composeTestRule.waitUntil { discoveryStarted }
    composeTestRule.onNodeWithTag(C.Tag.loading_screen_cancel_button).performClick()
    composeTestRule.waitUntil { cancelCount == 1 }

    assertEquals(1, cancelCount)
    coVerify(exactly = 1) { repository.guideFor(any()) }
  }

  private fun setContent(
      repository: MonumentGuideRepository,
      onCancel: () -> Unit = {},
      onSuccess: (MonumentGuide, Uri) -> Unit = { _, _ -> },
  ) {
    composeTestRule.activity.setContent {
      LoadingRoute(
          imageUri = uri,
          onCancel = onCancel,
          onDiscoverySuccess = onSuccess,
          repository = repository,
          readImage = { ImagePayload(byteArrayOf(1), "image/jpeg") },
      )
    }
  }
}
