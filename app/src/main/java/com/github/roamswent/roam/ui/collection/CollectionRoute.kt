// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.collection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.roamswent.roam.model.guide.PlaceholderSavedGuideRepository
import com.github.roamswent.roam.model.guide.SavedGuideRepository
import com.github.roamswent.roam.ui.guide.SavedGuidesViewModel

/**
 * Loads the saved guides and shows them in [CollectionScreen]. Until the collection is wired to
 * Firestore (#87), [repository] defaults to placeholder data.
 */
@Composable
fun CollectionRoute(
    modifier: Modifier = Modifier,
    repository: SavedGuideRepository = PlaceholderSavedGuideRepository(),
    onGuideClick: (String) -> Unit = {},
) {
  val viewModel: SavedGuidesViewModel = viewModel { SavedGuidesViewModel(repository) }
  val state by viewModel.uiState.collectAsState()

  LaunchedEffect(viewModel) { viewModel.loadSavedGuides() }

  CollectionScreen(state = state, modifier = modifier, onGuideClick = onGuideClick)
}
