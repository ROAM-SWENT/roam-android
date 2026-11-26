// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.roamswent.roam.model.friend.FirestoreFriendRepository
import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.guide.DefaultMonumentSharingProvider
import com.github.roamswent.roam.model.guide.MonumentSharingProvider
import com.github.roamswent.roam.model.guide.SavedGuideRepository
import com.github.roamswent.roam.model.location.AndroidLocationNameResolver
import com.github.roamswent.roam.model.location.LocationNameResolver
import com.github.roamswent.roam.model.profile.FirestorePublicProfileRepository
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import com.github.roamswent.roam.model.wishlist.FirestoreFriendWishlistRepository
import com.github.roamswent.roam.model.wishlist.FirestoreWishlistRepository
import com.github.roamswent.roam.model.wishlist.FriendScannedMonumentsSource
import com.github.roamswent.roam.model.wishlist.FriendWishlistedMonumentsSource
import com.github.roamswent.roam.model.wishlist.WishlistRepository
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
    profileAvatarBytes: ByteArray? = null,
    onProfileClicked: () -> Unit = {},
    onPickFromGallery: () -> Unit = {},
    wishlistRepository: WishlistRepository? = null,
    locationNameResolver: LocationNameResolver? = null,
    onWishlistGuideClick: (String, String) -> Unit = { _, _ -> },
    friendScansSource: FriendScannedMonumentsSource? = null,
    friendWishlistsSource: FriendWishlistedMonumentsSource? = null,
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
  val resolvedWishlistRepository =
      remember(wishlistRepository) { wishlistRepository ?: FirestoreWishlistRepository() }
  val context = LocalContext.current
  val resolvedLocationNameResolver =
      remember(locationNameResolver, context) {
        locationNameResolver ?: AndroidLocationNameResolver(context)
      }
  val resolvedFriendScansSource =
      remember(
          friendScansSource,
          resolvedFriendRepository,
          sharingRepository,
          resolvedProfileRepository,
      ) {
        friendScansSource
            ?: FriendScannedMonumentsSource(
                resolvedFriendRepository,
                sharingRepository,
                resolvedProfileRepository,
            )
      }
  val resolvedFriendWishlistsSource =
      remember(friendWishlistsSource, resolvedFriendRepository, resolvedProfileRepository) {
        friendWishlistsSource
            ?: FriendWishlistedMonumentsSource(
                resolvedFriendRepository,
                FirestoreFriendWishlistRepository(),
                resolvedProfileRepository,
            )
      }
  val wishlistViewModel: WishlistViewModel = viewModel {
    WishlistViewModel(
        resolvedWishlistRepository,
        resolvedFriendScansSource,
        resolvedFriendWishlistsSource,
        resolvedLocationNameResolver,
    )
  }
  val state by viewModel.uiState.collectAsState()
  val sharingState by sharingViewModel.uiState.collectAsState()
  val wishlistState by wishlistViewModel.uiState.collectAsState()

  LaunchedEffect(viewModel) { viewModel.loadSavedGuides() }
  LaunchedEffect(wishlistViewModel) { wishlistViewModel.loadWishlist() }

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
      profileAvatarBytes = profileAvatarBytes,
      onProfileClicked = onProfileClicked,
      onPickFromGallery = onPickFromGallery,
      wishlistState = wishlistState,
      onSetWishlisted = wishlistViewModel::setWishlisted,
      onRetryWishlist = wishlistViewModel::retryLoadingWishlist,
      onWishlistErrorShown = wishlistViewModel::clearMutationError,
      onRetryWishlistGuides = wishlistViewModel::refreshAvailability,
      onWishlistSuccessShown = wishlistViewModel::clearMutationSuccess,
      onWishlistGuideClick = onWishlistGuideClick,
      onRetryWishlistFriends = wishlistViewModel::retryLoadingFriends,
      onWishlistShown = wishlistViewModel::startFriendObservation,
      onWishlistHidden = wishlistViewModel::stopFriendObservation,
  )
}
