// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.guide

import com.github.roamswent.roam.model.location.Coordinates
import kotlinx.coroutines.flow.Flow

/** Stores and retrieves guides saved by the currently authenticated user. */
interface SavedGuideRepository {
  /** Saves [guide] and returns it with its persistent identifier. */
  suspend fun addGuide(guide: Guide): Guide

  /** Removes the guide identified by [guideId]. */
  suspend fun removeGuide(guideId: String)

  /** Marks the guide identified by [guideId] as a favourite, or removes it from favourites. */
  suspend fun setFavorite(guideId: String, isFavorite: Boolean)

  /**
   * Sets the location of the guide identified by [guideId], which puts it on Explore, or removes it
   * when [location] is `null`. The guide stays in the collection either way.
   *
   * Callers may make several updates without waiting for the previous ones, so implementations
   * must: take an update on as soon as it is called, so cancelling the caller only stops waiting
   * for the result; apply updates in call order, whatever order their results arrive in; and leave
   * the location unchanged when an update fails. The stored location is then the one of the last
   * call that succeeded. Firestore's local write queue provides all three.
   */
  suspend fun setLocation(guideId: String, location: Coordinates?)

  /** Retrieves every guide saved by the current user. */
  suspend fun getSavedGuides(): List<Guide>

  /** Observes the current collection, including cached values and later synchronization updates. */
  fun observeSavedGuides(): Flow<List<Guide>>

  /** Retrieves a saved guide by [guideId], or returns `null` if it does not exist. */
  suspend fun getGuide(guideId: String): Guide?
}
