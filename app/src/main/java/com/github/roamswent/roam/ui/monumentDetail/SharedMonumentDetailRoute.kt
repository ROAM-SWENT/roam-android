// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.monumentDetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.guide.MonumentSharingRepository
import com.github.roamswent.roam.model.guide.toMonumentGuide
import com.github.roamswent.roam.model.wishlist.FirestoreWishlistRepository
import com.github.roamswent.roam.model.wishlist.WishlistRepository
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.collection.WishlistViewModel
import kotlinx.coroutines.CancellationException

private sealed interface SharedGuideState {
  data object Loading : SharedGuideState

  data object Missing : SharedGuideState

  data object Error : SharedGuideState

  data class Loaded(val guide: Guide) : SharedGuideState
}

/** Shows the monument [guideId] that [ownerUid] shares with the user. */
@Composable
fun SharedMonumentDetailRoute(
    ownerUid: String,
    guideId: String,
    repository: MonumentSharingRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBackArrow: Boolean = false,
    wishlistRepository: WishlistRepository? = null,
) {
  val resolvedWishlistRepository =
      remember(wishlistRepository) { wishlistRepository ?: FirestoreWishlistRepository() }
  val wishlistViewModel: WishlistViewModel = viewModel {
    WishlistViewModel(resolvedWishlistRepository)
  }
  val wishlistState by wishlistViewModel.uiState.collectAsState()
  LaunchedEffect(wishlistViewModel) { wishlistViewModel.loadWishlist() }
  var attempt by remember(ownerUid, guideId, repository) { mutableIntStateOf(0) }
  val state by
      produceState<SharedGuideState>(
          SharedGuideState.Loading,
          ownerUid,
          guideId,
          repository,
          attempt,
      ) {
        value = SharedGuideState.Loading
        value =
            try {
              repository.getSharedGuideFrom(ownerUid, guideId)?.let { SharedGuideState.Loaded(it) }
                  ?: SharedGuideState.Missing
            } catch (exception: CancellationException) {
              throw exception
            } catch (_: Exception) {
              SharedGuideState.Error
            }
      }

  when (val current = state) {
    is SharedGuideState.Loaded -> {
      val monumentGuide = remember(current.guide) { current.guide.toMonumentGuide() }
      MonumentDetailScreen(
          image = rememberAsyncImagePainter(current.guide.photoBytes),
          guide = monumentGuide,
          onBackClick = onBackClick,
          modifier = modifier,
          showSharingControls = false,
          showBackArrow = showBackArrow,
          isWishlisted = (ownerUid to current.guide.id) in wishlistState.guideKeys,
          isWishlistEnabled =
              !wishlistState.isLoading &&
                  wishlistState.error == null &&
                  (ownerUid to current.guide.id) !in wishlistState.pendingGuides,
          onWishlistClick = { wishlistViewModel.toggleGuide(ownerUid, current.guide) },
          showWishlistError = wishlistState.mutationError != null,
          onWishlistErrorShown = wishlistViewModel::clearMutationError,
      )
    }
    SharedGuideState.Loading ->
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(Modifier.testTag(C.Tag.shared_monument_detail_loading))
        }
    SharedGuideState.Error ->
        SavedMonumentDetailMessage(
            messageRes = R.string.saved_monument_detail_error,
            tag = C.Tag.shared_monument_detail_error,
            onBackClick = onBackClick,
            onRetry = { attempt++ },
            modifier = modifier,
            retryTag = C.Tag.shared_monument_detail_retry,
            showBackArrow = showBackArrow,
        )
    SharedGuideState.Missing ->
        SavedMonumentDetailMessage(
            messageRes = R.string.shared_monument_detail_not_found,
            tag = C.Tag.shared_monument_detail_not_found,
            onBackClick = onBackClick,
            onRetry = null,
            modifier = modifier,
            showBackArrow = showBackArrow,
        )
  }
}
