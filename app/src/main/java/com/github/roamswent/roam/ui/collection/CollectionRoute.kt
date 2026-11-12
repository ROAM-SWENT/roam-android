// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.roamswent.roam.model.guide.SavedGuideRepository
import com.github.roamswent.roam.ui.guide.SavedGuidesViewModel

/**
 * Loads the guides saved in [repository] and shows them in [CollectionScreen]. The ViewModel is
 * created once with the first [repository] and keeps it for its lifetime.
 */
@Composable
fun CollectionRoute(
    repository: SavedGuideRepository,
    modifier: Modifier = Modifier,
    onGuideClick: (String) -> Unit = {},
) {
  val viewModel: SavedGuidesViewModel = viewModel { SavedGuidesViewModel(repository) }
  val state by viewModel.uiState.collectAsState()

  LaunchedEffect(viewModel) { viewModel.loadSavedGuides() }

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
  )
}
