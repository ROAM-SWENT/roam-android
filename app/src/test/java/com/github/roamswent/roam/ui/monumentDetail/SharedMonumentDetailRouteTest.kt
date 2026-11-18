// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.monumentDetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.roamswent.roam.model.guide.FakeMonumentSharingRepository
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.guide.MonumentSharingRepository
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedMonumentDetailRouteTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val louvre =
      Guide(
          id = "louvre",
          photoBytes = ByteArray(0),
          monumentName = "Louvre",
          confidence = 0.9,
          history = "A museum.",
          facts = emptyList(),
          culture = "French.",
      )

  @Test
  fun showsTheSharedGuide() {
    setRoute(FakeMonumentSharingRepository(mapOf("friend" to listOf(louvre))), "louvre")

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_title).assertTextEquals("Louvre")
  }

  @Test
  fun showsASpinnerWhileLoading() {
    val pending = CompletableDeferred<List<Guide>>()
    setRoute(
        object : MonumentSharingRepository by FakeMonumentSharingRepository() {
          override suspend fun getSharedGuidesFrom(ownerUid: String) = pending.await()
        },
        "louvre",
    )

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_title).assertDoesNotExist()

    pending.complete(listOf(louvre))

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_title).assertTextEquals("Louvre")
  }

  @Test
  fun showsNotFoundWhenTheGuideIsNotShared() {
    var backs = 0
    setRoute(FakeMonumentSharingRepository(), "louvre", onBack = { backs++ })

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).performClick()
    assertEquals(1, backs)
  }

  @Test
  fun showsNotFoundWhenLoadingFails() {
    setRoute(
        FakeMonumentSharingRepository().apply { loadError = IllegalStateException("boom") },
        "louvre",
    )

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).assertIsDisplayed()
  }

  private fun setRoute(
      repository: MonumentSharingRepository,
      guideId: String,
      onBack: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      SampleAppTheme {
        SharedMonumentDetailRoute(
            ownerUid = "friend",
            guideId = guideId,
            repository = repository,
            onBackClick = onBack,
        )
      }
    }
  }
}
