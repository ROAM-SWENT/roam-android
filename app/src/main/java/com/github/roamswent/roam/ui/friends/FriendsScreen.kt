/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 * Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.leaderboard.LeaderboardEntry
import com.github.roamswent.roam.resources.C

/**
 * Renders friends, requests and the friends leaderboard exposed by [FriendsViewModel]. Screen
 * callbacks keep rendering independently testable and allow navigation to remain owned by the app.
 *
 * @param leaderboard Entries in the order and with the ranks given by the repository.
 * @param currentUserUid UID of the signed-in user, whose leaderboard entry is highlighted.
 * @param hasLeaderboardError Whether the last leaderboard load failed; shown in the leaderboard
 *   only, so the friends list stays usable.
 * @param isLeaderboardLoading Whether a leaderboard load is in progress, independently of
 *   [isLoading].
 * @param hasLoadedLeaderboard Whether a leaderboard load has finished, successfully or not. Until
 *   then, and unless it failed, the leaderboard shows its loading spinner.
 * @param onLeaderboardVisibilityChanged Called with whether the Leaderboard segment is shown: on
 *   first composition, on every segment change, and with `false` when the screen leaves the
 *   composition, so the leaderboard is only loaded while it is shown.
 */
@Composable
fun FriendsScreen(
    modifier: Modifier = Modifier,
    friends: List<FriendUser> = emptyList(),
    friendRequests: List<FriendRequest> = emptyList(),
    searchResults: List<FriendSearchResult> = emptyList(),
    profileName: String? = null,
    profileAvatarBytes: ByteArray? = null,
    onProfileClicked: () -> Unit = {},
    onFriendClicked: (FriendUser) -> Unit = {},
    onFriendCollectionClicked: (FriendUser) -> Unit = {},
    onUserSearchChanged: (String) -> Unit = {},
    onAcceptRequest: (FriendRequest) -> Unit = {},
    onRejectRequest: (FriendRequest) -> Unit = {},
    onCancelRequest: (FriendRequest) -> Unit = {},
    onSendRequest: (FriendUser) -> Unit = {},
    onRemoveFriend: (FriendUser) -> Unit = {},
    isMutating: Boolean = false,
    isSearching: Boolean = false,
    isLoading: Boolean = false,
    hasLoaded: Boolean = false,
    onRefresh: () -> Unit = {},
    errorMessage: String? = null,
    message: String? = null,
    onMessageDismiss: () -> Unit = {},
    showAddFriendsInitially: Boolean = false,
    leaderboard: List<LeaderboardEntry> = emptyList(),
    currentUserUid: String? = null,
    hasLeaderboardError: Boolean = false,
    isLeaderboardLoading: Boolean = false,
    hasLoadedLeaderboard: Boolean = false,
    onLeaderboardVisibilityChanged: (Boolean) -> Unit = {},
) {
  var selectedTab by rememberSaveable { mutableStateOf(FriendsTab.Friends) }
  val currentOnLeaderboardVisibilityChanged by rememberUpdatedState(onLeaderboardVisibilityChanged)
  // Also runs for a segment restored after a configuration change or process death.
  LaunchedEffect(selectedTab) {
    currentOnLeaderboardVisibilityChanged(selectedTab == FriendsTab.Leaderboard)
  }
  DisposableEffect(Unit) { onDispose { currentOnLeaderboardVisibilityChanged(false) } }
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
        filterByUsernameQuery(friends, friendsQuery, FriendUser::username)
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
        profileAvatarBytes = profileAvatarBytes,
        onAddFriendsClicked = { isAddFriendsOpen = true },
        onProfileClicked = onProfileClicked,
        onFriendClicked = onFriendClicked,
        onFriendCollectionClicked = onFriendCollectionClicked,
        onRemoveFriend = onRemoveFriend,
        onAcceptRequest = onAcceptRequest,
        onRejectRequest = onRejectRequest,
        isMutating = isMutating,
        isLoading = isLoading,
        hasLoaded = hasLoaded,
        onRefresh = onRefresh,
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        leaderboard = leaderboard,
        currentUserUid = currentUserUid,
        hasLeaderboardError = hasLeaderboardError,
        isLeaderboardLoading = isLeaderboardLoading,
        hasLoadedLeaderboard = hasLoadedLeaderboard,
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
      val dismissLabel = stringResource(R.string.friends_dismiss_action)
      Snackbar(
          modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
          action = {
            Text(
                text = dismissLabel,
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
