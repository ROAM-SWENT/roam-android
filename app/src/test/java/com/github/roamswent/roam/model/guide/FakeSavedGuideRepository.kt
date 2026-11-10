// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.guide

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

/**
 * In-memory [SavedGuideRepository] for UI tests. [guides] can be changed directly to simulate a
 * synchronization update, a non-null [loadError] makes reads fail with it, and a non-null
 * [addError] makes saves fail with it.
 */
class FakeSavedGuideRepository(initialGuides: List<Guide> = emptyList()) : SavedGuideRepository {
  val guides = MutableStateFlow(initialGuides)
  var loadError: Exception? = null
  var addError: Exception? = null

  override suspend fun addGuide(guide: Guide): Guide {
    addError?.let { throw it }
    val savedGuide = guide.copy(id = guide.id.ifBlank { "guide-${guides.value.size + 1}" })
    guides.update { current -> current.filterNot { it.id == savedGuide.id } + savedGuide }
    return savedGuide
  }

  override suspend fun removeGuide(guideId: String) {
    guides.update { current -> current.filterNot { it.id == guideId } }
  }

  override suspend fun setFavorite(guideId: String, isFavorite: Boolean) {
    guides.update { current ->
      current.map { if (it.id == guideId) it.copy(isFavorite = isFavorite) else it }
    }
  }

  override suspend fun getSavedGuides(): List<Guide> {
    loadError?.let { throw it }
    return guides.value
  }

  override fun observeSavedGuides(): Flow<List<Guide>> =
      loadError?.let { error -> flow { throw error } } ?: guides.asStateFlow()

  override suspend fun getGuide(guideId: String): Guide? {
    loadError?.let { throw it }
    return guides.value.firstOrNull { it.id == guideId }
  }
}
