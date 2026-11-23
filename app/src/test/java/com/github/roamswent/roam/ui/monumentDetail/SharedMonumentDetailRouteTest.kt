// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.monumentDetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.roamswent.roam.model.guide.FakeMonumentSharingRepository
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.guide.MonumentShareSettings
import com.github.roamswent.roam.model.guide.MonumentSharingRepository
import com.github.roamswent.roam.model.guide.ShareAudience
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
          shareSettings = MonumentShareSettings(audience = ShareAudience.ALL_FRIENDS),
      )

  @Test
  fun showsTheSharedGuide() {
    setRoute(FakeMonumentSharingRepository(mapOf("friend" to listOf(louvre))), "louvre")

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_title).assertTextEquals("Louvre")
  }

  @Test
  fun showsASpinnerWhileLoading() {
    val pending = CompletableDeferred<Guide?>()
    setRoute(
        object : MonumentSharingRepository by FakeMonumentSharingRepository() {
          override suspend fun getSharedGuideFrom(ownerUid: String, guideId: String) =
              pending.await()
        },
        "louvre",
    )

    composeTestRule.onNodeWithTag(C.Tag.shared_monument_detail_loading).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_title).assertDoesNotExist()

    pending.complete(louvre)

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_title).assertTextEquals("Louvre")
  }

  @Test
  fun showsNotFoundWhenTheGuideIsNotShared() {
    var backs = 0
    setRoute(FakeMonumentSharingRepository(), "louvre", onBack = { backs++ })

    composeTestRule.onNodeWithTag(C.Tag.shared_monument_detail_not_found).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.shared_monument_detail_retry).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).performClick()
    assertEquals(1, backs)
  }

  @Test
  fun showsErrorAndRetriesWhenLoadingFails() {
    val repository = FakeMonumentSharingRepository(mapOf("friend" to listOf(louvre)))
    repository.loadError = IllegalStateException("boom")
    setRoute(repository, "louvre")

    composeTestRule.onNodeWithTag(C.Tag.shared_monument_detail_error).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.shared_monument_detail_not_found).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).assertIsDisplayed()
    composeTestRule.runOnIdle { repository.loadError = null }
    composeTestRule.onNodeWithTag(C.Tag.shared_monument_detail_retry).performClick()
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_title).assertTextEquals("Louvre")
  }

  @Test
  fun loadsOnlyTheRequestedGuide() {
    val requested = mutableListOf<Pair<String, String>>()
    setRoute(
        object : MonumentSharingRepository by FakeMonumentSharingRepository() {
          override suspend fun getSharedGuideFrom(ownerUid: String, guideId: String): Guide? {
            requested.add(ownerUid to guideId)
            return louvre
          }

          override suspend fun getSharedGuidesFrom(ownerUid: String): List<Guide> =
              error("The entire collection must not be loaded")
        },
        "louvre",
    )

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_title).assertTextEquals("Louvre")
    assertEquals(listOf("friend" to "louvre"), requested)
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
