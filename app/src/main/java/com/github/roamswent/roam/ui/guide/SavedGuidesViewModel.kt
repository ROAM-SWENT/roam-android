// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.guide.SavedGuideRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
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
    /** Failure from the most recent favourite change. */
    val favoriteError: Exception? = null,
)

/** Saves and retrieves the current user's guides and exposes operation status through [uiState]. */
class SavedGuidesViewModel(private val repository: SavedGuideRepository) : ViewModel() {
  private val _uiState = MutableStateFlow(SavedGuidesUiState())
  val uiState: StateFlow<SavedGuidesUiState> = _uiState.asStateFlow()
  private var loadSavedGuidesJob: Job? = null
  /** Local optimistic saves override repository snapshots until they finish. */
  private val pendingGuideSaves = mutableMapOf<String, Guide>()
  /** IDs hidden from snapshots while deletion is in progress. */
  private val pendingGuideDeletes = mutableSetOf<String>()
  /** Used to derive [SavedGuidesUiState.isSavingGuide] across concurrent saves. */
  private val pendingSaveIds = mutableSetOf<String>()
  /** Lets deletion wait for the corresponding save before removing the guide remotely. */
  private val pendingSaveJobs = mutableMapOf<String, Job>()
  /** IDs currently treated as saved, reconciled from snapshots and successful writes. */
  private val savedGuideIds = mutableSetOf<String>()
  /** Records saves that finish during deletion, for recovery if deletion fails. */
  private val savedGuideIdsDuringDelete = mutableSetOf<String>()
  /** Optimistic favourite values that override snapshots until their write settles. */
  private val pendingFavorites = mutableMapOf<String, PendingFavorite>()
  /** Final favourite value of writes that settled while their guide was being deleted. */
  private val favoritesSettledWhileDeleting = mutableMapOf<String, Boolean>()
  /** Increases with every favourite request so only the latest one settles its entry. */
  private var lastFavoriteRequest = 0L

  /**
   * Latest favourite value requested for a guide. [request] orders the writes so only the latest
   * one may clear or roll back the entry; [rollbackTo] is the value to restore if it fails.
   */
  private data class PendingFavorite(
      val isFavorite: Boolean,
      val request: Long,
      val rollbackTo: Boolean,
  )

