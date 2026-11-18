// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.monumentDetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import coil.compose.rememberAsyncImagePainter
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.guide.MonumentSharingRepository
import com.github.roamswent.roam.model.guide.toMonumentGuide
import kotlinx.coroutines.CancellationException

private sealed interface SharedGuideState {
  data object Loading : SharedGuideState

  data object Missing : SharedGuideState

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
) {
  val state by
      produceState<SharedGuideState>(SharedGuideState.Loading, ownerUid, guideId) {
        value =
            try {
              repository
                  .getSharedGuidesFrom(ownerUid)
                  .firstOrNull { it.id == guideId }
                  ?.let { SharedGuideState.Loaded(it) } ?: SharedGuideState.Missing
            } catch (exception: CancellationException) {
              throw exception
            } catch (_: Exception) {
              SharedGuideState.Missing
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
      )
    }
    SharedGuideState.Loading ->
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
    SharedGuideState.Missing ->
        Box(modifier.fillMaxSize()) {
          Text(
              text = stringResource(R.string.saved_monument_detail_not_found),
              style = MaterialTheme.typography.bodyLarge,
              modifier = Modifier.align(Alignment.Center),
          )
          MonumentDetailBackButton(
              onClick = onBackClick,
              modifier = Modifier.align(Alignment.TopStart),
          )
        }
  }
}
