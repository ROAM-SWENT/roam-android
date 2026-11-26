// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.guide.MonumentSharingRepository
import com.github.roamswent.roam.model.wishlist.FirestoreWishlistRepository
import com.github.roamswent.roam.model.wishlist.WishlistRepository

/** Shows the monuments [ownerUid] shares with the user, in a read-only [CollectionScreen]. */
@Composable
fun FriendCollectionRoute(
    ownerUid: String,
    ownerName: String,
    repository: MonumentSharingRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onGuideClick: (String) -> Unit = {},
    wishlistRepository: WishlistRepository? = null,
) {
  val viewModel: FriendCollectionViewModel =
      viewModel(key = ownerUid) { FriendCollectionViewModel(repository, ownerUid) }
  val state by viewModel.uiState.collectAsState()
  val resolvedWishlistRepository =
      remember(wishlistRepository) { wishlistRepository ?: FirestoreWishlistRepository() }
  val wishlistViewModel: WishlistViewModel = viewModel {
    WishlistViewModel(resolvedWishlistRepository)
  }
  val wishlistState by wishlistViewModel.uiState.collectAsState()

  LaunchedEffect(viewModel) { viewModel.load() }
  LaunchedEffect(wishlistViewModel) { wishlistViewModel.loadWishlistEntries() }

  CollectionScreen(
      state = state,
      modifier = modifier,
      onGuideClick = onGuideClick,
      onSearchQueryChange = viewModel::onSearchQueryChange,
      title = stringResource(R.string.friend_collection_title, ownerName),
      profileName = ownerName,
      readOnly = true,
      onBackClick = onBackClick,
      onRetryGuides = viewModel::load,
      wishlistState = wishlistState,
      isGuideWishlisted = { guide -> (ownerUid to guide.id) in wishlistState.guideKeys },
      isGuideWishlistEnabled = { !wishlistState.isLoading && wishlistState.error == null },
      onToggleWishlist = { guide -> wishlistViewModel.toggleGuide(ownerUid, guide) },
      onRetryWishlist = wishlistViewModel::retryLoadingWishlist,
      onWishlistErrorShown = wishlistViewModel::clearMutationError,
      onWishlistSuccessShown = wishlistViewModel::clearMutationSuccess,
  )
}
