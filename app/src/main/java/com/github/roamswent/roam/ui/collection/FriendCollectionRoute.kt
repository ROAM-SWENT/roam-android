// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.guide.MonumentSharingRepository
import com.github.roamswent.roam.ui.wishlist.WishlistViewModel

/** Shows the monuments [ownerUid] shares with the user, in a read-only [CollectionScreen]. */
@Composable
fun FriendCollectionRoute(
    ownerUid: String,
    ownerName: String,
    repository: MonumentSharingRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onGuideClick: (String) -> Unit = {},
    wishlistViewModel: WishlistViewModel = viewModel(),
) {
  val viewModel: FriendCollectionViewModel =
      viewModel(key = ownerUid) { FriendCollectionViewModel(repository, ownerUid) }
  val state by viewModel.uiState.collectAsState()
  val wishlistedIds by wishlistViewModel.wishlistedIds.collectAsState()

  LaunchedEffect(viewModel) { viewModel.load() }

  CollectionScreen(
      state = state,
      modifier = modifier,
      onGuideClick = onGuideClick,
      onSearchQueryChange = viewModel::onSearchQueryChange,
      title = stringResource(R.string.friend_collection_title, ownerName),
      profileName = ownerName,
      readOnly = true,
      wishlistedGuideIds = wishlistedIds,
      onToggleWishlist = wishlistViewModel::toggleWishlist,
      onBackClick = onBackClick,
      onRetryGuides = viewModel::load,
  )
}
