// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.SuccessResult
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.guide.SavedGuidesUiState
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WishlistScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val monument =
      WishlistMonument(
          id = "shared",
          name = "Sagrada Familia",
          city = "Barcelona",
          country = "Spain",
          scannedBy = listOf(WishlistFriend("Ada"), WishlistFriend("Grace")),
      )
  private val guide =
      Guide(
          id = "shared",
          photoBytes = byteArrayOf(),
          monumentName = "Sagrada Familia",
          confidence = 1.0,
          history = "",
          facts = emptyList(),
          culture = "",
      )

  @After
  fun resetImageLoader() {
    Coil.reset()
  }

  @Test
  fun wishlistIsAvailableWithNoSavedGuidesAndHasAnInformationHeader() {
    composeTestRule.setContent { SampleAppTheme { CollectionScreen(SavedGuidesUiState()) } }

    selectWishlist()

    composeTestRule.onNodeWithTag(C.Tag.collection_filter_wishlist).assertIsSelected()
    composeTestRule
        .onNodeWithText("From friends. Scan one to move it to your collection.")
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_empty).assertIsDisplayed()
  }

  @Test
  fun sharedFriendsErrorCanBeRetriedWithoutHidingWishlist() {
    var retries = 0
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = SavedGuidesUiState(),
            wishlistState =
                WishlistUiState(
                    monuments = listOf(monument.copy(scannedBy = emptyList())),
                    scannedByError = IllegalStateException("offline"),
                ),
            initialShowWishlist = true,
            onRetryWishlistFriends = { retries++ },
        )
      }
    }
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_card).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_friends_error).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_friends_retry).performClick()
    assertEquals(1, retries)
  }

  @Test
  fun unavailableGuideShowsMessageAndCannotBeOpenedWithoutLocation() {
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = SavedGuidesUiState(),
            wishlistState =
                WishlistUiState(listOf(monument.copy(city = "", country = "", unavailable = true))),
            initialShowWishlist = true,
        )
      }
    }
    composeTestRule.onNodeWithText("This shared guide is no longer available.").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_card).assertHasNoClickAction()
  }

  @Test
  fun coordinatesAreShownWhenNoLocationNameIsAvailable() {
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = SavedGuidesUiState(),
            wishlistState =
                WishlistUiState(
                    listOf(
                        monument.copy(city = "", country = "", location = Coordinates(48.86, 2.34))
                    )
                ),
            initialShowWishlist = true,
        )
      }
    }
    composeTestRule
        .onNodeWithText(
            java.lang.String.format(java.util.Locale.getDefault(), "%.4f, %.4f", 48.86, 2.34)
        )
        .assertIsDisplayed()
  }

  @Test
  fun changingInitialWishlistVisibilityDoesNotResetSelectedFilter() {
    var initialShowWishlist by mutableStateOf(false)
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = SavedGuidesUiState(),
            initialShowWishlist = initialShowWishlist,
        )
      }
    }

    selectWishlist()
    composeTestRule.runOnIdle { initialShowWishlist = true }
    composeTestRule.onNodeWithTag(C.Tag.collection_filter_wishlist).assertIsSelected()
    composeTestRule.runOnIdle { initialShowWishlist = false }
    composeTestRule.onNodeWithTag(C.Tag.collection_filter_wishlist).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_empty).assertIsDisplayed()
  }

  @Test
  fun readOnlyCollectionDoesNotExposeWishlistOrOwnerActions() {
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = SavedGuidesUiState(guides = listOf(guide)),
            readOnly = true,
            wishlistState = WishlistUiState(listOf(monument)),
            initialShowWishlist = true,
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.collection_card).assertIsDisplayed()
    composeTestRule.onNodeWithText("Search their collection").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_filter_wishlist).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_card).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.collection_gallery_button).assertDoesNotExist()
  }

  @Test
  fun retryRemainsAvailableAlongsideWishlistControls() {
    var retries = 0
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state =
                SavedGuidesUiState(
                    guides = listOf(guide),
                    guidesError = IllegalStateException("offline"),
                ),
            wishlistState = WishlistUiState(listOf(monument)),
            onRetryGuides = { retries++ },
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.collection_filter_wishlist).assertExists()
    composeTestRule.onNodeWithTag(C.Tag.collection_retry).performClick()
    assertEquals(1, retries)
  }

  @Test
  fun wishlistMonumentsOnlyAppearInWishlistAndRemainAfterRemoval() {
    val changes = mutableListOf<Pair<String, Boolean>>()
    composeTestRule.setContent {
      var wishlist by remember { mutableStateOf(WishlistUiState(listOf(monument))) }
      SampleAppTheme {
        CollectionScreen(
            state = SavedGuidesUiState(),
            wishlistState = wishlist,
            onSetWishlisted = { id, selected ->
              changes += id to selected
              wishlist =
                  wishlist.copy(
                      monuments = wishlist.monuments.map { it.copy(isWishlisted = selected) }
                  )
            },
        )
      }
    }

    composeTestRule.onAllNodesWithTag(C.Tag.collection_wishlist_card).assertCountEquals(0)
    selectWishlist()
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_card).assertIsDisplayed()
    composeTestRule.onNodeWithText("Barcelona, Spain").assertIsDisplayed()
    composeTestRule.onNodeWithText("Scanned by 2 friends").assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.collection_wishlist_button)
        .assertContentDescriptionEquals("Remove from wishlist")
        .performClick()
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_card).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.collection_wishlist_button)
        .assertContentDescriptionEquals("Add to wishlist")
        .performClick()
    assertEquals(listOf("shared" to false, "shared" to true), changes)
  }

  @Test
  fun wishlistSearchToleratesTypos() {
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = SavedGuidesUiState(searchQuery = "Barcelone"),
            wishlistState = WishlistUiState(listOf(monument)),
            initialShowWishlist = true,
        )
      }
    }
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_card).assertIsDisplayed()
  }

  @Test
  fun wishlistShowsLoadingInsteadOfEmptyMessage() {
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = SavedGuidesUiState(),
            wishlistState = WishlistUiState(isLoading = true),
            initialShowWishlist = true,
        )
      }
    }
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_empty).assertDoesNotExist()
  }

  @Test
  fun wishlistErrorOffersRetry() {
    var retries = 0
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = SavedGuidesUiState(),
            wishlistState = WishlistUiState(error = Exception("boom")),
            onRetryWishlist = { retries++ },
            initialShowWishlist = true,
        )
      }
    }
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_empty).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.collection_retry).performClick()
    assertEquals(1, retries)
  }

  @Test
  fun savedGuidesHaveNoWishlistButtonEvenIfIdsOverlap() {
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = SavedGuidesUiState(guides = listOf(guide)),
            wishlistState = WishlistUiState(listOf(monument)),
        )
      }
    }
    composeTestRule.onNodeWithTag(C.Tag.collection_card).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_button).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.collection_filter_favorites).performClick()
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_card).assertDoesNotExist()
  }

  @Test
  fun cardsUseFullWidthAndShowUpToThreeOverlappingAvatarsWithOverflow() {
    val friends = listOf("Ada", "Grace", "Katherine", "Alice").map { WishlistFriend(it) }
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = SavedGuidesUiState(),
            wishlistState = WishlistUiState(listOf(monument.copy(scannedBy = friends))),
        )
      }
    }
    selectWishlist()
    val card = composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_card)
    val screen = composeTestRule.onNodeWithTag(C.Tag.collection_screen)
    val cardBounds = card.getUnclippedBoundsInRoot()
    val screenBounds = screen.getUnclippedBoundsInRoot()
    assertEquals(
        (screenBounds.right - screenBounds.left).value - 40f,
        (cardBounds.right - cardBounds.left).value,
        0.1f,
    )
    val avatars =
        composeTestRule.onAllNodesWithTag(C.Tag.collection_wishlist_avatar, useUnmergedTree = true)
    avatars.assertCountEquals(3)
    assertTrue(
        avatars[0].getUnclippedBoundsInRoot().right > avatars[1].getUnclippedBoundsInRoot().left
    )
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_more_friends, true).assertExists()
    composeTestRule.onNodeWithText("Scanned by 4 friends").assertIsDisplayed()
  }

  @Test
  fun singleFriendShowsTheirNameWithoutOverflow() {
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = SavedGuidesUiState(),
            wishlistState =
                WishlistUiState(listOf(monument.copy(scannedBy = listOf(WishlistFriend("Ada"))))),
        )
      }
    }
    selectWishlist()
    composeTestRule.onNodeWithText("Scanned by Ada").assertIsDisplayed()
    composeTestRule
        .onAllNodesWithTag(C.Tag.collection_wishlist_avatar, useUnmergedTree = true)
        .assertCountEquals(1)
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_more_friends, true).assertDoesNotExist()
  }

  @Test
  fun locationShowsOnlyTheAvailablePart() {
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = SavedGuidesUiState(),
            wishlistState =
                WishlistUiState(
                    listOf(
                        monument.copy(id = "a", name = "A", city = "", country = "Spain"),
                        monument.copy(id = "b", name = "B", city = "Girona", country = ""),
                    )
                ),
        )
      }
    }
    selectWishlist()
    composeTestRule.onNodeWithText("Spain").assertIsDisplayed()
    composeTestRule.onNodeWithText("Girona").assertIsDisplayed()
  }

  @Test
  fun emptyOrBlankPhotosFallBackToThePlaceholder() {
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = SavedGuidesUiState(),
            wishlistState =
                WishlistUiState(
                    listOf(
                        monument.copy(id = "a", name = "A", photoBytes = byteArrayOf()),
                        monument.copy(id = "b", name = "B", photoUrl = " "),
                    )
                ),
        )
      }
    }
    selectWishlist()
    composeTestRule
        .onAllNodesWithTag(C.Tag.collection_card_photo_placeholder, useUnmergedTree = true)
        .assertCountEquals(2)
  }

  @Test
  fun loadErrorWithoutCardsDoesNotShowTheCardRetry() {
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = SavedGuidesUiState(),
            wishlistState = WishlistUiState(error = IllegalStateException("offline")),
        )
      }
    }
    selectWishlist()
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_card).assertDoesNotExist()
  }

  @Test
  fun singleFriendWithoutProfileShowsGenericName() {
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = SavedGuidesUiState(),
            wishlistState =
                WishlistUiState(listOf(monument.copy(scannedBy = listOf(WishlistFriend(""))))),
        )
      }
    }
    selectWishlist()
    composeTestRule.onNodeWithText("Scanned by Friend").assertIsDisplayed()
  }

  @Test
  fun unavailableFriendAvatarFallsBackToInitial() {
    val requests = AtomicInteger()
    Coil.setImageLoader(
        ImageLoader.Builder(ApplicationProvider.getApplicationContext())
            .components {
              add(
                  Interceptor { chain ->
                    requests.incrementAndGet()
                    ErrorResult(null, chain.request, IllegalStateException("image unavailable"))
                  }
              )
            }
            .build()
    )
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = SavedGuidesUiState(),
            wishlistState =
                WishlistUiState(
                    listOf(
                        monument.copy(
                            scannedBy = listOf(WishlistFriend("Ada", "https://example.com/avatar"))
                        )
                    )
                ),
            initialShowWishlist = true,
        )
      }
    }

    composeTestRule.waitUntil(5_000) { requests.get() > 0 }
    composeTestRule.onNodeWithText("A", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun threeFriendsHaveNoOverflowIcon() {
    val friends = listOf("Ada", "Grace", "Katherine").map { WishlistFriend(it) }
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = SavedGuidesUiState(),
            wishlistState = WishlistUiState(listOf(monument.copy(scannedBy = friends))),
        )
      }
    }
    selectWishlist()
    composeTestRule
        .onAllNodesWithTag(C.Tag.collection_wishlist_avatar, useUnmergedTree = true)
        .assertCountEquals(3)
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_more_friends, true).assertDoesNotExist()
    composeTestRule.onNodeWithText("Scanned by 3 friends").assertIsDisplayed()
  }

  @Test
  fun missingLocationAndFriendsAreHiddenAndBlankPhotoUsesPlaceholder() {
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = SavedGuidesUiState(),
            wishlistState =
                WishlistUiState(
                    listOf(
                        monument.copy(
                            city = " ",
                            country = "",
                            photoUrl = " ",
                            scannedBy = emptyList(),
                            isWishlisted = false,
                        )
                    )
                ),
            initialShowWishlist = true,
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_card).assertIsDisplayed()
    composeTestRule.onNodeWithText("Barcelona, Spain").assertDoesNotExist()
    composeTestRule.onNodeWithText("Scanned by 2 friends").assertDoesNotExist()
    composeTestRule
        .onAllNodesWithTag(C.Tag.collection_wishlist_avatar, useUnmergedTree = true)
        .assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.collection_card_photo_placeholder, true).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.collection_wishlist_button)
        .assertContentDescriptionEquals("Add to wishlist")
  }

  @Test
  fun wishlistImageShowsErrorAndThenSuccessfulReplacement() {
    val successfulRequests = AtomicInteger()
    Coil.setImageLoader(
        ImageLoader.Builder(ApplicationProvider.getApplicationContext())
            .components {
              add(
                  Interceptor { chain ->
                    if (chain.request.data.toString().endsWith("error")) {
                      ErrorResult(null, chain.request, IllegalStateException("image unavailable"))
                    } else {
                      successfulRequests.incrementAndGet()
                      SuccessResult(ColorDrawable(Color.RED), chain.request, DataSource.MEMORY)
                    }
                  }
              )
            }
            .build()
    )
    var wishlist by
        mutableStateOf(
            WishlistUiState(listOf(monument.copy(photoUrl = "https://example.com/error")))
        )
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = SavedGuidesUiState(),
            wishlistState = wishlist,
            initialShowWishlist = true,
        )
      }
    }

    composeTestRule.waitUntil(5_000) {
      composeTestRule
          .onAllNodesWithTag(C.Tag.collection_card_photo_error, useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeTestRule.onNodeWithTag(C.Tag.collection_card_photo_error, true).assertIsDisplayed()
    composeTestRule.runOnIdle {
      wishlist = WishlistUiState(listOf(monument.copy(photoUrl = "https://example.com/success")))
    }
    composeTestRule.waitUntil(5_000) {
      val errors =
          composeTestRule
              .onAllNodesWithTag(C.Tag.collection_card_photo_error, useUnmergedTree = true)
              .fetchSemanticsNodes()
      successfulRequests.get() > 0 && errors.isEmpty()
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_photo, true).assertExists()
  }

  @Test
  fun selectedWishlistSurvivesStateRestoration() {
    val restoration = StateRestorationTester(composeTestRule)
    restoration.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = SavedGuidesUiState(),
            wishlistState = WishlistUiState(listOf(monument)),
        )
      }
    }

    selectWishlist()
    restoration.emulateSavedInstanceStateRestore()
    composeTestRule.onNodeWithTag(C.Tag.collection_filter_wishlist).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_card).assertIsDisplayed()
  }

  @Test
  fun wishlistSelectedWhileFavoritesFilterIsActiveAndSearchMatchesName() {
    var state by
        mutableStateOf(SavedGuidesUiState(guides = listOf(guide), showFavoritesOnly = true))
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = state,
            wishlistState = WishlistUiState(listOf(monument.copy(city = "", country = "Spain"))),
            onShowFavoritesOnlyChange = { state = state.copy(showFavoritesOnly = it) },
            onSearchQueryChange = { state = state.copy(searchQuery = it) },
        )
      }
    }
    selectWishlist()
    composeTestRule.onNodeWithTag(C.Tag.collection_filter_wishlist).assertIsSelected()
    composeTestRule.onNodeWithText("Spain").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_search_bar).performTextInput("Sagrada")
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_card).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_filter_all).performClick()
    composeTestRule.onNodeWithTag(C.Tag.collection_card).assertIsDisplayed()
  }

  @Test
  fun requestsMonumentAndFriendPhotos() {
    val requests = CopyOnWriteArrayList<Any>()
    Coil.setImageLoader(
        ImageLoader.Builder(ApplicationProvider.getApplicationContext())
            .components {
              add(
                  Interceptor { chain ->
                    requests.add(chain.request.data)
                    awaitCancellation()
                  }
              )
            }
            .build()
    )
    val photoUrl = "https://example.com/monument.jpg"
    val avatarUrl = "https://example.com/avatar.jpg"
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = SavedGuidesUiState(),
            wishlistState =
                WishlistUiState(
                    listOf(
                        monument.copy(
                            photoUrl = photoUrl,
                            scannedBy = listOf(WishlistFriend("Ada", avatarUrl)),
                        )
                    )
                ),
        )
      }
    }
    selectWishlist()
    composeTestRule.waitUntil(5_000) { requests.containsAll(listOf(photoUrl, avatarUrl)) }
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_photo, true).assertExists()
  }

  @Test
  fun wishlistSearchAndDarkTheme() {
    var state by mutableStateOf(SavedGuidesUiState())
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = true, dynamicColor = false) {
        CollectionScreen(
            state = state,
            wishlistState = WishlistUiState(listOf(monument)),
            onSearchQueryChange = { state = state.copy(searchQuery = it) },
        )
      }
    }
    selectWishlist()
    composeTestRule.onNodeWithTag(C.Tag.collection_search_bar).performTextInput("Barcelona, Spain")
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_card).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_search_clear_button).performClick()
    composeTestRule.onNodeWithTag(C.Tag.collection_search_bar).performTextInput("Rome")
    composeTestRule.onNodeWithTag(C.Tag.collection_search_no_matches).assertIsDisplayed()
  }

  @Test
  fun removalIsRetainedAcrossFiltersUntilTheSnapshotIsRefreshed() {
    var wishlist by mutableStateOf(WishlistUiState(listOf(monument)))
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = SavedGuidesUiState(),
            wishlistState = wishlist,
            onSetWishlisted = { _, selected ->
              wishlist =
                  wishlist.copy(
                      monuments = wishlist.monuments.map { it.copy(isWishlisted = selected) }
                  )
            },
        )
      }
    }
    selectWishlist()
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_button).performClick()
    composeTestRule.onNodeWithTag(C.Tag.collection_filter_all).performScrollTo().performClick()
    selectWishlist()
    composeTestRule
        .onNodeWithTag(C.Tag.collection_wishlist_button)
        .assertContentDescriptionEquals("Add to wishlist")
    composeTestRule.runOnIdle { wishlist = WishlistUiState(refreshVersion = 1) }
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_card).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.collection_wishlist_empty).assertIsDisplayed()
  }

  private fun selectWishlist() {
    composeTestRule.onNodeWithTag(C.Tag.collection_filter_wishlist).performScrollTo().performClick()
  }
}
