// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.roamswent.roam.model.guide.MonumentSharingRepository
import com.github.roamswent.roam.ui.guide.SavedGuidesUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Exposes the monuments [ownerUid] shares with the current user as a [SavedGuidesUiState], so the
 * collection screen can display them read-only.
 */
class FriendCollectionViewModel(
    private val repository: MonumentSharingRepository,
    private val ownerUid: String,
) : ViewModel() {
  private val _uiState = MutableStateFlow(SavedGuidesUiState())
  val uiState: StateFlow<SavedGuidesUiState> = _uiState.asStateFlow()
  private var loadJob: Job? = null

  fun load() {
    if (loadJob?.isActive == true) return
    _uiState.update { it.copy(isLoadingGuides = true, guidesError = null) }
    loadJob = viewModelScope.launch {
      try {
        repository.observeSharedGuidesFrom(ownerUid).collect { guides ->
          _uiState.update { it.copy(guides = guides, isLoadingGuides = false, guidesError = null) }
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(isLoadingGuides = false, guidesError = exception) }
      }
    }
  }

  fun onSearchQueryChange(query: String) {
    _uiState.update { it.copy(searchQuery = query) }
  }
}
