// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.monumentDetail

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.roamswent.roam.model.guide.FakeMonumentSharingRepository
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.guide.MonumentShareSettings
import com.github.roamswent.roam.model.guide.MonumentSharingRepository
import com.github.roamswent.roam.model.guide.ShareAudience
import com.github.roamswent.roam.model.wishlist.ResolvedWishlistItem
import com.github.roamswent.roam.model.wishlist.WishlistItem
import com.github.roamswent.roam.model.wishlist.WishlistMonumentKey
import com.github.roamswent.roam.model.wishlist.WishlistRepository
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.collection.FakeWishlistRepository
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_back_button)
        .assertContentDescriptionEquals("Home")
  }

  @Test
  fun wishlistGuideUsesBackArrow() {
    var backs = 0
    setRoute(
        FakeMonumentSharingRepository(mapOf("friend" to listOf(louvre))),
        "louvre",
        onBack = { backs++ },
        showBackArrow = true,
    )
    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_back_button)
        .assertContentDescriptionEquals("Back")
        .performClick()
    assertEquals(1, backs)
  }

  @Test
  fun wishlistGuideErrorAndMissingStatesUseBackArrow() {
    val repository = FakeMonumentSharingRepository()
    repository.loadError = IllegalStateException("offline")
    setRoute(repository, "louvre", showBackArrow = true)
    composeTestRule.onNodeWithTag(C.Tag.shared_monument_detail_error).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_back_button)
        .assertContentDescriptionEquals("Back")
    composeTestRule.runOnIdle { repository.loadError = null }
    composeTestRule.onNodeWithTag(C.Tag.shared_monument_detail_retry).performClick()
    composeTestRule.onNodeWithTag(C.Tag.shared_monument_detail_not_found).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_back_button)
        .assertContentDescriptionEquals("Back")
  }

  @Test
  fun wishlistButtonAddsAndRemovesTheSharedGuide() {
    val wishlist = FakeWishlistRepository()
    setRoute(
        FakeMonumentSharingRepository(mapOf("friend" to listOf(louvre))),
        "louvre",
        wishlist = wishlist,
    )
    val button = composeTestRule.onNodeWithTag(C.Tag.monument_detail_wishlist_button)
    button.assertContentDescriptionEquals("Add to wishlist")

    button.performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) { wishlist.items.value.size == 1 }
    val item = wishlist.items.value.single()
    assertEquals("friend", item.sourceOwnerUid)
    assertEquals("louvre", item.sourceGuideId)
    button.assertContentDescriptionEquals("Remove from wishlist")
    composeTestRule.onNodeWithText("Added to wishlist").assertIsDisplayed()

    button.performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) { wishlist.items.value.isEmpty() }
    button.assertContentDescriptionEquals("Add to wishlist")
  }

  @Test
  fun wishlistFailureKeepsTheButtonStateAndStaysUsable() {
    val wishlist = FakeWishlistRepository()
    wishlist.writeError = IllegalStateException("offline")
    setRoute(
        FakeMonumentSharingRepository(mapOf("friend" to listOf(louvre))),
        "louvre",
        wishlist = wishlist,
    )
    val button = composeTestRule.onNodeWithTag(C.Tag.monument_detail_wishlist_button)
    button.performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule
          .onAllNodesWithText("Couldn’t update your wishlist. Please try again.")
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    assertTrue(wishlist.items.value.isEmpty())
    button.assertContentDescriptionEquals("Add to wishlist")
  }

  @Test
  fun wishlistButtonIsDisabledWhenLoadingFails() {
    val wishlist = FakeWishlistRepository().apply { loadError = IllegalStateException("offline") }
    setRoute(
        FakeMonumentSharingRepository(mapOf("friend" to listOf(louvre))),
        "louvre",
        wishlist = wishlist,
    )
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_wishlist_button).assertIsNotEnabled()
    composeTestRule
        .onNodeWithText("Couldn’t load your wishlist. Please try again.")
        .assertIsDisplayed()
    assertTrue(wishlist.items.value.isEmpty())
  }

  @Test
  fun retryAfterAFailedWishlistLoadEnablesTheButton() {
    val wishlist = FakeWishlistRepository().apply { loadError = IllegalStateException("offline") }
    setRoute(
        FakeMonumentSharingRepository(mapOf("friend" to listOf(louvre))),
        "louvre",
        wishlist = wishlist,
    )
    composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
    wishlist.loadError = null
    composeTestRule.onNodeWithText("Retry").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule
          .onAllNodesWithText("Couldn’t load your wishlist. Please try again.")
          .fetchSemanticsNodes()
          .isEmpty()
    }
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_wishlist_button).assertIsEnabled()
  }

  @Test
  fun bookmarkStateIsReadWithoutResolvingGuides() {
    val wishlist = FakeWishlistRepository()
    var resolved = 0
    val counting =
        object : WishlistRepository by wishlist {
          override suspend fun getResolvedWishlist(): List<ResolvedWishlistItem> {
            resolved++
            return wishlist.getResolvedWishlist()
          }
        }
    setRoute(
        FakeMonumentSharingRepository(mapOf("friend" to listOf(louvre))),
        "louvre",
        wishlist = counting,
    )
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_wishlist_button).assertIsEnabled()
    assertEquals(0, resolved)
  }

  @Test
  fun wishlistButtonStaysUsableWhileTheWriteIsPending() {
    val wishlist = FakeWishlistRepository()
    val release = CompletableDeferred<Unit>()
    val delayedWishlist =
        object : WishlistRepository by wishlist {
          override suspend fun addToWishlist(item: WishlistItem): WishlistItem {
            release.await()
            return wishlist.addToWishlist(item)
          }
        }
    setRoute(
        FakeMonumentSharingRepository(mapOf("friend" to listOf(louvre))),
        "louvre",
        wishlist = delayedWishlist,
    )
    val button = composeTestRule.onNodeWithTag(C.Tag.monument_detail_wishlist_button)
    button.performClick()
    button.assertIsEnabled()
    button.assertContentDescriptionEquals("Remove from wishlist")
    assertTrue(wishlist.items.value.isEmpty())
    release.complete(Unit)
  }

  @Test
  fun wishlistedGuideShowsFilledButton() {
    val wishlist = FakeWishlistRepository()
    val key = WishlistMonumentKey.forSharedGuide("friend", louvre.id)
    wishlist.items.value = listOf(WishlistItem(key, "friend", louvre.id, louvre.monumentName, 1))
    setRoute(
        FakeMonumentSharingRepository(mapOf("friend" to listOf(louvre))),
        "louvre",
        wishlist = wishlist,
    )
    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_wishlist_button)
        .assertContentDescriptionEquals("Remove from wishlist")
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
      showBackArrow: Boolean = false,
      wishlist: WishlistRepository = FakeWishlistRepository(),
  ) {
    composeTestRule.setContent {
      SampleAppTheme {
        SharedMonumentDetailRoute(
            ownerUid = "friend",
            guideId = guideId,
            repository = repository,
            onBackClick = onBack,
            showBackArrow = showBackArrow,
            wishlistRepository = wishlist,
        )
      }
    }
  }
}
