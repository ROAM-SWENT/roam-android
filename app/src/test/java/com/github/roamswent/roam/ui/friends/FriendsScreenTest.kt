/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 * Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
 */

package com.github.roamswent.roam.ui.friends

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.leaderboard.LeaderboardEntry
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class FriendsScreenTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun rendersProfileAndFiltersFriendsByUsername() {
    val friends =
        listOf(
            FriendUser(id = "1", username = "Ada"),
            FriendUser(id = "2", username = "Beatrice"),
        )
    var clickedFriend: FriendUser? = null
    composeTestRule.setContent {
      SampleAppTheme {
        FriendsScreen(
            friends = friends,
            profileName = "Grace Hopper",
            onFriendClicked = { clickedFriend = it },
        )
      }
    }

    composeTestRule
        .onNodeWithTag(C.Tag.friends_title)
        .assertIsDisplayed()
        .assertTextEquals("Friends")
    composeTestRule
        .onNodeWithTag(C.Tag.home_profile_button)
        .assertIsDisplayed()
        .assertTextEquals("G")
    composeTestRule.onNodeWithText("Ada").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.friends_search).performTextInput("bea")
    composeTestRule.onNodeWithText("Beatrice").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Ada").assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.friends_friend_row).performTouchInput {
      click(Offset(30f, 30f))
    }

    assertEquals(friends[1], clickedFriend)
  }

  @Test
  fun pullingDownAtTopRefreshesAndKeepsFriendsScreenVisible() {
    var refreshCount = 0
    var isLoading by mutableStateOf(false)
    composeTestRule.setContent {
      SampleAppTheme {
        FriendsScreen(
            friends = listOf(FriendUser("friend", "Ada")),
            isLoading = isLoading,
            hasLoaded = true,
            onRefresh = { refreshCount++ },
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.friends_list).performTouchInput { swipeDown() }

    assertEquals(1, refreshCount)
    composeTestRule.onNodeWithTag(C.Tag.friends_title).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.friends_friend_row).assertIsDisplayed()
    composeTestRule.runOnIdle { isLoading = true }
    composeTestRule.onNodeWithTag(C.Tag.friends_refresh_indicator).assertIsDisplayed()
  }

  @Test
  fun showsRefreshIndicatorForAnEmptyFriendsListAfterInitialLoad() {
    composeTestRule.setContent {
      SampleAppTheme { FriendsScreen(isLoading = true, hasLoaded = true) }
    }

    composeTestRule.onNodeWithTag(C.Tag.friends_refresh_indicator).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.friends_initial_loading).assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.friends_list).assertIsDisplayed()
  }

  @Test
  fun showsCenteredSpinnerOnInitialEmptyLoad() {
    composeTestRule.setContent {
      SampleAppTheme { FriendsScreen(isLoading = true, hasLoaded = false) }
    }

    composeTestRule.onNodeWithTag(C.Tag.friends_initial_loading).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.friends_list).assertCountEquals(0)
  }

  @Test
  fun showsOnlyNewestRequestsAndShowsTheirCount() {
    val requests =
        listOf(
            FriendRequest(FriendUser("old", "Oldest"), 1L),
            FriendRequest(FriendUser("newest", "Newest"), 4L),
            FriendRequest(FriendUser("middle", "Middle"), 3L),
            FriendRequest(FriendUser("second", "Second"), 2L),
        )
    composeTestRule.setContent { SampleAppTheme { FriendsScreen(friendRequests = requests) } }

    composeTestRule
        .onNodeWithTag(C.Tag.friends_pending_request_count)
        .assertIsDisplayed()
        .assertContentDescriptionEquals("4 pending friend requests")
    composeTestRule.onNodeWithText("Newest").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Middle").assertCountEquals(0)
    composeTestRule.onAllNodesWithText("Second").assertCountEquals(0)
    composeTestRule.onAllNodesWithText("Oldest").assertCountEquals(0)
  }

  @Test
  fun addFriendsPanelShowsRequestsSearchResultsAndDismissesOnScrimClick() {
    val requests = listOf(FriendRequest(FriendUser("request", "Request User"), 1L))
    val searchResults = listOf(FriendSearchResult(FriendUser("result", "Search Result")))
    var lastSearchQuery: String? = null
    composeTestRule.setContent {
      SampleAppTheme {
        FriendsScreen(
            friendRequests = requests,
            searchResults = searchResults,
            onUserSearchChanged = { lastSearchQuery = it },
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.friends_add_button).performClick()
    composeTestRule.onNodeWithTag(C.Tag.friends_user_search).assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Request User").assertCountEquals(2)
    composeTestRule.onAllNodesWithTag(C.Tag.friends_add_user_row).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(C.Tag.friends_accept_request).assertCountEquals(2)
    composeTestRule.onAllNodesWithTag(C.Tag.friends_reject_request).assertCountEquals(2)
    composeTestRule.onNodeWithTag(C.Tag.friends_user_search).performTextInput("search")
    composeTestRule.onNodeWithText("Search Result").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.friends_add_user_row).assertExists()
    composeTestRule.onNodeWithTag(C.Tag.friends_add_user_button).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.friends_accept_request).assertCountEquals(1)
    composeTestRule.onAllNodesWithTag(C.Tag.friends_reject_request).assertCountEquals(1)
    assertEquals("search", lastSearchQuery)

    composeTestRule.onNodeWithTag(C.Tag.friends_add_dismiss).performTouchInput {
      click(Offset(1f, 1f))
    }
    composeTestRule.onAllNodesWithText("Search Result").assertCountEquals(0)
  }

  @Test
  fun backButtonDismissesAddFriendsPanelAndKeepsFriendsScreenOpen() {
    composeTestRule.setContent { SampleAppTheme { FriendsScreen(showAddFriendsInitially = true) } }

    composeTestRule.onNodeWithTag(C.Tag.friends_user_search).assertIsDisplayed()
    composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

    composeTestRule.onNodeWithTag(C.Tag.friends_screen_container).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.friends_title).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.friends_user_search).assertCountEquals(0)
  }

  @Test
  fun searchedFriendShowsCancelOrRequestActionsBasedOnRelationship() {
    val results =
        listOf(
            FriendSearchResult(
                user = FriendUser("outgoing", "Outgoing User"),
                outgoingRequestId = "outgoing-request",
            ),
            FriendSearchResult(user = FriendUser("new", "New User")),
        )
    composeTestRule.setContent {
      SampleAppTheme { FriendsScreen(searchResults = results, showAddFriendsInitially = true) }
    }

    composeTestRule.onNodeWithTag(C.Tag.friends_user_search).performTextInput("user")
    composeTestRule.onNodeWithText("Outgoing User").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.friends_cancel_request).assertIsDisplayed()
    composeTestRule.onNodeWithText("New User").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.friends_add_user_button).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.friends_add_user_button).assertCountEquals(1)
  }

  @Test
  fun addFriendsPanelInvokesActionsForEachSearchRelationship() {
    val incomingUser = FriendUser("incoming", "Incoming User")
    val outgoingUser = FriendUser("outgoing", "Outgoing User")
    val friendUser = FriendUser("friend", "Existing Friend")
    val newUser = FriendUser("new", "New User")
    val accepted = mutableListOf<FriendRequest>()
    val rejected = mutableListOf<FriendRequest>()
    val cancelled = mutableListOf<FriendRequest>()
    val sent = mutableListOf<FriendUser>()
    val results =
        listOf(
            FriendSearchResult(incomingUser, incomingRequestId = "incoming-request"),
            FriendSearchResult(outgoingUser, outgoingRequestId = "outgoing-request"),
            FriendSearchResult(friendUser, isFriend = true),
            FriendSearchResult(newUser),
        )
    composeTestRule.setContent {
      SampleAppTheme {
        FriendsScreen(
            searchResults = results,
            showAddFriendsInitially = true,
            onAcceptRequest = accepted::add,
            onRejectRequest = rejected::add,
            onCancelRequest = cancelled::add,
            onSendRequest = sent::add,
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.friends_user_search).performTextInput("user")
    composeTestRule.onNodeWithText("Incoming User").assertIsDisplayed()
    composeTestRule.onNodeWithText("Outgoing User").assertIsDisplayed()
    composeTestRule.onNodeWithText("Existing Friend").assertIsDisplayed()
    composeTestRule.onNodeWithText("New User").assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.friends_accept_request)[0].performClick()
    composeTestRule.onAllNodesWithTag(C.Tag.friends_reject_request)[0].performClick()
    composeTestRule.onNodeWithTag(C.Tag.friends_cancel_request).performClick()
    composeTestRule.onNodeWithTag(C.Tag.friends_add_user_button).performClick()

    assertEquals(
        listOf(FriendRequest(incomingUser, id = "incoming-request")),
        accepted,
    )
    assertEquals(
        listOf(FriendRequest(incomingUser, id = "incoming-request")),
        rejected,
    )
    assertEquals(
        listOf(FriendRequest(outgoingUser, id = "outgoing-request")),
        cancelled,
    )
    assertEquals(listOf(newUser), sent)
  }

  @Test
  fun addFriendsPanelShowsEmptyRequestsAndSearchProgressStates() {
    var isSearching by mutableStateOf(true)
    var errorMessage by mutableStateOf<String?>(null)
    composeTestRule.setContent {
      SampleAppTheme {
        FriendsScreen(
            showAddFriendsInitially = true,
            isSearching = isSearching,
            errorMessage = errorMessage,
        )
      }
    }

    composeTestRule.onNodeWithText("Add friends").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.friends_user_search).performTextInput("nobody")
    val searchingText = composeTestRule.activity.getString(R.string.friends_searching)
    composeTestRule.onNodeWithText(searchingText).assertIsDisplayed()
    composeTestRule.onAllNodesWithText("No users found").assertCountEquals(0)

    composeTestRule.runOnIdle { isSearching = false }
    composeTestRule.onNodeWithText("No users found").assertIsDisplayed()
    composeTestRule.onAllNodesWithText(searchingText).assertCountEquals(0)

    composeTestRule.runOnIdle { errorMessage = "Search failed" }
    composeTestRule.onNodeWithText("Search failed").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("No users found").assertCountEquals(0)
  }

  @Test
  fun deletingFriendRequiresConfirmation() {
    val friend = FriendUser("friend-id", "Ada")
    val removedFriends = mutableListOf<FriendUser>()
    composeTestRule.setContent {
      SampleAppTheme {
        FriendsScreen(
            friends = listOf(friend),
            onRemoveFriend = { removedFriends.add(it) },
        )
      }
    }

    composeTestRule.onNodeWithContentDescription("More options for Ada").performClick()
    composeTestRule.onNodeWithTag(C.Tag.friends_remove_friend).assertIsDisplayed().performClick()
    composeTestRule.onNodeWithText("Remove friend?").assertIsDisplayed()
    composeTestRule.onNodeWithText("Are you sure you want to delete Ada?").assertIsDisplayed()
    composeTestRule.onNodeWithText("Cancel").performClick()
    assertEquals(emptyList<FriendUser>(), removedFriends)
    composeTestRule.onAllNodesWithText("Remove friend?").assertCountEquals(0)

    composeTestRule.onNodeWithContentDescription("More options for Ada").performClick()
    composeTestRule.onNodeWithTag(C.Tag.friends_remove_friend).performClick()
    composeTestRule.onNodeWithText("Delete friend").performClick()

    assertEquals(listOf(friend), removedFriends)
  }

  @Test
  fun viewCollectionButtonReportsTheFriend() {
    var clicked: FriendUser? = null
    composeTestRule.setContent {
      SampleAppTheme {
        FriendsScreen(
            friends = listOf(FriendUser(id = "1", username = "Ada")),
            onFriendCollectionClicked = { clicked = it },
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.friends_view_collection).performClick()

    assertEquals("1", clicked?.id)
  }

  private val self = LeaderboardEntry("self", "Traveler", verifiedMonumentCount = 3, rank = 2)
  private val friend = LeaderboardEntry("friend", "Ada", verifiedMonumentCount = 5, rank = 1)

  private fun string(id: Int, vararg args: Any) = composeTestRule.activity.getString(id, *args)

  private fun openLeaderboard() {
    composeTestRule.onNodeWithTag(C.Tag.friends_tab_leaderboard).performClick()
  }

  @Test
  fun toggleSwitchesBetweenFriendsAndLeaderboard() {
    composeTestRule.setContent {
      SampleAppTheme {
        FriendsScreen(
            friends = listOf(FriendUser("friend", "Ada")),
            leaderboard = listOf(friend, self),
            currentUserUid = "self",
            hasLoaded = true,
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.friends_tab_friends).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.friends_list).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.friends_leaderboard_list).assertCountEquals(0)

    openLeaderboard()

    composeTestRule.onNodeWithTag(C.Tag.friends_tab_leaderboard).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.friends_tab_friends).assertIsNotSelected()
    composeTestRule.onNodeWithTag(C.Tag.friends_leaderboard_list).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.friends_list).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(C.Tag.friends_search).assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.friends_title).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.friends_add_button).assertIsDisplayed()

    composeTestRule.onNodeWithTag(C.Tag.friends_tab_friends).performClick()

    composeTestRule.onNodeWithTag(C.Tag.friends_list).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.friends_search).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.friends_leaderboard_list).assertCountEquals(0)
  }

  @Test
  fun leaderboardShowsRankUsernameAndMonumentCountInGivenOrder() {
    val tied = LeaderboardEntry("tied", "Zed", verifiedMonumentCount = 1, rank = 3)
    composeTestRule.setContent {
      SampleAppTheme {
        FriendsScreen(
            leaderboard = listOf(friend, self, tied),
            currentUserUid = "self",
            hasLoaded = true,
        )
      }
    }
    openLeaderboard()

    composeTestRule.onAllNodesWithTag(C.Tag.friends_leaderboard_row).assertCountEquals(2)
    composeTestRule
        .onAllNodesWithTag(C.Tag.friends_leaderboard_row)[0]
        .assert(hasText("#1"))
        .assert(hasText("Ada"))
        .assert(hasText("5 monuments"))
        .assertContentDescriptionEquals(string(R.string.friends_leaderboard_rank_description, 1))
    composeTestRule
        .onAllNodesWithTag(C.Tag.friends_leaderboard_row)[1]
        .assert(hasText("#3"))
        .assert(hasText("Zed"))
        .assert(hasText("1 monument"))
    composeTestRule
        .onNodeWithTag(C.Tag.friends_leaderboard_current_user_row)
        .assert(hasText("#2"))
        .assert(hasText("Traveler"))
        .assert(hasText("3 monuments"))
  }

  @Test
  fun currentUserRowIsIdentifiedByUidOnly() {
    // Another user shares the current user's username: only the UID decides the highlight.
    val namesake = LeaderboardEntry("other", "Traveler", verifiedMonumentCount = 9, rank = 1)
    composeTestRule.setContent {
      SampleAppTheme {
        FriendsScreen(
            leaderboard = listOf(namesake, self),
            currentUserUid = "self",
            hasLoaded = true,
        )
      }
    }
    openLeaderboard()

    val you = string(R.string.friends_leaderboard_you)
    composeTestRule
        .onNodeWithTag(C.Tag.friends_leaderboard_current_user_row)
        .assertIsDisplayed()
        .assert(hasText("#2"))
        .assert(hasText(you))
    composeTestRule
        .onNodeWithTag(C.Tag.friends_leaderboard_row)
        .assert(hasText("#1"))
        .assert(hasText(you).not())
    composeTestRule.onAllNodesWithText(you).assertCountEquals(1)
  }

  @Test
  fun leaderboardWithOnlyCurrentUserShowsEmptyStateAndOwnRow() {
    composeTestRule.setContent {
      SampleAppTheme {
        FriendsScreen(leaderboard = listOf(self), currentUserUid = "self", hasLoaded = true)
      }
    }
    openLeaderboard()

    composeTestRule.onNodeWithTag(C.Tag.friends_leaderboard_empty).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(string(R.string.friends_leaderboard_empty_message))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.friends_leaderboard_current_user_row).assertIsDisplayed()
  }

  @Test
  fun emptyLeaderboardShowsEmptyState() {
    composeTestRule.setContent {
      SampleAppTheme { FriendsScreen(currentUserUid = "self", hasLoaded = true) }
    }
    openLeaderboard()

    composeTestRule.onNodeWithTag(C.Tag.friends_leaderboard_empty).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.friends_leaderboard_row).assertCountEquals(0)
  }

  @Test
  fun emptyStateActionOpensAddFriendsPanel() {
    composeTestRule.setContent {
      SampleAppTheme {
        FriendsScreen(leaderboard = listOf(self), currentUserUid = "self", hasLoaded = true)
      }
    }
    openLeaderboard()
    composeTestRule.onAllNodesWithTag(C.Tag.friends_user_search).assertCountEquals(0)

    composeTestRule.onNodeWithTag(C.Tag.friends_leaderboard_add_friends).performClick()

    composeTestRule.onNodeWithTag(C.Tag.friends_user_search).assertIsDisplayed()
  }

  @Test
  fun leaderboardWithFriendsDoesNotShowEmptyState() {
    composeTestRule.setContent {
      SampleAppTheme {
        FriendsScreen(
            leaderboard = listOf(friend, self),
            currentUserUid = "self",
            hasLoaded = true,
        )
      }
    }
    openLeaderboard()

    composeTestRule.onNodeWithTag(C.Tag.friends_leaderboard_row).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.friends_leaderboard_empty).assertCountEquals(0)
  }

  @Test
  fun pullingDownRefreshesInLeaderboardMode() {
    var refreshCount = 0
    var isLoading by mutableStateOf(false)
    composeTestRule.setContent {
      SampleAppTheme {
        FriendsScreen(
            leaderboard = listOf(friend, self),
            currentUserUid = "self",
            isLoading = isLoading,
            hasLoaded = true,
            onRefresh = { refreshCount++ },
        )
      }
    }
    openLeaderboard()

    composeTestRule.onNodeWithTag(C.Tag.friends_leaderboard_list).performTouchInput { swipeDown() }

    assertEquals(1, refreshCount)
    composeTestRule.runOnIdle { isLoading = true }
    composeTestRule.onNodeWithTag(C.Tag.friends_refresh_indicator).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.friends_leaderboard_list).assertIsDisplayed()
  }

  @Test
  fun leaderboardErrorIsShownInLeaderboardWithoutAffectingFriends() {
    composeTestRule.setContent {
      SampleAppTheme {
        FriendsScreen(
            friends = listOf(FriendUser("friend", "Ada")),
            leaderboard = listOf(friend, self),
            currentUserUid = "self",
            hasLoaded = true,
            hasLeaderboardError = true,
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.friends_friend_row).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.friends_search).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.friends_leaderboard_error).assertCountEquals(0)

    openLeaderboard()

    composeTestRule.onNodeWithTag(C.Tag.friends_leaderboard_error).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.friends_leaderboard_error)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.friends_leaderboard_row).assertIsDisplayed()

    composeTestRule.onNodeWithTag(C.Tag.friends_tab_friends).performClick()
    composeTestRule.onNodeWithTag(C.Tag.friends_friend_row).assertIsDisplayed()
  }

  @Test
  fun leaderboardErrorWithNoEntriesReplacesEmptyState() {
    composeTestRule.setContent {
      SampleAppTheme {
        FriendsScreen(currentUserUid = "self", hasLoaded = true, hasLeaderboardError = true)
      }
    }
    openLeaderboard()

    composeTestRule.onNodeWithTag(C.Tag.friends_leaderboard_error).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.friends_leaderboard_empty).assertCountEquals(0)
  }

  @Test
  fun leaderboardShowsSpinnerOnInitialLoad() {
    composeTestRule.setContent {
      SampleAppTheme { FriendsScreen(isLoading = true, hasLoaded = false) }
    }
    openLeaderboard()

    composeTestRule.onNodeWithTag(C.Tag.friends_initial_loading).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.friends_leaderboard_empty).assertCountEquals(0)
  }
}
