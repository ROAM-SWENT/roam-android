// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.intercept.Interceptor
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.guide.SavedGuidesUiState
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import com.github.roamswent.roam.ui.theme.ScreenBackground
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
  fun screenPreservesLightColorsAndUsesDarkThemeSurfaces() {
    val guide = guide("guide", "Eiffel Tower", discoveryDate = newerGuide.discoveryDate)
    var darkTheme by mutableStateOf(false)
    var darkBackgroundColor = 0
    var darkCardColor = 0
    var darkTitleColor = 0

    composeTestRule.setContent {
      SampleAppTheme(darkTheme = darkTheme, dynamicColor = false) {
        if (darkTheme) {
          darkBackgroundColor = MaterialTheme.colorScheme.background.toArgb()
          darkCardColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
          darkTitleColor = MaterialTheme.colorScheme.onSurface.toArgb()
        }
        CollectionScreen(
            state = SavedGuidesUiState(guides = listOf(guide)),
        )
      }
    }

    val lightBackground =
        composeTestRule.onNodeWithTag(C.Tag.collection_screen).captureToImage().asAndroidBitmap()
    val lightCard =
        composeTestRule.onNodeWithTag(C.Tag.collection_card).captureToImage().asAndroidBitmap()

    assertEquals(ScreenBackground.toArgb(), lightBackground.getPixel(0, 0))
    assertTrue(containsColor(lightCard, Color.White.toArgb()))

    composeTestRule.runOnIdle { darkTheme = true }

    val darkBackground =
        composeTestRule.onNodeWithTag(C.Tag.collection_screen).captureToImage().asAndroidBitmap()
    val darkCard =
        composeTestRule.onNodeWithTag(C.Tag.collection_card).captureToImage().asAndroidBitmap()
    val darkTitle =
        composeTestRule
            .onNodeWithTag(C.Tag.collection_card_name, useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()

    assertEquals(darkBackgroundColor, darkBackground.getPixel(0, 0))
    assertNotEquals(ScreenBackground.toArgb(), darkBackground.getPixel(0, 0))
    assertNotEquals(darkBackgroundColor, darkCardColor)
    assertTrue(containsColor(darkCard, darkCardColor))
    assertTrue(containsColor(darkTitle, darkTitleColor))
  }

  @Test
  fun populatedCollectionPreviewRendersGuideCards() {
    composeTestRule.setContent { PreviewCollectionScreen() }
    composeTestRule.onNodeWithTag(C.Tag.collection_title).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_grid).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.collection_card).assertCountEquals(4)
  }

  @Test
  fun populatedDarkCollectionPreviewRendersGuideCards() {
    composeTestRule.setContent { PreviewCollectionScreenDark() }
    composeTestRule.onNodeWithTag(C.Tag.collection_title).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_grid).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.collection_card).assertCountEquals(4)
  }

  @Test
  fun emptyCollectionPreviewRendersEmptyState() {
    composeTestRule.setContent { PreviewCollectionScreenEmpty() }
    composeTestRule.onNodeWithTag(C.Tag.collection_title).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_empty_state).assertIsDisplayed()
  }

  @Test
  fun emptyDarkCollectionPreviewRendersEmptyState() {
    composeTestRule.setContent { PreviewCollectionScreenEmptyDark() }
    composeTestRule.onNodeWithTag(C.Tag.collection_title).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_empty_state).assertIsDisplayed()
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

  @Test
  fun keepsDecodedPhotoWhenAnotherGuideIsAddedAndTheArrayIsRebuilt() {
    val requests = AtomicInteger()
    // Only the first request may finish, so a second one would leave the spinner up.
    Coil.setImageLoader(
        ImageLoader.Builder(ApplicationProvider.getApplicationContext())
            .components {
              add(
                  Interceptor { chain ->
                    if (requests.getAndIncrement() == 0) chain.proceed(chain.request)
                    else awaitCancellation()
                  }
              )
            }
            .build()
    )
    var state by mutableStateOf(SavedGuidesUiState(guides = listOf(guideWithPhoto(pngBytes()))))
    composeTestRule.setContent { SampleAppTheme { CollectionScreen(state = state) } }
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      hasTag(C.Tag.collection_card_photo) && !hasTag(C.Tag.collection_card_photo_loading)
    }

    // Another guide is added; the snapshot rebuilds the first guide with a new array of equal
    // bytes.
    state = SavedGuidesUiState(guides = listOf(guideWithPhoto(pngBytes()), olderGuide))
    composeTestRule.waitForIdle()

    assertEquals(1, requests.get())
    assertFalse(hasTag(C.Tag.collection_card_photo_loading))
    assertFalse(hasTag(C.Tag.collection_card_photo_error))
  }

  @Test
  fun decodesAgainWhenThePhotoOfAGuideChanges() {
    var state by mutableStateOf(SavedGuidesUiState(guides = listOf(guideWithPhoto(pngBytes()))))
    composeTestRule.setContent { SampleAppTheme { CollectionScreen(state = state) } }
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      hasTag(C.Tag.collection_card_photo) && !hasTag(C.Tag.collection_card_photo_loading)
    }

    state = SavedGuidesUiState(guides = listOf(guideWithPhoto(byteArrayOf(1, 2, 3))))

    waitForTag(C.Tag.collection_card_photo_error)
  }

  @Test
  fun heartShowsWhetherEachGuideIsAFavourite() {
    setScreen(SavedGuidesUiState(guides = listOf(olderGuide, newerGuide.copy(isFavorite = true))))

    val hearts = composeTestRule.onAllNodesWithTag(C.Tag.collection_card_favorite)
    hearts.assertCountEquals(2)
    // Newest first: the favourite Eiffel Tower, then the Colosseum.
    hearts[0].assertContentDescriptionEquals("Remove from favourites")
    hearts[1].assertContentDescriptionEquals("Add to favourites")
  }

  @Test
  fun tappingTheHeartTogglesThatGuideWithoutOpeningIt() {
    val toggled = mutableListOf<String>()
    val opened = mutableListOf<String>()
    setScreen(
        SavedGuidesUiState(guides = listOf(olderGuide, newerGuide)),
        onGuideClick = { opened += it },
        onToggleFavorite = { toggled += it },
    )

    composeTestRule.onAllNodesWithTag(C.Tag.collection_card_favorite)[1].performClick()

    assertEquals(listOf("older"), toggled)
    assertTrue(opened.isEmpty())
  }

  @Test
  fun allChipIsSelectedByDefaultAndChipsReportTheChosenFilter() {
    val filters = mutableListOf<Boolean>()
    setScreen(
        SavedGuidesUiState(guides = listOf(olderGuide)),
        onShowFavoritesOnlyChange = { filters += it },
    )

    composeTestRule.onNodeWithTag(C.Tag.collection_filter_all).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.collection_filter_favorites).assertIsNotSelected()
    composeTestRule.onNodeWithTag(C.Tag.collection_filter_favorites).performClick()
    composeTestRule.onNodeWithTag(C.Tag.collection_filter_all).performClick()

    assertEquals(listOf(true, false), filters)
  }

  @Test
  fun favouritesFilterShowsOnlyFavouriteGuides() {
    setScreen(
        SavedGuidesUiState(
            guides = listOf(olderGuide.copy(isFavorite = true), newerGuide),
            showFavoritesOnly = true,
        )
    )

    composeTestRule.onNodeWithTag(C.Tag.collection_filter_favorites).assertIsSelected()
    composeTestRule.onAllNodesWithTag(C.Tag.collection_card).assertCountEquals(1)
    composeTestRule
        .onNodeWithTag(C.Tag.collection_card_name, useUnmergedTree = true)
        .assertTextEquals("Colosseum")
  }

  @Test
  fun favouritesFilterWithoutFavouritesShowsItsOwnEmptyState() {
    setScreen(SavedGuidesUiState(guides = listOf(olderGuide), showFavoritesOnly = true))

    composeTestRule.onNodeWithTag(C.Tag.collection_favorites_empty).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_filter_all).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_grid).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.collection_empty_state).assertDoesNotExist()
  }

  @Test
  fun chipsAreHiddenWhenTheCollectionIsEmptyOrLoading() {
    var state by mutableStateOf(SavedGuidesUiState())
    composeTestRule.setContent { SampleAppTheme { CollectionScreen(state = state) } }
    composeTestRule.onNodeWithTag(C.Tag.collection_filter_all).assertDoesNotExist()

    state = SavedGuidesUiState(isLoadingGuides = true)

    composeTestRule.onNodeWithTag(C.Tag.collection_filter_all).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.collection_filter_favorites).assertDoesNotExist()
  }

  @Test
  fun visibleGuidesFiltersFavouritesThenSortsNewestFirst() {
    val newerFavourite = newerGuide.copy(isFavorite = true)
    val olderFavourite = olderGuide.copy(isFavorite = true)
    val undated = guide("undated", "Big Ben", discoveryDate = null)

    assertEquals(
        listOf(newerFavourite, olderFavourite),
        visibleGuides(listOf(olderFavourite, undated, newerFavourite), favoritesOnly = true),
    )
    assertEquals(
        listOf(newerFavourite, olderFavourite, undated),
        visibleGuides(listOf(olderFavourite, undated, newerFavourite), favoritesOnly = false),
    )
  }

  @Test
  fun unlikingInFavouritesModeOffersUndo() {
    val restored = mutableListOf<String>()
    setScreen(
        SavedGuidesUiState(
            guides = listOf(olderGuide.copy(isFavorite = true)),
            showFavoritesOnly = true,
        ),
        onRestoreFavorite = { restored += it },
    )

    composeTestRule.onNodeWithTag(C.Tag.collection_card_favorite).performClick()

    composeTestRule.onNodeWithText("Removed from favourites").assertIsDisplayed()
    composeTestRule.onNodeWithText("Undo").performClick()
    assertEquals(listOf("older"), restored)
  }

  @Test
  fun unlikingInAllModeOffersNoUndo() {
    setScreen(SavedGuidesUiState(guides = listOf(olderGuide.copy(isFavorite = true))))

    composeTestRule.onNodeWithTag(C.Tag.collection_card_favorite).performClick()

    composeTestRule.onNodeWithText("Removed from favourites").assertDoesNotExist()
  }

  @Test
  fun favouriteErrorIsShownOnceAndReported() {
    var reported = 0
    setScreen(
        SavedGuidesUiState(
            guides = listOf(olderGuide),
            favoriteError = IllegalStateException("offline"),
        ),
        onFavoriteErrorShown = { reported++ },
    )

    composeTestRule
        .onNodeWithText("Couldn’t update favourites. Please try again.")
        .assertIsDisplayed()
    assertEquals(1, reported)
  }

  @Test
  fun searchBarRendersWithPlaceholderWhenGuidesExist() {
    setScreen(SavedGuidesUiState(guides = listOf(olderGuide)))

    composeTestRule.onNodeWithTag(C.Tag.collection_search_bar).assertIsDisplayed()
    composeTestRule
        .onNodeWithText("Search your collection", useUnmergedTree = true)
        .assertIsDisplayed()
  }

  @Test
  fun typingInSearchBarFiltersGuideGridLive() {
    var state by mutableStateOf(SavedGuidesUiState(guides = listOf(olderGuide, newerGuide)))
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = state,
            onSearchQueryChange = { state = state.copy(searchQuery = it) },
        )
      }
    }

    composeTestRule.onAllNodesWithTag(C.Tag.collection_card).assertCountEquals(2)

    composeTestRule.onNodeWithTag(C.Tag.collection_search_bar).performTextInput("Eiffel")

    composeTestRule.onAllNodesWithTag(C.Tag.collection_card).assertCountEquals(1)
    composeTestRule
        .onNodeWithTag(C.Tag.collection_card_name, useUnmergedTree = true)
        .assertTextEquals("Eiffel Tower")
  }

  @Test
  fun noMatchesStateAppearsWhenGuidesExistAndNonBlankQueryHasNoMatches() {
    setScreen(
        SavedGuidesUiState(guides = listOf(olderGuide, newerGuide), searchQuery = "UnknownMonument")
    )

    composeTestRule.onNodeWithTag(C.Tag.collection_search_no_matches).assertIsDisplayed()
    composeTestRule.onNodeWithText("No matches").assertIsDisplayed()
    composeTestRule.onNodeWithText("No monument matches your search.").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_empty_state).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.collection_favorites_empty).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.collection_grid).assertDoesNotExist()
  }

  @Test
  fun noMatchesStateDoesNotAppearWhenCollectionIsFullyEmptyOrFavoritesEmpty() {
    var state by
        mutableStateOf(SavedGuidesUiState(guides = emptyList(), searchQuery = "UnknownMonument"))
    composeTestRule.setContent { SampleAppTheme { CollectionScreen(state = state) } }

    composeTestRule.onNodeWithTag(C.Tag.collection_empty_state).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_search_no_matches).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.collection_search_bar).assertDoesNotExist()

    state =
        SavedGuidesUiState(
            guides = listOf(olderGuide),
            showFavoritesOnly = true,
            searchQuery = "",
        )

    composeTestRule.onNodeWithTag(C.Tag.collection_favorites_empty).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_search_no_matches).assertDoesNotExist()
  }

  @Test
  fun clearButtonAppearsWhenQueryIsNotEmptyAndResetsQuery() {
    var state by mutableStateOf(SavedGuidesUiState(guides = listOf(olderGuide), searchQuery = ""))
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = state,
            onSearchQueryChange = { state = state.copy(searchQuery = it) },
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.collection_search_clear_button).assertDoesNotExist()

    state = state.copy(searchQuery = "Colosseum")

    composeTestRule.onNodeWithTag(C.Tag.collection_search_clear_button).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_search_clear_button).performClick()

    assertEquals("", state.searchQuery)
  }

  @Test
  fun searchBarCapsInputAt40CharactersAndDropsLongerInput() {
    var state by mutableStateOf(SavedGuidesUiState(guides = listOf(olderGuide), searchQuery = ""))
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = state,
            onSearchQueryChange = { state = state.copy(searchQuery = it) },
        )
      }
    }

    val fortyChars = "a".repeat(40)
    composeTestRule.onNodeWithTag(C.Tag.collection_search_bar).performTextInput(fortyChars)
    assertEquals(fortyChars, state.searchQuery)

    composeTestRule.onNodeWithTag(C.Tag.collection_search_bar).performTextInput("b")
    assertEquals(fortyChars, state.searchQuery)
  }

  private fun guideWithPhoto(photoBytes: ByteArray) = newerGuide.copy(photoBytes = photoBytes)

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

  private fun setScreen(
      state: SavedGuidesUiState,
      onGuideClick: (String) -> Unit = {},
      onToggleFavorite: (String) -> Unit = {},
      onRestoreFavorite: (String) -> Unit = {},
      onFavoriteErrorShown: () -> Unit = {},
      onShowFavoritesOnlyChange: (Boolean) -> Unit = {},
      onSearchQueryChange: (String) -> Unit = {},
  ) {
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = state,
            onGuideClick = onGuideClick,
            onToggleFavorite = onToggleFavorite,
            onRestoreFavorite = onRestoreFavorite,
            onFavoriteErrorShown = onFavoriteErrorShown,
            onShowFavoritesOnlyChange = onShowFavoritesOnlyChange,
            onSearchQueryChange = onSearchQueryChange,
        )
      }
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

  private fun containsColor(bitmap: Bitmap, color: Int): Boolean {
    for (x in 0 until bitmap.width) {
      for (y in 0 until bitmap.height) {
        if (bitmap.getPixel(x, y) == color) return true
      }
    }
    return false
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
  }
}
