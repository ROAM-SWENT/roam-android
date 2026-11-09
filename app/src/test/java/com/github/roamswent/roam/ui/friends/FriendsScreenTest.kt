/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.friends

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
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
  @get:Rule val composeTestRule = createComposeRule()

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
  fun showsOnlyThreeNewestRequestsAndShowsTheirCount() {
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
    composeTestRule.onNodeWithText("Middle").assertIsDisplayed()
    composeTestRule.onNodeWithText("Second").assertIsDisplayed()
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
}
