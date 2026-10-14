// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.ui.loading

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.github.roamswent.roam.model.ai.ImagePayload
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.ai.MonumentGuideRepository
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LoadingRoute(
    imageUri: Uri,
    onCancel: () -> Unit,
    onDiscoverySuccess: (MonumentGuide, Uri) -> Unit,
    onDiscoveryError: (String) -> Unit,
    repository: MonumentGuideRepository,
    readImage: (suspend (Uri) -> ImagePayload)? = null,
    language: String = Locale.getDefault().toLanguageTag(),
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val imageReader =
      remember(context, readImage) {
        readImage
            ?: { uri ->
              withContext(Dispatchers.IO) {
                val bytes =
                    checkNotNull(context.contentResolver.openInputStream(uri)) {
                          "Cannot open image: $uri"
                        }
                        .use { it.readBytes() }
                ImagePayload(bytes, "image/jpeg")
              }
            }
      }
  val viewModel: LoadingViewModel =
      viewModel(key = imageUri.toString()) {
        LoadingViewModel(repository, imageReader, imageUri, language)
      }
  val state by viewModel.uiState.collectAsState()
  val latestOnCancel by rememberUpdatedState(onCancel)
  val latestOnDiscoverySuccess by rememberUpdatedState(onDiscoverySuccess)
  val latestOnDiscoveryError by rememberUpdatedState(onDiscoveryError)

  LaunchedEffect(viewModel) {
    viewModel.navigationEvents.collect { event ->
      when (event) {
        is LoadingNavigationEvent.NavigateToMonumentDetail ->
            latestOnDiscoverySuccess(event.guide, Uri.parse(event.imageUri))
        is LoadingNavigationEvent.NavigateHomeWithError -> latestOnDiscoveryError(event.message)
        LoadingNavigationEvent.NavigateHome -> latestOnCancel()
      }
    }
  }

  LoadingScreen(
      image = rememberAsyncImagePainter(imageUri),
      onCancel = viewModel::onCancel,
      modifier = modifier,
      errorMessage = (state as? LoadingUiState.Error)?.message,
  )
}
