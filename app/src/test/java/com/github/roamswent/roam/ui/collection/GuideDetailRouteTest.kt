// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.collection

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
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GuideDetailRouteTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val eiffelTower =
      Guide(
          id = "eiffel",
          photoBytes = ByteArray(0),
          monumentName = "Eiffel Tower",
          confidence = 0.92,
          history = "Built in 1889.",
          facts = listOf("330 meters tall", "Repainted every seven years"),
          culture = "A symbol of Paris.",
          location = Coordinates(48.8584, 2.2945),
          // Noon UTC, so the formatted day is the same in any time zone the tests run in.
          discoveryDate = 1_791_806_400_000,
      )

  @Test
  fun showsTheFullSavedGuideWithDateAndLocation() {
    setRoute(FakeSavedGuideRepository(listOf(eiffelTower)), guideId = "eiffel")

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_screen_container).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_title).assertTextEquals("Eiffel Tower")
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_history).assertTextEquals("Built in 1889.")
    composeTestRule.onAllNodesWithTag(C.Tag.monument_detail_fact_item).assertCountEquals(2)
    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_culture)
        .assertTextEquals("A symbol of Paris.")
    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_date)
        .assertTextEquals("Discovered on 12 Oct 2026")
    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_location)
        .assertTextEquals("48.8584° N, 2.2945° E")
  }

  @Test
  fun omitsDateAndLocationWhenTheGuideHasNone() {
    val guide = eiffelTower.copy(location = null, discoveryDate = null)
    setRoute(FakeSavedGuideRepository(listOf(guide)), guideId = "eiffel")

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_title).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_date).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_location).assertDoesNotExist()
  }

  @Test
  fun showsTheGuideWhenItsPhotoCannotBeDecoded() {
    val guide = eiffelTower.copy(photoBytes = byteArrayOf(1, 2, 3))
    setRoute(FakeSavedGuideRepository(listOf(guide)), guideId = "eiffel")

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_picture).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_title).assertTextEquals("Eiffel Tower")
  }

  @Test
  fun showsNotFoundWhenTheGuideIsMissing() {
    setRoute(FakeSavedGuideRepository(listOf(eiffelTower)), guideId = "deleted")

    composeTestRule.onNodeWithTag(C.Tag.guide_detail_not_found).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_screen_container).assertDoesNotExist()
  }

  @Test
  fun showsErrorWhenTheGuideFailsToLoad() {
    val repository =
        FakeSavedGuideRepository(listOf(eiffelTower)).apply {
          loadError = IllegalStateException("offline")
        }
    setRoute(repository, guideId = "eiffel")

    composeTestRule.onNodeWithTag(C.Tag.guide_detail_error).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.guide_detail_not_found).assertDoesNotExist()
  }

  @Test
  fun retryLoadsTheGuideAgainAfterAnError() {
    val repository =
        FakeSavedGuideRepository(listOf(eiffelTower)).apply {
          loadError = IllegalStateException("offline")
        }
    setRoute(repository, guideId = "eiffel")
    composeTestRule.onNodeWithTag(C.Tag.guide_detail_error).assertIsDisplayed()

    repository.loadError = null
    composeTestRule.onNodeWithTag(C.Tag.guide_detail_retry).performClick()

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_title).assertTextEquals("Eiffel Tower")
    composeTestRule.onNodeWithTag(C.Tag.guide_detail_error).assertDoesNotExist()
  }

  @Test
  fun retryKeepsShowingTheErrorWhileTheGuideStillFailsToLoad() {
    val repository =
        FakeSavedGuideRepository(listOf(eiffelTower)).apply {
          loadError = IllegalStateException("offline")
        }
    setRoute(repository, guideId = "eiffel")

    composeTestRule.onNodeWithTag(C.Tag.guide_detail_retry).performClick()

    composeTestRule.onNodeWithTag(C.Tag.guide_detail_error).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_screen_container).assertDoesNotExist()
  }

  @Test
  fun noRetryButtonWhenTheGuideLoads() {
    setRoute(FakeSavedGuideRepository(listOf(eiffelTower)), guideId = "eiffel")

    composeTestRule.onNodeWithTag(C.Tag.guide_detail_retry).assertDoesNotExist()
  }

  @Test
  fun showsSpinnerWhileTheGuideLoads() {
    val repository = mockk<SavedGuideRepository>()
    coEvery { repository.getGuide("eiffel") } coAnswers { awaitCancellation() }
    setRoute(repository, guideId = "eiffel")

    composeTestRule.onNodeWithTag(C.Tag.guide_detail_loading).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.guide_detail_not_found).assertDoesNotExist()
  }

  @Test
  fun backButtonInvokesCallbackWhenTheGuideIsMissing() {
    var backClicks = 0
    composeTestRule.setContent {
      SampleAppTheme {
        GuideDetailRoute(
            guideId = "deleted",
            repository = FakeSavedGuideRepository(),
            onBackClick = { backClicks++ },
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).performClick()

    assertEquals(1, backClicks)
  }

  private fun setRoute(repository: SavedGuideRepository, guideId: String) {
    composeTestRule.setContent {
      SampleAppTheme {
        GuideDetailRoute(guideId = guideId, repository = repository, onBackClick = {})
      }
    }
  }
}
