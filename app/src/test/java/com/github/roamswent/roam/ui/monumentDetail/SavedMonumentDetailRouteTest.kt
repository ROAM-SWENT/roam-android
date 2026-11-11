// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.monumentDetail

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.roamswent.roam.model.guide.FakeSavedGuideRepository
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.guide.SavedGuideRepository
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SavedMonumentDetailRouteTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val eiffelTower =
      Guide(
          id = "eiffel",
          photoBytes = ByteArray(0),
          monumentName = "Eiffel Tower",
          confidence = 0.92,
          history = "Built in 1889.",
          facts = listOf("330 meters tall.", "Repainted every seven years."),
          culture = "A symbol of Paris.",
      )

  @Test
  fun showsTheSavedGuideContent() {
    setRoute(FakeSavedGuideRepository(listOf(eiffelTower)), guideId = "eiffel")

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_screen_container).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_title).assertTextEquals("Eiffel Tower")
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_confidence).assertTextEquals("92% match")
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_history).assertTextEquals("Built in 1889.")
    composeTestRule.onAllNodesWithTag(C.Tag.monument_detail_fact_item).assertCountEquals(2)
    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_culture)
        .assertTextEquals("A symbol of Paris.")
  }

  @Test
  fun showsASpinnerWhileTheGuideLoads() {
    val pending = CompletableDeferred<Guide?>()
    setRoute(
        object : SavedGuideRepository by FakeSavedGuideRepository() {
          override suspend fun getGuide(guideId: String): Guide? = pending.await()
        },
        guideId = "eiffel",
    )

    composeTestRule.onNodeWithTag(C.Tag.saved_monument_detail_loading).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.saved_monument_detail_not_found).assertDoesNotExist()

    pending.complete(eiffelTower)

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_title).assertTextEquals("Eiffel Tower")
  }

  @Test
  fun showsNotFoundWhenTheGuideNoLongerExists() {
    setRoute(FakeSavedGuideRepository(listOf(eiffelTower)), guideId = "deleted")

    composeTestRule
        .onNodeWithTag(C.Tag.saved_monument_detail_not_found)
        .assertTextEquals("This monument is no longer in your collection.")
    composeTestRule.onNodeWithTag(C.Tag.saved_monument_detail_retry).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).assertIsDisplayed()
  }

  @Test
  fun retryAfterALoadFailureShowsTheGuide() {
    val repository =
        FakeSavedGuideRepository(listOf(eiffelTower)).apply {
          loadError = IllegalStateException("offline")
        }
    setRoute(repository, guideId = "eiffel")

    composeTestRule
        .onNodeWithTag(C.Tag.saved_monument_detail_error)
        .assertTextEquals("Couldn’t load this monument. Please try again.")

    repository.loadError = null
    composeTestRule.onNodeWithTag(C.Tag.saved_monument_detail_retry).performClick()

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_title).assertTextEquals("Eiffel Tower")
  }

  @Test
  fun backButtonCallsOnBackClick() {
    var backClicks = 0
    setRoute(
        FakeSavedGuideRepository(listOf(eiffelTower)),
        guideId = "eiffel",
        onBackClick = { backClicks++ },
    )

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).performClick()

    assertEquals(1, backClicks)
  }

  @Test
  fun backButtonOnTheErrorMessageCallsOnBackClick() {
    var backClicks = 0
    setRoute(FakeSavedGuideRepository(), guideId = "missing", onBackClick = { backClicks++ })

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).performClick()

    assertEquals(1, backClicks)
  }

  private fun setRoute(
      repository: SavedGuideRepository,
      guideId: String,
      onBackClick: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      SampleAppTheme {
        SavedMonumentDetailRoute(
            guideId = guideId,
            repository = repository,
            onBackClick = onBackClick,
        )
      }
    }
  }
}
