// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.guide

import kotlinx.coroutines.flow.Flow

/** Stores and retrieves guides saved by the currently authenticated user. */
interface SavedGuideRepository {
  /** Saves [guide] and returns it with its persistent identifier. */
  suspend fun addGuide(guide: Guide): Guide

  /** Removes the guide identified by [guideId]. */
  suspend fun removeGuide(guideId: String)

  /** Marks the guide identified by [guideId] as a favourite, or removes it from favourites. */
  suspend fun setFavorite(guideId: String, isFavorite: Boolean)

  /** Retrieves every guide saved by the current user. */
  suspend fun getSavedGuides(): List<Guide>

  /** Observes the current collection, including cached values and later synchronization updates. */
  fun observeSavedGuides(): Flow<List<Guide>>

  /** Retrieves a saved guide by [guideId], or returns `null` if it does not exist. */
  suspend fun getGuide(guideId: String): Guide?
}
