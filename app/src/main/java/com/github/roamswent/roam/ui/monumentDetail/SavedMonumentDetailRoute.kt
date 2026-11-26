// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.monumentDetail

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.friend.FirestoreFriendRepository
import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.guide.DefaultMonumentSharingProvider
import com.github.roamswent.roam.model.guide.MonumentSharingProvider
import com.github.roamswent.roam.model.guide.SavedGuideRepository
import com.github.roamswent.roam.model.guide.ShareAudience
import com.github.roamswent.roam.model.guide.SharingEligibility
import com.github.roamswent.roam.model.guide.toMonumentGuide
import com.github.roamswent.roam.model.profile.FirestorePublicProfileRepository
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.collection.CollectionSharingViewModel
import com.github.roamswent.roam.ui.collection.GuideSharingDialog
import com.github.roamswent.roam.ui.guide.SavedGuidesViewModel

/**
 * Shows the guide saved under [guideId] in [MonumentDetailScreen], with a spinner while it loads,
 * and a message when it cannot be loaded or no longer exists. The guide is loaded once, when the
 * ViewModel is created, so it is not reloaded on recomposition or configuration changes.
 */
@Composable
fun SavedMonumentDetailRoute(
    guideId: String,
    repository: SavedGuideRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    friendRepository: FriendRepository? = null,
    publicProfileRepository: PublicProfileRepository? = null,
    monumentSharingProvider: MonumentSharingProvider? = null,
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
    SavedGuidesViewModel(repository, sharingRepository).also { it.loadGuide(guideId) }
  }
  val sharingViewModel: CollectionSharingViewModel = viewModel {
    CollectionSharingViewModel(resolvedFriendRepository, resolvedProfileRepository)
  }
  val state by viewModel.uiState.collectAsState()
  val sharingState by sharingViewModel.uiState.collectAsState()
  var isShareDialogOpen by remember { mutableStateOf(false) }
  val guide = state.guide

  when {
    guide != null -> {
      LaunchedEffect(guide.id, guide.shareSettings) {
        if (guide.shareSettings.audience != ShareAudience.NONE) {
          sharingViewModel.loadFriends()
        }
      }
      val monumentGuide = remember(guide) { guide.toMonumentGuide() }
      val recipients =
          when (guide.shareSettings.audience) {
            ShareAudience.ALL_FRIENDS,
            ShareAudience.ALL_FOLLOWERS -> sharingState.friends
            ShareAudience.SELECTED_FRIENDS ->
                sharingState.friends.filter { it.uid in guide.shareSettings.sharedWithUids }
            ShareAudience.NONE -> emptyList()
          }
      MonumentDetailScreen(
          image = rememberAsyncImagePainter(guide.photoBytes),
          guide = monumentGuide,
          onBackClick = onBackClick,
          modifier = modifier,
          sharingState =
              MonumentDetailSharingState(
                  settings = guide.shareSettings,
                  recipients = recipients,
                  unavailableBecauseTooFar = guide.sharingEligibility == SharingEligibility.TOO_FAR,
                  isSaving = guide.id in state.sharingGuideIds,
              ),
          onShareClick = {
            isShareDialogOpen = true
            sharingViewModel.loadFriends()
          },
          showSharingError = state.sharingError != null,
          onSharingErrorShown = viewModel::clearSharingError,
      )
      if (isShareDialogOpen) {
        GuideSharingDialog(
            guideId = guide.id,
            monumentName = guide.monumentName,
            shareSettings = guide.shareSettings,
            friends = sharingState.friends,
            isLoadingFriends = sharingState.isLoadingFriends,
            friendsError = sharingState.friendsError,
            isSaving = guide.id in state.sharingGuideIds,
            onRetryLoadingFriends = sharingViewModel::retryLoadingFriends,
            onDismiss = { isShareDialogOpen = false },
            onConfirm = { audience, friendUids ->
              viewModel.setShareAudience(guide.id, audience, friendUids)
              isShareDialogOpen = false
            },
        )
      }
    }
    state.isLoadingGuide ->
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(Modifier.testTag(C.Tag.saved_monument_detail_loading))
        }
    state.guideError != null ->
        SavedMonumentDetailMessage(
            messageRes = R.string.saved_monument_detail_error,
            tag = C.Tag.saved_monument_detail_error,
            onBackClick = onBackClick,
            onRetry = { viewModel.loadGuide(guideId) },
            modifier = modifier,
        )
    else ->
        SavedMonumentDetailMessage(
            messageRes = R.string.saved_monument_detail_not_found,
            tag = C.Tag.saved_monument_detail_not_found,
            onBackClick = onBackClick,
            onRetry = null,
            modifier = modifier,
        )
  }
}

/** Centered message with the detail back button, and a retry button when [onRetry] is set. */
@Composable
internal fun SavedMonumentDetailMessage(
    @StringRes messageRes: Int,
    tag: String,
    onBackClick: () -> Unit,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    retryTag: String = C.Tag.saved_monument_detail_retry,
    showBackArrow: Boolean = false,
) {
  Box(modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.align(Alignment.Center).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
          text = stringResource(messageRes),
          style = MaterialTheme.typography.bodyLarge,
          textAlign = TextAlign.Center,
          modifier = Modifier.testTag(tag),
      )
      if (onRetry != null) {
        Button(onClick = onRetry, modifier = Modifier.testTag(retryTag)) {
          Text(stringResource(R.string.saved_monument_detail_retry))
        }
      }
    }
    MonumentDetailBackButton(
        onClick = onBackClick,
        modifier = Modifier.align(Alignment.TopStart),
        showBackArrow = showBackArrow,
    )
  }
}
