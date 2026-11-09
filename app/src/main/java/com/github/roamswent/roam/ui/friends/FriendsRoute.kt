/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
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
import com.github.roamswent.roam.model.profile.FirestorePublicProfileRepository
import com.github.roamswent.roam.model.profile.PublicProfileRepository

@Composable
fun FriendsRoute(
    modifier: Modifier = Modifier,
    profileName: String? = null,
    onProfileClicked: () -> Unit = {},
    onFriendClicked: (FriendUser) -> Unit = {},
    friendRepository: FriendRepository? = null,
    publicProfileRepository: PublicProfileRepository? = null,
) {
  val resolvedFriendRepository =
      remember(friendRepository) { friendRepository ?: FirestoreFriendRepository() }
  val resolvedProfileRepository =
      remember(publicProfileRepository) {
        publicProfileRepository ?: FirestorePublicProfileRepository()
      }
  val viewModel: FriendsViewModel = viewModel {
    FriendsViewModel(resolvedFriendRepository, resolvedProfileRepository)
  }
  val state by viewModel.uiState.collectAsState()

  LaunchedEffect(viewModel) { viewModel.loadFriends() }

  FriendsScreen(
      modifier = modifier,
      friends = state.friends,
      friendRequests = state.incomingRequests,
      searchResults = state.searchResults,
      profileName = profileName,
      onProfileClicked = onProfileClicked,
      onFriendClicked = onFriendClicked,
      onUserSearchChanged = viewModel::searchUsers,
      onAcceptRequest = viewModel::acceptRequest,
      onRejectRequest = viewModel::rejectRequest,
      onCancelRequest = viewModel::cancelRequest,
      onSendRequest = viewModel::sendRequest,
      onRemoveFriend = viewModel::removeFriend,
      isMutating = state.isMutating,
      isSearching = state.isSearching,
      isLoading = state.isLoading,
      errorMessage = state.error?.localizedMessage,
      message = state.message,
      onMessageDismiss = viewModel::clearMessage,
  )
}
