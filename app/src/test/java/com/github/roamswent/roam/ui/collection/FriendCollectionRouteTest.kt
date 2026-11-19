// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.roamswent.roam.model.guide.FakeMonumentSharingRepository
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.guide.MonumentSharingRepository
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FriendCollectionRouteTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val louvre =
      Guide(
          id = "louvre",
          photoBytes = ByteArray(0),
          monumentName = "Louvre",
          confidence = 0.9,
          history = "",
          facts = emptyList(),
          culture = "",
      )

  @Test
  fun showsFriendNameAndSharedGuidesWithoutOwnerActions() {
    setRoute(FakeMonumentSharingRepository(mapOf("friend" to listOf(louvre))))

    composeTestRule.onNodeWithTag(C.Tag.collection_title).assertTextEquals("Alice's monuments")
    composeTestRule.onAllNodesWithTag(C.Tag.collection_card).assertCountEquals(1)
    composeTestRule.onNodeWithTag(C.Tag.collection_card_delete).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.collection_card_favorite).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.collection_filter_all).assertDoesNotExist()
  }

  @Test
  fun reportsClicksOnGuidesAndBack() {
    val clicked = mutableListOf<String>()
    var backs = 0
    setRoute(
        FakeMonumentSharingRepository(mapOf("friend" to listOf(louvre))),
        onBack = { backs++ },
        onGuideClick = clicked::add,
    )

    composeTestRule.onNodeWithTag(C.Tag.collection_card).performClick()
    composeTestRule.onNodeWithTag(C.Tag.collection_back_button).performClick()

    assertEquals(listOf("louvre"), clicked)
    assertEquals(1, backs)
  }

  @Test
  fun showsEmptyStateWhenNothingIsShared() {
    setRoute(FakeMonumentSharingRepository())

    composeTestRule.onNodeWithTag(C.Tag.collection_empty_state).assertIsDisplayed()
  }

  @Test
  fun showsErrorWhenLoadingFails() {
    setRoute(FakeMonumentSharingRepository().apply { loadError = IllegalStateException("boom") })

    composeTestRule.onNodeWithTag(C.Tag.collection_error).assertIsDisplayed()
  }

  @Test
  fun retryAfterInitialFailureReloadsTheCollection() {
    val repository = FakeMonumentSharingRepository(mapOf("friend" to listOf(louvre)))
    repository.loadError = IllegalStateException("offline")
    setRoute(repository)
    composeTestRule.onNodeWithTag(C.Tag.collection_error).assertIsDisplayed()

    composeTestRule.runOnIdle { repository.loadError = null }
    composeTestRule.onNodeWithTag(C.Tag.collection_retry).performClick()

    composeTestRule.onNodeWithTag(C.Tag.collection_card).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_error).assertDoesNotExist()
  }

  @Test
  fun showsErrorAndRetryAlongsidePreviouslyLoadedGuides() {
    val repository = mockk<MonumentSharingRepository>()
    var attempts = 0
    every { repository.observeSharedGuidesFrom("friend") } answers
        {
          attempts++
          if (attempts == 1) {
            flow {
              emit(listOf(louvre))
              throw IllegalStateException("offline")
            }
          } else flowOf(listOf(louvre))
        }
    setRoute(repository)

    composeTestRule.onNodeWithTag(C.Tag.collection_card).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_retry_error).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_retry).performClick()
    composeTestRule.onNodeWithTag(C.Tag.collection_retry_error).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.collection_card).assertIsDisplayed()
    assertEquals(2, attempts)
  }

  @Test
  fun searchFiltersTheSharedGuides() {
    setRoute(FakeMonumentSharingRepository(mapOf("friend" to listOf(louvre))))

    composeTestRule.onNodeWithTag(C.Tag.collection_search_bar).assertIsDisplayed()
  }

  private fun setRoute(
      repository: MonumentSharingRepository,
      onBack: () -> Unit = {},
      onGuideClick: (String) -> Unit = {},
  ) {
    composeTestRule.setContent {
      SampleAppTheme {
        FriendCollectionRoute(
            ownerUid = "friend",
            ownerName = "Alice",
            repository = repository,
            onBackClick = onBack,
            onGuideClick = onGuideClick,
        )
      }
    }
  }
}
