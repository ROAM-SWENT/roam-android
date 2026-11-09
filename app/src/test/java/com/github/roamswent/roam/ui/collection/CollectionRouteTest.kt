// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.github.roamswent.roam.model.guide.FakeSavedGuideRepository
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollectionRouteTest {
  @get:Rule val composeTestRule = createComposeRule()

  // Noon UTC, so the formatted day is the same in any time zone the tests run in.
  private val colosseum = guide("colosseum", "Colosseum", discoveryDate = 1_791_720_000_000)
  private val eiffelTower = guide("eiffel", "Eiffel Tower", discoveryDate = 1_791_806_400_000)

  @Test
  fun showsGuidesFromRepositoryNewestFirst() {
    setRoute(FakeSavedGuideRepository(listOf(colosseum, eiffelTower)))

    composeTestRule.onNodeWithTag(C.Tag.collection_grid).assertIsDisplayed()
    val names =
        composeTestRule.onAllNodesWithTag(C.Tag.collection_card_name, useUnmergedTree = true)
    names[0].assertTextEquals("Eiffel Tower")
    names[1].assertTextEquals("Colosseum")
    val dates =
        composeTestRule.onAllNodesWithTag(C.Tag.collection_card_date, useUnmergedTree = true)
    dates[0].assertTextEquals("12 Oct 2026")
    dates[1].assertTextEquals("11 Oct 2026")
  }

  @Test
  fun showsGuidesAddedAfterTheFirstLoad() {
    val repository = FakeSavedGuideRepository(listOf(colosseum))
    setRoute(repository)
    composeTestRule.onAllNodesWithTag(C.Tag.collection_card).assertCountEquals(1)

    repository.guides.value = listOf(colosseum, eiffelTower)

    composeTestRule.onAllNodesWithTag(C.Tag.collection_card).assertCountEquals(2)
  }

  @Test
  fun showsEmptyStateWhenRepositoryHasNoGuides() {
    setRoute(FakeSavedGuideRepository())

    composeTestRule.onNodeWithTag(C.Tag.collection_empty_state).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_grid).assertDoesNotExist()
  }

  @Test
  fun showsEmptyStateOnceTheLastGuideIsRemoved() {
    val repository = FakeSavedGuideRepository(listOf(colosseum))
    setRoute(repository)
    composeTestRule.onNodeWithTag(C.Tag.collection_grid).assertIsDisplayed()

    repository.guides.value = emptyList()

    composeTestRule.onNodeWithTag(C.Tag.collection_empty_state).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_grid).assertDoesNotExist()
  }

  @Test
  fun showsErrorWhenRepositoryFails() {
    setRoute(FakeSavedGuideRepository().apply { loadError = IllegalStateException("offline") })

    composeTestRule.onNodeWithTag(C.Tag.collection_error).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_empty_state).assertDoesNotExist()
  }

  private fun setRoute(repository: FakeSavedGuideRepository) {
    composeTestRule.setContent { SampleAppTheme { CollectionRoute(repository = repository) } }
  }

  private fun guide(id: String, name: String, discoveryDate: Long) =
      Guide(
          id = id,
          photoBytes = ByteArray(0),
          monumentName = name,
          confidence = 0.9,
          history = "History",
          facts = emptyList(),
          culture = "Culture",
          discoveryDate = discoveryDate,
      )
}
