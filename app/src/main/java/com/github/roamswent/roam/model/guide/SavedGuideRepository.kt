// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.model.guide

/** Stores and retrieves guides saved by the currently authenticated user. */
interface SavedGuideRepository {
  suspend fun addGuide(guide: Guide): Guide

  suspend fun removeGuide(guideId: String)

  suspend fun getSavedGuides(): List<Guide>

  suspend fun getGuide(guideId: String): Guide?
}
