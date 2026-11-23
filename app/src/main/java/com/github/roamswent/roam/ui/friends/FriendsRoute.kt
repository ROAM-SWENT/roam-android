/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 * Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
 */

package com.github.roamswent.roam.ui.friends

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.roamswent.roam.model.friend.FirestoreFriendRepository
import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.leaderboard.FirestoreLeaderboardRepository
import com.github.roamswent.roam.model.leaderboard.LeaderboardRepository
import com.github.roamswent.roam.model.profile.FirestorePublicProfileRepository
import com.github.roamswent.roam.model.profile.PublicProfileRepository

private val NO_PROFILE_CLICK: () -> Unit = {}
private val NO_FRIEND_CLICK: (FriendUser) -> Unit = {}

/**
 * Friends tab: builds the repositories that were not injected and connects [FriendsViewModel] to
 * [FriendsScreen]. The default leaderboard repository reuses the route's friend and profile
 * repositories rather than building its own.
 */
@Composable
fun FriendsRoute(
    modifier: Modifier = Modifier,
    profileName: String? = null,
    profileAvatarBytes: ByteArray? = null,
    onProfileClicked: () -> Unit = NO_PROFILE_CLICK,
    onFriendClicked: (FriendUser) -> Unit = NO_FRIEND_CLICK,
    onFriendCollectionClicked: (FriendUser) -> Unit = NO_FRIEND_CLICK,
    friendRepository: FriendRepository? = null,
    publicProfileRepository: PublicProfileRepository? = null,
    leaderboardRepository: LeaderboardRepository? = null,
) {
  val resolvedFriendRepository =
      remember(friendRepository) { friendRepository ?: FirestoreFriendRepository() }
  val resolvedProfileRepository =
      remember(publicProfileRepository) {
        publicProfileRepository ?: FirestorePublicProfileRepository()
      }
  val resolvedLeaderboardRepository =
      remember(leaderboardRepository, resolvedFriendRepository, resolvedProfileRepository) {
        leaderboardRepository
            ?: FirestoreLeaderboardRepository(
                friendRepository = resolvedFriendRepository,
                publicProfileRepository = resolvedProfileRepository,
            )
      }
  val viewModel: FriendsViewModel = viewModel {
    FriendsViewModel(
        resolvedFriendRepository,
        resolvedProfileRepository,
        resolvedLeaderboardRepository,
    )
  }
  val state by viewModel.uiState.collectAsState()

  LaunchedEffect(viewModel) { viewModel.loadFriends() }

  FriendsScreen(
      modifier = modifier,
      friends = state.friends,
      friendRequests = state.incomingRequests,
      searchResults = state.searchResults,
      profileName = profileName,
      profileAvatarBytes = profileAvatarBytes,
      onProfileClicked = onProfileClicked,
      onFriendClicked = onFriendClicked,
      onFriendCollectionClicked = onFriendCollectionClicked,
      onUserSearchChanged = viewModel::searchUsers,
      onAcceptRequest = viewModel::acceptRequest,
      onRejectRequest = viewModel::rejectRequest,
      onCancelRequest = viewModel::cancelRequest,
      onSendRequest = viewModel::sendRequest,
      onRemoveFriend = viewModel::removeFriend,
      isMutating = state.isMutating,
      isSearching = state.isSearching,
      isLoading = state.isLoading,
      hasLoaded = state.hasLoaded,
      onRefresh = viewModel::refresh,
      errorMessage = state.error?.localizedMessage,
      message = state.message,
      onMessageDismiss = viewModel::clearMessage,
      leaderboard = state.leaderboard,
      currentUserUid = state.currentUserUid,
      hasLeaderboardError = state.leaderboardError != null,
  )
}
