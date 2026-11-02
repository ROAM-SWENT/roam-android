// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.collection

import android.graphics.Bitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.intercept.Interceptor
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.guide.SavedGuidesUiState
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CollectionScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  // Noon UTC, so the formatted day is the same in any time zone the tests run in.
  private val olderGuide = guide("older", "Colosseum", discoveryDate = 1_791_720_000_000)
  private val newerGuide = guide("newer", "Eiffel Tower", discoveryDate = 1_791_806_400_000)

  @After
  fun resetImageLoader() {
    Coil.reset()
  }

  @Test
  fun showsOneCardPerGuideWithNamePlaceholderPhotoAndDate() {
    setScreen(SavedGuidesUiState(guides = listOf(olderGuide, newerGuide)))

    composeTestRule.onNodeWithTag(C.Tag.collection_title).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_grid).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.collection_card).assertCountEquals(2)
    composeTestRule
        .onAllNodesWithTag(C.Tag.collection_card_photo_placeholder, useUnmergedTree = true)
        .assertCountEquals(2)
    composeTestRule
        .onAllNodesWithTag(C.Tag.collection_card_date, useUnmergedTree = true)
        .assertCountEquals(2)
    composeTestRule.onNodeWithTag(C.Tag.collection_empty_state).assertDoesNotExist()
  }

  @Test
  fun showsNewestGuideFirst() {
    setScreen(SavedGuidesUiState(guides = listOf(olderGuide, newerGuide)))

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
  fun hidesDateWhenDiscoveryDateIsUnknown() {
    setScreen(SavedGuidesUiState(guides = listOf(guide("no-date", "Big Ben", null))))

    composeTestRule
        .onAllNodesWithTag(C.Tag.collection_card_name, useUnmergedTree = true)
        .assertCountEquals(1)
    composeTestRule
        .onNodeWithTag(C.Tag.collection_card_date, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun clickingACardReportsItsGuideId() {
    val clicked = mutableListOf<String>()
    setScreen(
        SavedGuidesUiState(guides = listOf(olderGuide, newerGuide)),
        onGuideClick = clicked::add,
    )

    composeTestRule.onAllNodesWithTag(C.Tag.collection_card)[1].performClick()

    assertEquals(listOf("older"), clicked)
  }

  @Test
  fun showsEmptyStateWhenCollectionIsEmpty() {
    setScreen(SavedGuidesUiState())

    composeTestRule.onNodeWithTag(C.Tag.collection_empty_state).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_grid).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.collection_loading).assertDoesNotExist()
  }

  @Test
  fun showsLoadingIndicatorBeforeFirstGuidesArrive() {
    setScreen(SavedGuidesUiState(isLoadingGuides = true))

    composeTestRule.onNodeWithTag(C.Tag.collection_loading).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_empty_state).assertDoesNotExist()
  }

  @Test
  fun showsErrorWhenCollectionFailsToLoad() {
    setScreen(SavedGuidesUiState(guidesError = IllegalStateException("load failed")))

    composeTestRule.onNodeWithTag(C.Tag.collection_error).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_empty_state).assertDoesNotExist()
  }

  @Test
  fun keepsLoadedGuidesVisibleWhileReloadingOrAfterAnError() {
    setScreen(
        SavedGuidesUiState(
            guides = listOf(newerGuide),
            isLoadingGuides = true,
            guidesError = IllegalStateException("refresh failed"),
        )
    )

    composeTestRule.onAllNodesWithTag(C.Tag.collection_card).assertCountEquals(1)
    composeTestRule.onNodeWithTag(C.Tag.collection_loading).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.collection_error).assertDoesNotExist()
  }

  @Test
  fun showsStoredPhotoOnceDecoded() {
    setScreen(SavedGuidesUiState(guides = listOf(newerGuide.copy(photoBytes = pngBytes()))))

    waitForTag(C.Tag.collection_card_photo)
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      !hasTag(C.Tag.collection_card_photo_loading) && !hasTag(C.Tag.collection_card_photo_error)
    }
    composeTestRule
        .onNodeWithTag(C.Tag.collection_card_photo_placeholder, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun showsSpinnerWhilePhotoIsLoading() {
    Coil.setImageLoader(
        ImageLoader.Builder(ApplicationProvider.getApplicationContext())
            .components { add(Interceptor { awaitCancellation() }) }
            .build()
    )
    setScreen(SavedGuidesUiState(guides = listOf(newerGuide.copy(photoBytes = pngBytes()))))

    composeTestRule
        .onNodeWithTag(C.Tag.collection_card_photo_loading, useUnmergedTree = true)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.collection_card_photo_error, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun showsErrorPlaceholderWhenPhotoCannotBeDecoded() {
    setScreen(
        SavedGuidesUiState(guides = listOf(newerGuide.copy(photoBytes = byteArrayOf(1, 2, 3))))
    )

    waitForTag(C.Tag.collection_card_photo_error)
    composeTestRule
        .onNodeWithTag(C.Tag.collection_card_photo_error, useUnmergedTree = true)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.collection_card_photo_loading, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  private fun waitForTag(tag: String) {
    composeTestRule.waitUntil(TIMEOUT_MILLIS) { hasTag(tag) }
  }

  private fun hasTag(tag: String): Boolean =
      composeTestRule
          .onAllNodesWithTag(tag, useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isNotEmpty()

  private fun pngBytes(): ByteArray {
    val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
    return ByteArrayOutputStream().use { stream ->
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
      stream.toByteArray()
    }
  }

  private fun setScreen(state: SavedGuidesUiState, onGuideClick: (String) -> Unit = {}) {
    composeTestRule.setContent {
      SampleAppTheme { CollectionScreen(state = state, onGuideClick = onGuideClick) }
    }
  }

  private fun guide(id: String, name: String, discoveryDate: Long?) =
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

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
  }
}