  /**
   * Observes the saved collection, including cached data. Each snapshot replaces [savedGuideIds] so
   * guides removed elsewhere no longer block a future save.
   */
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
          savedGuideIds.clear()
          savedGuideIds += guides.filterNot { it.id in pendingGuideDeletes }.map { it.id }
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
        val guide = repository.getGuide(guideId)?.withPendingFavorite()
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
    if (
        guideToSave.id in pendingSaveIds ||
            guideToSave.id in pendingGuideDeletes ||
            guideToSave.id in savedGuideIds ||
            _uiState.value.guides.any { it.id == guideToSave.id }
    ) {
      return
    }
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
    // Register the Job before it runs so a concurrent delete can reliably wait for it.
    lateinit var saveJob: Job
    saveJob =
        viewModelScope.launch(start = CoroutineStart.LAZY) {
          try {
            val savedGuide = repository.addGuide(guideToSave)
            if (savedGuide.id in pendingGuideDeletes) {
              savedGuideIdsDuringDelete += savedGuide.id
              _uiState.update {
                it.copy(savedGuideId = it.savedGuideId?.takeUnless { id -> id == savedGuide.id })
              }
            } else {
              savedGuideIds += savedGuide.id
              _uiState.update {
                it.copy(guides = it.guides.upsert(savedGuide), savedGuideId = savedGuide.id)
              }
            }
          } catch (exception: CancellationException) {
            rollbackFailedSave(guideToSave.id, previousGuide)
            throw exception
          } catch (exception: Exception) {
            rollbackFailedSave(guideToSave.id, previousGuide)
            _uiState.update { it.copy(saveError = exception) }
          } finally {
            pendingGuideSaves.remove(guideToSave.id)
            pendingSaveIds.remove(guideToSave.id)
            if (pendingSaveJobs[guideToSave.id] === saveJob) {
              pendingSaveJobs.remove(guideToSave.id)
            }
            _uiState.update { it.copy(isSavingGuide = pendingSaveIds.isNotEmpty()) }
          }
        }
    pendingSaveJobs[guideToSave.id] = saveJob
    saveJob.start()
  }

  /** Removes [guideId] from the UI immediately while Firestore synchronizes the deletion. */
  fun deleteGuide(guideId: String) {
    if (guideId in _uiState.value.deletingGuideIds) return
    val previousGuide = _uiState.value.guides.firstOrNull { it.id == guideId }
    val wasSavedBeforeDelete = guideId in savedGuideIds
    val savedGuideIdBeforeDelete = _uiState.value.savedGuideId?.takeIf { it == guideId }
    val saveJob = pendingSaveJobs[guideId]
    pendingGuideSaves.remove(guideId)
    // Pending favourites are kept until the deletion settles, in case the guide is restored.
    pendingGuideDeletes += guideId

    _uiState.update {
      it.copy(
          guides = it.guides.filterNot { guide -> guide.id == guideId },
          guide = it.guide?.takeUnless { guide -> guide.id == guideId },
          deletingGuideIds = it.deletingGuideIds + guideId,
          savedGuideId = it.savedGuideId?.takeUnless { id -> id == guideId },
          deleteError = null,
      )
    }
    viewModelScope.launch {
      var deletionSucceeded = false
      try {
        // Keep the pending-delete marker set while waiting so snapshots and save completion cannot
        // make the guide visible again.
        saveJob?.join()
        repository.removeGuide(guideId)
        deletionSucceeded = true
        savedGuideIds.remove(guideId)
        savedGuideIdsDuringDelete.remove(guideId)
        pendingFavorites.remove(guideId)
        favoritesSettledWhileDeleting.remove(guideId)
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(deleteError = exception) }
      } finally {
        pendingGuideDeletes.remove(guideId)
        val saveSucceededDuringDelete = savedGuideIdsDuringDelete.remove(guideId)
        val settledFavorite = favoritesSettledWhileDeleting.remove(guideId)
        // A failed delete restores a previously saved guide, but a failed save remains retryable.
        if (!deletionSucceeded && (wasSavedBeforeDelete || saveSucceededDuringDelete)) {
          savedGuideIds += guideId
          _uiState.update { state ->
            state.copy(
                savedGuideId =
                    state.savedGuideId
                        ?: savedGuideIdBeforeDelete
                        ?: guideId.takeIf { saveSucceededDuringDelete },
                guides =
                    if (previousGuide != null && state.guides.none { it.id == guideId }) {
                      val restoredGuide =
                          settledFavorite?.let { isFavorite ->
                            previousGuide.copy(isFavorite = isFavorite)
                          } ?: previousGuide
                      state.guides + restoredGuide.withPendingFavorite()
                    } else {
                      state.guides
                    },
            )
          }
        }
        _uiState.update {
          it.copy(
              deletingGuideIds = it.deletingGuideIds - guideId,
              isSavingGuide = pendingSaveIds.isNotEmpty(),
          )
        }
      }
    }
  }

  /**
   * Marks [guideId] as a favourite, or removes it from favourites, immediately in the UI while
   * Firestore synchronizes the change. Offline writes only complete once back online, so a newer
   * request is never blocked by an older one: the latest request wins.
   */
  fun setFavorite(guideId: String, isFavorite: Boolean) {
    val current = displayedGuide(guideId) ?: return
    if (current.isFavorite == isFavorite) return
    val request = ++lastFavoriteRequest
    val rollbackTo = pendingFavorites[guideId]?.rollbackTo ?: current.isFavorite
    pendingFavorites[guideId] = PendingFavorite(isFavorite, request, rollbackTo)
    _uiState.update { it.withFavorite(guideId, isFavorite).copy(favoriteError = null) }

    viewModelScope.launch {
      try {
        repository.setFavorite(guideId, isFavorite)
        val pending = pendingFavorites[guideId]
        if (pending?.request == request) {
          pendingFavorites.remove(guideId)
          if (guideId in pendingGuideDeletes) favoritesSettledWhileDeleting[guideId] = isFavorite
        } else if (pending != null) {
          // Writes are applied in order, so this value is now the one to restore on failure.
          pendingFavorites[guideId] = pending.copy(rollbackTo = isFavorite)
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        val pending = pendingFavorites[guideId]
        when {
          // Writes settle in order, so no pending entry means the guide was deleted.
          pending == null -> Unit
          // A newer request decides the final value, so only report the failure.
          pending.request != request -> {
            if (guideId !in pendingGuideDeletes) {
              _uiState.update { it.copy(favoriteError = exception) }
            }
          }
          // The guide is hidden while it is deleted; remember the value to restore if that fails.
          guideId in pendingGuideDeletes -> {
            pendingFavorites.remove(guideId)
            favoritesSettledWhileDeleting[guideId] = pending.rollbackTo
          }
          else -> {
            pendingFavorites.remove(guideId)
            _uiState.update {
              it.withFavorite(guideId, pending.rollbackTo).copy(favoriteError = exception)
            }
          }
        }
      }
    }
  }

  /** Flips the favourite state of [guideId]. */
  fun toggleFavorite(guideId: String) {
    val current = displayedGuide(guideId) ?: return
    setFavorite(guideId, !current.isFavorite)
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
          favoriteError = null,
      )
    }
  }

  private fun displayedGuide(guideId: String): Guide? =
      _uiState.value.guides.firstOrNull { it.id == guideId }
          ?: _uiState.value.guide?.takeIf { it.id == guideId }

  private fun SavedGuidesUiState.withFavorite(
      guideId: String,
      isFavorite: Boolean,
  ): SavedGuidesUiState =
      copy(
          guides = guides.map { if (it.id == guideId) it.copy(isFavorite = isFavorite) else it },
          guide = guide?.let { if (it.id == guideId) it.copy(isFavorite = isFavorite) else it },
      )

  private fun Guide.withPendingFavorite(): Guide =
      pendingFavorites[id]?.let { copy(isFavorite = it.isFavorite) } ?: this

  // Pending favourites are applied on every upsert so a snapshot or save result that predates the
  // latest favourite request does not flip it back.
  private fun List<Guide>.upsert(guide: Guide): List<Guide> =
      filterNot { it.id == guide.id } + guide.withPendingFavorite()

  private fun rollbackFailedSave(guideId: String, previousGuide: Guide?) {
    if (guideId in pendingGuideDeletes) return
    _uiState.update { state ->
      state.copy(
          guides =
              if (previousGuide == null) {
                state.guides.filterNot { it.id == guideId }
              } else {
                state.guides.upsert(previousGuide)
              },
      )
    }
  }

  private fun Guide.hasSameContentAs(other: Guide): Boolean =
      photoBytes.contentEquals(other.photoBytes) &&
          monumentName == other.monumentName &&
          confidence == other.confidence &&
          history == other.history &&
          facts == other.facts &&
          culture == other.culture &&
          location == other.location

  private fun mergePendingChanges(snapshotGuides: List<Guide>): List<Guide> {
    var mergedGuides =
        snapshotGuides.filterNot { it.id in pendingGuideDeletes }.map { it.withPendingFavorite() }
    pendingGuideSaves.values.forEach { guide -> mergedGuides = mergedGuides.upsert(guide) }
    return mergedGuides
  }
}
