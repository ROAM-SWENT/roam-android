/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.friends

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.resources.C

/**
 * Renders friends and requests exposed by [FriendsViewModel]. Screen callbacks keep rendering
 * independently testable and allow navigation to remain owned by the app.
 */
@Composable
fun FriendsScreen(
    modifier: Modifier = Modifier,
    friends: List<FriendUser> = emptyList(),
    friendRequests: List<FriendRequest> = emptyList(),
    searchResults: List<FriendSearchResult> = emptyList(),
    profileName: String? = null,
    onProfileClicked: () -> Unit = {},
    onFriendClicked: (FriendUser) -> Unit = {},
    onUserSearchChanged: (String) -> Unit = {},
    onAcceptRequest: (FriendRequest) -> Unit = {},
    onRejectRequest: (FriendRequest) -> Unit = {},
    onCancelRequest: (FriendRequest) -> Unit = {},
    onSendRequest: (FriendUser) -> Unit = {},
    onRemoveFriend: (FriendUser) -> Unit = {},
    isMutating: Boolean = false,
    isSearching: Boolean = false,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    message: String? = null,
    onMessageDismiss: () -> Unit = {},
    showAddFriendsInitially: Boolean = false,
) {
  var friendsQuery by remember { mutableStateOf("") }
  var userQuery by remember { mutableStateOf("") }
  var isAddFriendsOpen by
      remember(showAddFriendsInitially) { mutableStateOf(showAddFriendsInitially) }
  val newestRequests =
      remember(friendRequests) {
        friendRequests
            .sortedByDescending(FriendRequest::requestedAtMillis)
            .take(MAX_VISIBLE_REQUESTS)
      }
  val filteredFriends =
      remember(friends, friendsQuery) {
        friends.filter { it.username.contains(friendsQuery.trim(), ignoreCase = true) }
      }

  Box(
      modifier =
          modifier.fillMaxSize().background(screenBackgroundColor()).semantics {
            testTag = C.Tag.friends_screen_container
          }
  ) {
    FriendsContent(
        friends = filteredFriends,
        friendRequests = newestRequests,
        pendingRequestCount = friendRequests.size,
        friendsQuery = friendsQuery,
        onFriendsQueryChanged = { friendsQuery = it },
        profileName = profileName,
        onAddFriendsClicked = { isAddFriendsOpen = true },
        onProfileClicked = onProfileClicked,
        onFriendClicked = onFriendClicked,
        onRemoveFriend = onRemoveFriend,
        onAcceptRequest = onAcceptRequest,
        onRejectRequest = onRejectRequest,
        isMutating = isMutating,
        isLoading = isLoading,
        modifier = Modifier.fillMaxSize().blur(if (isAddFriendsOpen) 8.dp else 0.dp),
    )

    if (isAddFriendsOpen) {
      BackHandler { isAddFriendsOpen = false }
      Box(
          modifier =
              Modifier.fillMaxSize()
                  .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                  .clickable(
                      interactionSource = remember { MutableInteractionSource() },
                      indication = null,
                      onClick = { isAddFriendsOpen = false },
                  )
                  .semantics { testTag = C.Tag.friends_add_dismiss }
      )
      AddFriendsPanel(
          query = userQuery,
          requests = friendRequests,
          searchResults = searchResults,
          isSearching = isSearching,
          hasError = errorMessage != null,
          isMutating = isMutating,
          onQueryChanged = {
            userQuery = it
            onUserSearchChanged(it)
          },
          onAcceptRequest = onAcceptRequest,
          onRejectRequest = onRejectRequest,
          onCancelRequest = onCancelRequest,
          onSendRequest = onSendRequest,
          modifier =
              Modifier.align(Alignment.Center).fillMaxWidth(0.9f).fillMaxHeight(0.78f).pointerInput(
                  Unit
              ) {
                awaitEachGesture {
                  awaitFirstDown(requireUnconsumed = false)
                  waitForUpOrCancellation()
                }
              },
      )
    }

    val visibleMessage = errorMessage ?: message
    if (visibleMessage != null) {
      Snackbar(
          modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
          action = {
            Text(
                text = "Dismiss",
                modifier = Modifier.clickable(onClick = onMessageDismiss),
            )
          },
      ) {
        Text(visibleMessage)
      }
    }
  }
}

private const val MAX_VISIBLE_REQUESTS = 1
