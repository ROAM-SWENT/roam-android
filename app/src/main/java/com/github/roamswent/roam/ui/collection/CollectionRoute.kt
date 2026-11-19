// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.roamswent.roam.model.friend.FirestoreFriendRepository
import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.guide.DefaultMonumentSharingProvider
import com.github.roamswent.roam.model.guide.MonumentSharingProvider
import com.github.roamswent.roam.model.guide.SavedGuideRepository
import com.github.roamswent.roam.model.profile.FirestorePublicProfileRepository
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import com.github.roamswent.roam.ui.guide.SavedGuidesViewModel
import com.github.roamswent.roam.ui.home.HomeViewModel

/**
 * Loads the guides saved in [repository] and shows them in [CollectionScreen]. The ViewModel is
 * created once with the first [repository] and keeps it for its lifetime.
 */
@Composable
fun CollectionRoute(
    repository: SavedGuideRepository,
    modifier: Modifier = Modifier,
    onGuideClick: (String) -> Unit = {},
    friendRepository: FriendRepository? = null,
    publicProfileRepository: PublicProfileRepository? = null,
    monumentSharingProvider: MonumentSharingProvider? = null,
    profileName: String = HomeViewModel.DEFAULT_USER_NAME,
    onProfileClicked: () -> Unit = {},
    onPickFromGallery: () -> Unit = {},
) {
  val resolvedFriendRepository =
      remember(friendRepository) { friendRepository ?: FirestoreFriendRepository() }
  val resolvedProfileRepository =
      remember(publicProfileRepository) {
        publicProfileRepository ?: FirestorePublicProfileRepository()
      }
  val sharingRepository =
      remember(monumentSharingProvider) {
        (monumentSharingProvider ?: DefaultMonumentSharingProvider()).repository()
      }
  val viewModel: SavedGuidesViewModel = viewModel {
    SavedGuidesViewModel(repository, sharingRepository)
  }
  val sharingViewModel: CollectionSharingViewModel = viewModel {
    CollectionSharingViewModel(resolvedFriendRepository, resolvedProfileRepository)
  }
  val state by viewModel.uiState.collectAsState()
  val sharingState by sharingViewModel.uiState.collectAsState()

  LaunchedEffect(viewModel) { viewModel.loadSavedGuides() }

  CollectionScreen(
      state = state,
      modifier = modifier,
      onGuideClick = onGuideClick,
      onDeleteGuide = viewModel::deleteGuide,
      onDeleteErrorShown = viewModel::clearDeleteError,
      onToggleFavorite = viewModel::toggleFavorite,
      onRestoreFavorite = { viewModel.setFavorite(it, isFavorite = true) },
      onFavoriteErrorShown = viewModel::clearFavoriteError,
      onShowFavoritesOnlyChange = viewModel::setShowFavoritesOnly,
      onSearchQueryChange = viewModel::onSearchQueryChange,
      onOpenSharing = sharingViewModel::loadFriends,
      friends = sharingState.friends,
      isLoadingFriends = sharingState.isLoadingFriends,
      friendsError = sharingState.friendsError,
      onRetryLoadingFriends = sharingViewModel::retryLoadingFriends,
      onSetShareAudience = viewModel::setShareAudience,
      onSharingErrorShown = viewModel::clearSharingError,
      profileName = profileName,
      onProfileClicked = onProfileClicked,
      onPickFromGallery = onPickFromGallery,
  )
}
