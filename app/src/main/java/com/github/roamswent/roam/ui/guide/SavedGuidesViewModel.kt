// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.guide.SavedGuideRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SavedGuidesUiState(
    /** Guides currently loaded for the collection screen. */
    val guides: List<Guide> = emptyList(),
    /** Whether the collection is being loaded. */
    val isLoadingGuides: Boolean = false,
    /** Failure from the most recent collection load. */
    val guidesError: Exception? = null,
    /** Guide currently loaded for the detail screen, or null if not loaded/not found. */
    val guide: Guide? = null,
    /** Whether a guide is being loaded. */
    val isLoadingGuide: Boolean = false,
    /** Failure from the most recent guide load. */
    val guideError: Exception? = null,
    /** IDs of guides currently being removed. */
    val deletingGuideIds: Set<String> = emptySet(),
    /** Failure from the most recent deletion. */
    val deleteError: Exception? = null,
    /** Whether a guide is currently being saved. */
    val isSavingGuide: Boolean = false,
    /** ID of the most recently saved guide, cleared when another save starts. */
    val savedGuideId: String? = null,
    /** Failure from the most recent save. */
    val saveError: Exception? = null,
)

/** Saves and retrieves the current user's guides and exposes operation status through [uiState]. */
class SavedGuidesViewModel(private val repository: SavedGuideRepository) : ViewModel() {
  private val _uiState = MutableStateFlow(SavedGuidesUiState())
  val uiState: StateFlow<SavedGuidesUiState> = _uiState.asStateFlow()
  private var loadSavedGuidesJob: Job? = null
  private val pendingGuideSaves = mutableMapOf<String, Guide>()
  private val pendingGuideDeletes = mutableSetOf<String>()
  private val pendingSaveIds = mutableSetOf<String>()

  /** Observes the saved collection, including cached data */
  fun loadSavedGuides(seedGuides: List<Guide> = emptyList()) {
    if (loadSavedGuidesJob?.isActive == true) return

    _uiState.update { it.copy(isLoadingGuides = true, guidesError = null) }
    loadSavedGuidesJob = viewModelScope.launch {
      try {
        var receivedInitialSnapshot = false
        repository.observeSavedGuides().collect { guides ->
          _uiState.update { state ->
            state.copy(
                guides = mergePendingChanges(guides),
                isLoadingGuides = false,
                guidesError = null,
            )
          }
          if (!receivedInitialSnapshot) {
            receivedInitialSnapshot = true
            val existingIds = guides.mapTo(mutableSetOf()) { it.id }
            seedGuides.filterNot { it.id in existingIds }.forEach(::saveGuide)
          }
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(isLoadingGuides = false, guidesError = exception) }
      }
    }
  }

  /**
   * Loads [guideId] for a detail screen. A missing guide is reported as a null
   * [SavedGuidesUiState.guide].
   */
  fun loadGuide(guideId: String) {
    _uiState.update { it.copy(guide = null, isLoadingGuide = true, guideError = null) }
    viewModelScope.launch {
      try {
        val guide = repository.getGuide(guideId)
        _uiState.update { it.copy(guide = guide, isLoadingGuide = false) }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(isLoadingGuide = false, guideError = exception) }
      }
    }
  }

  /** Adds [guide] immediately to the UI and lets Firestore synchronize it in the background. */
  fun saveGuide(guide: Guide) {
    if (guide.id.isBlank() && _uiState.value.guides.any { it.hasSameContentAs(guide) }) return
    val guideToSave = guide.copy(id = guide.id.ifBlank { UUID.randomUUID().toString() })
    if (guideToSave.id in pendingSaveIds) return
    val previousGuide = _uiState.value.guides.firstOrNull { it.id == guideToSave.id }
    pendingGuideSaves[guideToSave.id] = guideToSave
    pendingSaveIds += guideToSave.id

    _uiState.update {
      it.copy(
          guides = it.guides.upsert(guideToSave),
          isSavingGuide = true,
          savedGuideId = null,
          saveError = null,
      )
    }
    viewModelScope.launch {
      try {
        val savedGuide = repository.addGuide(guideToSave)
        pendingGuideSaves.remove(guideToSave.id)
        pendingSaveIds.remove(guideToSave.id)
        _uiState.update { state ->
          state.copy(
              guides =
                  if (savedGuide.id in pendingGuideDeletes) {
                    state.guides.filterNot { it.id == savedGuide.id }
                  } else {
                    state.guides.upsert(savedGuide)
                  },
              isSavingGuide = pendingSaveIds.isNotEmpty(),
              savedGuideId = savedGuide.id,
          )
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        pendingGuideSaves.remove(guideToSave.id)
        pendingSaveIds.remove(guideToSave.id)
        _uiState.update { state ->
          state.copy(
              guides =
                  if (previousGuide == null) {
                    state.guides.filterNot { it.id == guideToSave.id }
                  } else {
                    state.guides.upsert(previousGuide)
                  },
              isSavingGuide = pendingSaveIds.isNotEmpty(),
              saveError = exception,
          )
        }
      }
    }
  }

  /** Removes [guideId] from the UI immediately while Firestore synchronizes the deletion. */
  fun deleteGuide(guideId: String) {
    if (guideId in _uiState.value.deletingGuideIds) return
    val previousGuide = _uiState.value.guides.firstOrNull { it.id == guideId }
    pendingGuideSaves.remove(guideId)
    pendingGuideDeletes += guideId

    _uiState.update {
      it.copy(
          guides = it.guides.filterNot { guide -> guide.id == guideId },
          guide = it.guide?.takeUnless { guide -> guide.id == guideId },
          deletingGuideIds = it.deletingGuideIds + guideId,
          deleteError = null,
      )
    }
    viewModelScope.launch {
      try {
        repository.removeGuide(guideId)
        pendingGuideDeletes.remove(guideId)
        _uiState.update {
          it.copy(
              deletingGuideIds = it.deletingGuideIds - guideId,
          )
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        pendingGuideDeletes.remove(guideId)
        _uiState.update {
          it.copy(
              guides =
                  if (previousGuide != null && it.guides.none { guide -> guide.id == guideId }) {
                    it.guides + previousGuide
                  } else {
                    it.guides
                  },
              deletingGuideIds = it.deletingGuideIds - guideId,
              deleteError = exception,
          )
        }
      }
    }
  }

  /** Clears operation errors and the last save result from the UI state. */
  fun clearErrors() {
    _uiState.update {
      it.copy(
          guidesError = null,
          guideError = null,
          deleteError = null,
          saveError = null,
          savedGuideId = null,
      )
    }
  }

  private fun List<Guide>.upsert(guide: Guide): List<Guide> =
      filterNot { it.id == guide.id } + guide

  private fun Guide.hasSameContentAs(other: Guide): Boolean =
      photoBytes.contentEquals(other.photoBytes) &&
          monumentName == other.monumentName &&
          confidence == other.confidence &&
          history == other.history &&
          facts == other.facts &&
          culture == other.culture &&
          location == other.location

  private fun mergePendingChanges(snapshotGuides: List<Guide>): List<Guide> {
    var mergedGuides = snapshotGuides.filterNot { it.id in pendingGuideDeletes }
    pendingGuideSaves.values.forEach { guide -> mergedGuides = mergedGuides.upsert(guide) }
    return mergedGuides
  }
}
