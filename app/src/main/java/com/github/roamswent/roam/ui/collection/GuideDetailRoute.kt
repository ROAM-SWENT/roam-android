// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.guide.SavedGuideRepository
import com.github.roamswent.roam.model.guide.toMonumentGuide
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.guide.SavedGuidesViewModel
import com.github.roamswent.roam.ui.monumentDetail.MonumentDetailBackButton
import com.github.roamswent.roam.ui.monumentDetail.MonumentDetailScreen
import com.github.roamswent.roam.ui.monumentDetail.PhotoPlaceholderPainter
import com.github.roamswent.roam.ui.theme.CollectionPlaceholder
import com.github.roamswent.roam.ui.theme.CollectionPlaceholderIcon
import com.github.roamswent.roam.ui.theme.LoadingErrorText
import com.github.roamswent.roam.ui.theme.ScreenBackground

/**
 * Loads the saved guide [guideId] from [repository] and shows it in [MonumentDetailScreen], with
 * its stored photo, discovery date and location. The guide is requested once, when the ViewModel is
 * created, so it is not reloaded on recomposition or rotation.
 */
@Composable
fun GuideDetailRoute(
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
    guide != null -> SavedGuideDetail(guide, onBackClick, modifier)
    state.isLoadingGuide ->
        GuideDetailMessage(onBackClick, modifier) {
          CircularProgressIndicator(
              modifier = Modifier.semantics { testTag = C.Tag.guide_detail_loading }
          )
        }
    state.guideError != null ->
        GuideDetailMessage(onBackClick, modifier) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GuideDetailText(R.string.guide_detail_error, C.Tag.guide_detail_error)
            Button(
                onClick = { viewModel.loadGuide(guideId) },
                modifier =
                    Modifier.padding(top = 16.dp).semantics { testTag = C.Tag.guide_detail_retry },
            ) {
              Text(stringResource(R.string.guide_detail_retry))
            }
          }
        }
    else ->
        GuideDetailMessage(onBackClick, modifier) {
          GuideDetailText(R.string.guide_detail_not_found, C.Tag.guide_detail_not_found)
        }
  }
}

@Composable
private fun SavedGuideDetail(guide: Guide, onBackClick: () -> Unit, modifier: Modifier) {
  val icon = rememberVectorPainter(Icons.Filled.AccountBalance)
  val placeholder =
      remember(icon) {
        PhotoPlaceholderPainter(icon, CollectionPlaceholder, CollectionPlaceholderIcon, 72.dp)
      }
  MonumentDetailScreen(
      image =
          rememberAsyncImagePainter(
              model = guide.photoBytes,
              placeholder = placeholder,
              error = placeholder,
          ),
      guide = guide.toMonumentGuide(),
      onBackClick = onBackClick,
      modifier = modifier,
      discoveryDate = guide.discoveryDate,
      location = guide.location,
  )
}

@Composable
private fun GuideDetailMessage(
    onBackClick: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
  Box(modifier = modifier.fillMaxSize().background(ScreenBackground)) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
      content()
    }
    MonumentDetailBackButton(onClick = onBackClick, modifier = Modifier.align(Alignment.TopStart))
  }
}

@Composable
private fun GuideDetailText(textRes: Int, tag: String) {
  Text(
      text = stringResource(textRes),
      fontSize = 14.sp,
      color = LoadingErrorText,
      textAlign = TextAlign.Center,
      modifier = Modifier.semantics { testTag = tag },
  )
}
