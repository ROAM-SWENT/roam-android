// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import android.graphics.Bitmap
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.friend.Friendship
import com.github.roamswent.roam.model.guide.FakeMonumentSharingRepository
import com.github.roamswent.roam.model.guide.FakeSavedGuideRepository
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.guide.MonumentShareSettings
import com.github.roamswent.roam.model.guide.ShareAudience
import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
  fun profileBubbleReceivesAvatarBytes() {
    val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    bitmap.recycle()

    setRoute(
        FakeSavedGuideRepository(),
        profileName = "Ada",
        profileAvatarBytes = out.toByteArray(),
    )

    composeTestRule.onNodeWithTag(C.Tag.collection_profile_button).assertIsDisplayed()
  }

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

  @Test
  fun tappingTheHeartSavesTheFavouriteAndFillsTheHeart() {
    val repository = FakeSavedGuideRepository(listOf(colosseum))
    setRoute(repository)

    composeTestRule.onNodeWithTag(C.Tag.collection_card_favorite).performClick()

    composeTestRule.waitUntil { repository.guides.value.single().isFavorite }
    composeTestRule
        .onNodeWithTag(C.Tag.collection_card_favorite)
        .assertContentDescriptionEquals("Remove from favourites")
  }

  @Test
  fun favouritesFilterHidesOthersAndUnlikingRemovesTheCard() {
    val repository =
        FakeSavedGuideRepository(listOf(colosseum.copy(isFavorite = true), eiffelTower))
    setRoute(repository)

    composeTestRule.onNodeWithTag(C.Tag.collection_filter_favorites).performClick()
    composeTestRule.onAllNodesWithTag(C.Tag.collection_card).assertCountEquals(1)

    composeTestRule.onNodeWithTag(C.Tag.collection_card_favorite).performClick()

    composeTestRule.onNodeWithTag(C.Tag.collection_favorites_empty).assertIsDisplayed()
    composeTestRule.waitUntil { repository.guides.value.none { it.isFavorite } }

    composeTestRule.onNodeWithTag(C.Tag.collection_filter_all).performClick()
    composeTestRule.onAllNodesWithTag(C.Tag.collection_card).assertCountEquals(2)
    assertEquals(2, repository.guides.value.size)
  }

  @Test
  fun failedUnlikeBringsTheCardBackAndShowsAnError() {
    val repository = FakeSavedGuideRepository(listOf(colosseum.copy(isFavorite = true)))
    repository.favoriteError = IllegalStateException("offline")
    setRoute(repository)
    composeTestRule.onNodeWithTag(C.Tag.collection_filter_favorites).performClick()

    composeTestRule.onNodeWithTag(C.Tag.collection_card_favorite).performClick()

    composeTestRule
        .onNodeWithText("Couldn’t update favourites. Please try again.")
        .assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.collection_card).assertCountEquals(1)
    assertTrue(repository.guides.value.single().isFavorite)
  }

  @Test
  fun undoAfterUnlikingRestoresTheFavourite() {
    val repository = FakeSavedGuideRepository(listOf(colosseum.copy(isFavorite = true)))
    setRoute(repository)
    composeTestRule.onNodeWithTag(C.Tag.collection_filter_favorites).performClick()

    composeTestRule.onNodeWithTag(C.Tag.collection_card_favorite).performClick()
    composeTestRule.onNodeWithTag(C.Tag.collection_favorites_empty).assertIsDisplayed()
    composeTestRule.onNodeWithText("Undo").performClick()

    composeTestRule.waitUntil { repository.guides.value.single().isFavorite }
    composeTestRule.onAllNodesWithTag(C.Tag.collection_card).assertCountEquals(1)
  }

  @Test
  fun sharingThroughRoutePersistsTheAudience() {
    val repository = FakeSavedGuideRepository(listOf(colosseum))
    val sharing = FakeMonumentSharingRepository()
    val friends = mockk<FriendRepository>()
    val profiles = mockk<PublicProfileRepository>()
    coEvery { friends.getFriends() } returns listOf(Friendship("friendship", "friend"))
    coEvery { profiles.getProfile("friend") } returns PublicProfile("friend", "Alice")
    setSharingRoute(repository, sharing, friends, profiles)

    composeTestRule.onNodeWithTag(C.Tag.collection_card_share).performClick()
    composeTestRule.onNodeWithText("Alice").performClick()
    composeTestRule.onNodeWithTag(C.Tag.collection_share_confirm).performClick()
    composeTestRule.waitUntil { sharing.shareSettings.value[colosseum.id] != null }

    val settings = sharing.shareSettings.value.getValue(colosseum.id)
    assertEquals(ShareAudience.ALL_FRIENDS, settings.audience)
    assertEquals(emptySet<String>(), settings.sharedWithUids)
    composeTestRule
        .onNodeWithTag(C.Tag.collection_share_status, true)
        .assertTextEquals("Shared with all friends")

    composeTestRule.onNodeWithTag(C.Tag.collection_card_share).performClick()
    composeTestRule.onNodeWithTag(C.Tag.collection_share_audience_none).performClick()
    composeTestRule.onNodeWithTag(C.Tag.collection_share_confirm).performClick()
    composeTestRule.waitUntil {
      sharing.shareSettings.value.getValue(colosseum.id).audience == ShareAudience.NONE
    }
    coVerify(exactly = 2) { friends.getFriends() }
  }

  @Test
  fun reopeningSharingShowsNewlyAcceptedFriends() {
    val friends = mockk<FriendRepository>()
    val profiles = mockk<PublicProfileRepository>()
    coEvery { friends.getFriends() } returns
        emptyList() andThen
        listOf(Friendship("friendship", "friend"))
    coEvery { profiles.getProfile("friend") } returns PublicProfile("friend", "Alice")
    setSharingRoute(
        FakeSavedGuideRepository(listOf(colosseum)),
        FakeMonumentSharingRepository(),
        friends,
        profiles,
    )

    composeTestRule.onNodeWithTag(C.Tag.collection_card_share).performClick()
    composeTestRule.onNodeWithText("Add friends before sharing a guide.").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_share_cancel).performClick()

    composeTestRule.onNodeWithTag(C.Tag.collection_card_share).performClick()
    composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
    coVerify(exactly = 2) { friends.getFriends() }
  }

  @Test
  fun sharingRouteRetriesFailedFriendLoadAndReportsFailedWrite() {
    val repository = FakeSavedGuideRepository(listOf(colosseum))
    val sharing = FakeMonumentSharingRepository()
    sharing.updateError = IllegalStateException("offline")
    val friends = mockk<FriendRepository>()
    val profiles = mockk<PublicProfileRepository>()
    coEvery { friends.getFriends() } throws
        IllegalStateException("offline") andThen
        listOf(Friendship("friendship", "friend"))
    coEvery { profiles.getProfile("friend") } returns PublicProfile("friend", "Alice")
    setSharingRoute(repository, sharing, friends, profiles)

    composeTestRule.onNodeWithTag(C.Tag.collection_card_share).performClick()
    composeTestRule.onNodeWithText("Retry").performClick()
    composeTestRule.onNodeWithText("Alice").performClick()
    composeTestRule.onNodeWithTag(C.Tag.collection_share_confirm).performClick()

    composeTestRule.onNodeWithText("Couldn’t update sharing. Please try again.").assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.collection_share_status, true)
        .assertTextEquals("Not shared")
    assertTrue(sharing.shareSettings.value.isEmpty())
    coVerify(exactly = 2) { friends.getFriends() }
  }

  @Test
  fun uncheckingLastVisibleFriendClearsStaleRecipients() {
    val sharing = FakeMonumentSharingRepository()
    val friends = mockk<FriendRepository>()
    val profiles = mockk<PublicProfileRepository>()
    coEvery { friends.getFriends() } returns listOf(Friendship("friendship", "friend"))
    coEvery { profiles.getProfile("friend") } returns PublicProfile("friend", "Alice")
    val guide =
        colosseum.copy(
            shareSettings =
                MonumentShareSettings(
                    audience = ShareAudience.SELECTED_FRIENDS,
                    sharedWithUids = setOf("friend", "no-longer-a-friend"),
                )
        )
    setSharingRoute(FakeSavedGuideRepository(listOf(guide)), sharing, friends, profiles)

    composeTestRule.onNodeWithTag(C.Tag.collection_card_share).performClick()
    composeTestRule.onNodeWithText("Alice").performClick()
    composeTestRule.onNodeWithTag(C.Tag.collection_share_confirm).performClick()

    composeTestRule.waitUntil {
      sharing.shareSettings.value.getValue(colosseum.id).audience == ShareAudience.NONE
    }
    assertEquals(
        emptySet<String>(),
        sharing.shareSettings.value.getValue(colosseum.id).sharedWithUids,
    )
  }

  private fun setSharingRoute(
      repository: FakeSavedGuideRepository,
      sharing: FakeMonumentSharingRepository,
      friends: FriendRepository,
      profiles: PublicProfileRepository,
  ) {
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionRoute(
            repository = repository,
            friendRepository = friends,
            publicProfileRepository = profiles,
            monumentSharingProvider = { sharing },
        )
      }
    }
  }

  private fun setRoute(
      repository: FakeSavedGuideRepository,
      profileName: String = "Traveler",
      profileAvatarBytes: ByteArray? = null,
  ) {
    composeTestRule.setContent {
      SampleAppTheme {
        CollectionRoute(
            repository = repository,
            monumentSharingProvider = { FakeMonumentSharingRepository() },
            profileName = profileName,
            profileAvatarBytes = profileAvatarBytes,
        )
      }
    }
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
