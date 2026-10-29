// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.ui.loading

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import coil.compose.rememberAsyncImagePainter
import com.github.roamswent.roam.model.ai.MonumentGuide

@Composable
fun LoadingRoute(
    imageUri: Uri,
    onCancel: () -> Unit,
    onDiscoverySuccess: (MonumentGuide, Uri) -> Unit,
    loadingViewModel: LoadingViewModel,
    modifier: Modifier = Modifier,
) {
  val state by loadingViewModel.uiState.collectAsState()
  val latestOnCancel by rememberUpdatedState(onCancel)
  val latestOnDiscoverySuccess by rememberUpdatedState(onDiscoverySuccess)

  LaunchedEffect(loadingViewModel) {
    loadingViewModel.startDiscovery()
    loadingViewModel.navigationEvents.collect { event ->
      when (event) {
        is LoadingNavigationEvent.NavigateToMonumentDetail ->
            latestOnDiscoverySuccess(event.guide, Uri.parse(event.imageUri))
        LoadingNavigationEvent.NavigateHome -> latestOnCancel()
      }
    }
  }

  LoadingScreen(
      image = rememberAsyncImagePainter(imageUri),
      onCancel = loadingViewModel::onCancel,
      modifier = modifier,
      errorMessage = (state as? LoadingUiState.Error)?.messageResId?.let { stringResource(it) },
  )
}
