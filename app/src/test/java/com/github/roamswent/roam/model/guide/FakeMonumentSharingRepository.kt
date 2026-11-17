// Co-authored-by: Gemini 3.8 Flash <noreply@google.com>

package com.github.roamswent.roam.model.guide

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [MonumentSharingRepository] backed by [MutableStateFlow] for tests and future UI
 * consumers.
 *
 * Configurable error hooks:
 * - [loadError]: when non-null, [getShareSettings], [getSharedGuidesFrom], and
 *   [observeSharedGuidesFrom] fail with it.
 * - [updateError]: when non-null, [setShareAudience], [grantAccess], and [revokeAccess] fail with
 *   it.
 * - [dismissError]: when non-null, [dismissSharedGuide] fails with it.
 */
class FakeMonumentSharingRepository(
    initialGuidesByOwner: Map<String, List<Guide>> = emptyMap(),
    initialSettingsByGuideId: Map<String, MonumentShareSettings> = emptyMap(),
    var currentUserId: String = "test-user-id",
) : MonumentSharingRepository {
  val guidesByOwner = MutableStateFlow(initialGuidesByOwner)
  val shareSettings = MutableStateFlow(initialSettingsByGuideId)

  var loadError: Exception? = null
  var updateError: Exception? = null
  var dismissError: Exception? = null

  private fun updateSettings(
      guideId: String,
      transform: (MonumentShareSettings) -> MonumentShareSettings,
  ) {
    val current =
        shareSettings.value[guideId]
            ?: guidesByOwner.value.values.flatten().firstOrNull { it.id == guideId }?.shareSettings
            ?: MonumentShareSettings()
    val updated = transform(current)
    shareSettings.update { it + (guideId to updated) }
    guidesByOwner.update { map ->
      map.mapValues { (_, guides) ->
        guides.map { if (it.id == guideId) it.copy(shareSettings = updated) else it }
      }
    }
  }

  private fun isVisible(guide: Guide) =
      currentUserId !in guide.shareSettings.dismissedByUids &&
          guide.shareSettings.audience != ShareAudience.NONE

  override suspend fun setShareAudience(
      guideId: String,
      audience: ShareAudience,
      recipientUids: Set<String>,
  ) {
    updateError?.let { throw it }
    validateGuideId(guideId)
    updateSettings(guideId) { current ->
      when (audience) {
        ShareAudience.SELECTED_FRIENDS ->
            current.copy(
                audience = audience,
                sharedWithUids = recipientUids,
                dismissedByUids = current.dismissedByUids - recipientUids,
            )
        ShareAudience.ALL_FRIENDS,
        ShareAudience.ALL_FOLLOWERS ->
            current.copy(
                audience = audience,
                sharedWithUids = emptySet(),
                dismissedByUids = emptySet(),
            )
        ShareAudience.NONE ->
            current.copy(
                audience = audience,
                sharedWithUids = emptySet(),
            )
      }
    }
  }

  override suspend fun grantAccess(guideId: String, recipientUids: Set<String>) {
    updateError?.let { throw it }
    validateGuideId(guideId)
    if (recipientUids.isEmpty()) return
    updateSettings(guideId) { current ->
      current.copy(
          audience =
              if (current.audience == ShareAudience.NONE) ShareAudience.SELECTED_FRIENDS
              else current.audience,
          sharedWithUids = current.sharedWithUids + recipientUids,
          dismissedByUids = current.dismissedByUids - recipientUids,
      )
    }
  }

  override suspend fun revokeAccess(guideId: String, recipientUids: Set<String>) {
    updateError?.let { throw it }
    validateGuideId(guideId)
    if (recipientUids.isEmpty()) return
    updateSettings(guideId) { it.copy(sharedWithUids = it.sharedWithUids - recipientUids) }
  }

  override suspend fun dismissSharedGuide(ownerUid: String, guideId: String) {
    dismissError?.let { throw it }
    validateGuideId(guideId)
    updateSettings(guideId) {
      it.copy(
          dismissedByUids = it.dismissedByUids + currentUserId,
          sharedWithUids = it.sharedWithUids - currentUserId,
      )
    }
  }

  override suspend fun getShareSettings(guideId: String): MonumentShareSettings {
    loadError?.let { throw it }
    validateGuideId(guideId)
    return shareSettings.value[guideId]
        ?: guidesByOwner.value.values.flatten().firstOrNull { it.id == guideId }?.shareSettings
        ?: throw MonumentSharingException.GuideNotFound()
  }

  override suspend fun getSharedGuidesFrom(ownerUid: String): List<Guide> {
    loadError?.let { throw it }
    return (guidesByOwner.value[ownerUid] ?: emptyList()).filter(::isVisible)
  }

  override fun observeSharedGuidesFrom(ownerUid: String): Flow<List<Guide>> {
    loadError?.let { error ->
      return flow { throw error }
    }
    return guidesByOwner.map { map -> (map[ownerUid] ?: emptyList()).filter(::isVisible) }
  }

  private fun validateGuideId(guideId: String) {
    require(guideId.isNotBlank()) { "Guide ID cannot be blank." }
  }
}
