// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import coil.intercept.Interceptor
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.guide.SavedGuidesUiState
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import java.util.concurrent.CopyOnWriteArrayList
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
  fun wishlistMonumentsOnlyAppearInWishlistAndRemainAfterRemoval() {
    val changes = mutableListOf<Pair<String, Boolean>>()
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionScreen(
            state = SavedGuidesUiState(),
            wishlistState = WishlistUiState(listOf(monument)),
            onSetWishlisted = { id, selected -> changes += id to selected },
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
      SampleAppTheme { CollectionScreen(state = SavedGuidesUiState(), wishlistState = wishlist) }
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

  @Test
  fun backendEntryPointsAreNoOps() {
    val viewModel = WishlistViewModel()
    viewModel.loadWishlist()
    viewModel.setWishlisted("shared", true)
    assertEquals(WishlistUiState(), viewModel.uiState.value)
  }

  private fun selectWishlist() {
    composeTestRule.onNodeWithTag(C.Tag.collection_filter_wishlist).performScrollTo().performClick()
  }
}
