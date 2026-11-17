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

  override suspend fun setShareAudience(
      guideId: String,
      audience: ShareAudience,
      recipientUids: Set<String>,
  ) {
    updateError?.let { throw it }
    validateGuideId(guideId)

    val currentSettings =
        shareSettings.value[guideId]
            ?: guidesByOwner.value.values.flatten().firstOrNull { it.id == guideId }?.shareSettings
            ?: MonumentShareSettings()

    val newSettings =
        when (audience) {
          ShareAudience.SELECTED_FRIENDS ->
              currentSettings.copy(
                  audience = audience,
                  sharedWithUids = recipientUids,
                  dismissedByUids = currentSettings.dismissedByUids - recipientUids,
              )
          ShareAudience.ALL_FRIENDS,
          ShareAudience.ALL_FOLLOWERS ->
              currentSettings.copy(
                  audience = audience,
                  sharedWithUids = emptySet(),
                  dismissedByUids = emptySet(),
              )
          ShareAudience.NONE ->
              currentSettings.copy(
                  audience = audience,
                  sharedWithUids = emptySet(),
              )
        }

    shareSettings.update { it + (guideId to newSettings) }
    guidesByOwner.update { map ->
      map.mapValues { (_, guides) ->
        guides.map { guide ->
          if (guide.id == guideId) guide.copy(shareSettings = newSettings) else guide
        }
      }
    }
  }

  override suspend fun grantAccess(guideId: String, recipientUids: Set<String>) {
    updateError?.let { throw it }
    validateGuideId(guideId)
    if (recipientUids.isEmpty()) return

    val currentSettings =
        shareSettings.value[guideId]
            ?: guidesByOwner.value.values.flatten().firstOrNull { it.id == guideId }?.shareSettings
            ?: MonumentShareSettings()

    val audience =
        if (currentSettings.audience == ShareAudience.NONE) ShareAudience.SELECTED_FRIENDS
        else currentSettings.audience

    val newSettings =
        currentSettings.copy(
            audience = audience,
            sharedWithUids = currentSettings.sharedWithUids + recipientUids,
            dismissedByUids = currentSettings.dismissedByUids - recipientUids,
        )

    shareSettings.update { it + (guideId to newSettings) }
    guidesByOwner.update { map ->
      map.mapValues { (_, guides) ->
        guides.map { guide ->
          if (guide.id == guideId) guide.copy(shareSettings = newSettings) else guide
        }
      }
    }
  }

  override suspend fun revokeAccess(guideId: String, recipientUids: Set<String>) {
    updateError?.let { throw it }
    validateGuideId(guideId)
    if (recipientUids.isEmpty()) return

    val currentSettings =
        shareSettings.value[guideId]
            ?: guidesByOwner.value.values.flatten().firstOrNull { it.id == guideId }?.shareSettings
            ?: MonumentShareSettings()

    val newSettings =
        currentSettings.copy(
            sharedWithUids = currentSettings.sharedWithUids - recipientUids,
        )

    shareSettings.update { it + (guideId to newSettings) }
    guidesByOwner.update { map ->
      map.mapValues { (_, guides) ->
        guides.map { guide ->
          if (guide.id == guideId) guide.copy(shareSettings = newSettings) else guide
        }
      }
    }
  }

  override suspend fun dismissSharedGuide(ownerUid: String, guideId: String) {
    dismissError?.let { throw it }
    validateGuideId(guideId)

    guidesByOwner.update { map ->
      val ownerGuides = map[ownerUid] ?: return@update map
      map +
          (ownerUid to
              ownerGuides.map { guide ->
                if (guide.id == guideId) {
                  val newSettings =
                      guide.shareSettings.copy(
                          dismissedByUids = guide.shareSettings.dismissedByUids + currentUserId,
                          sharedWithUids = guide.shareSettings.sharedWithUids - currentUserId,
                      )
                  guide.copy(shareSettings = newSettings)
                } else guide
              })
    }

    shareSettings.update { map ->
      val current = map[guideId] ?: return@update map
      map +
          (guideId to
              current.copy(
                  dismissedByUids = current.dismissedByUids + currentUserId,
                  sharedWithUids = current.sharedWithUids - currentUserId,
              ))
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
    return (guidesByOwner.value[ownerUid] ?: emptyList()).filter { guide ->
      currentUserId !in guide.shareSettings.dismissedByUids &&
          guide.shareSettings.audience != ShareAudience.NONE
    }
  }

  override fun observeSharedGuidesFrom(ownerUid: String): Flow<List<Guide>> {
    loadError?.let { error ->
      return flow { throw error }
    }
    return guidesByOwner.map { map ->
      (map[ownerUid] ?: emptyList()).filter { guide ->
        currentUserId !in guide.shareSettings.dismissedByUids &&
            guide.shareSettings.audience != ShareAudience.NONE
      }
    }
  }

  private fun validateGuideId(guideId: String) {
    require(guideId.isNotBlank()) { "Guide ID cannot be blank." }
  }
}
