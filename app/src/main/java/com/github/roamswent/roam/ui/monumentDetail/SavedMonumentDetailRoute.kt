// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.guide.SavedGuideRepository
import com.github.roamswent.roam.model.guide.toMonumentGuide
import com.github.roamswent.roam.resources.C
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
) {
  val viewModel: SavedGuidesViewModel = viewModel {
    SavedGuidesViewModel(repository).also { it.loadGuide(guideId) }
  }
  val state by viewModel.uiState.collectAsState()
  val guide = state.guide

  when {
    guide != null -> {
      val monumentGuide = remember(guide) { guide.toMonumentGuide() }
      MonumentDetailScreen(
          image = rememberAsyncImagePainter(guide.photoBytes),
          guide = monumentGuide,
          onBackClick = onBackClick,
          modifier = modifier,
      )
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
private fun SavedMonumentDetailMessage(
    @StringRes messageRes: Int,
    tag: String,
    onBackClick: () -> Unit,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
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
        Button(onClick = onRetry, modifier = Modifier.testTag(C.Tag.saved_monument_detail_retry)) {
          Text(stringResource(R.string.saved_monument_detail_retry))
        }
      }
    }
    MonumentDetailBackButton(onClick = onBackClick, modifier = Modifier.align(Alignment.TopStart))
  }
}
