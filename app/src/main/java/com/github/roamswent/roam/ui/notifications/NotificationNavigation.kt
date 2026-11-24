/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.notifications

import android.content.Intent

/** Destination requested by a notification tap. */
sealed interface NotificationNavigation {
  /** Opens the friends screen. */
  data object Friends : NotificationNavigation

  /**
   * Opens a monument shared by [ownerUid].
   *
   * @property ownerUid identifier of the account that owns the shared guide
   * @property guideId identifier of the shared guide
   */
  data class SharedMonument(val ownerUid: String, val guideId: String) : NotificationNavigation
}

/**
 * A notification destination together with its back-stack behavior.
 *
 * @property navigation destination requested by the notification
 * @property preserveBackStack whether navigation should retain the current back stack
 */
data class NotificationNavigationRequest(
    val navigation: NotificationNavigation,
    val preserveBackStack: Boolean,
)

/** Parses notification intents into app navigation destinations. */
object NotificationNavigationParser {
  /**
   * Returns the destination encoded by [intent], or `null` for unsupported intents.
   *
   * @param intent notification intent to parse, or `null`
   */
  fun parse(intent: Intent?): NotificationNavigation? {
    return when (intent?.action) {
      FriendRequestNotifications.ACTION_OPEN_FRIENDS -> NotificationNavigation.Friends
      MonumentShareNotifications.ACTION_OPEN_SHARED_MONUMENT -> parseSharedMonument(intent)
      else -> null
    }
  }

  private fun parseSharedMonument(intent: Intent): NotificationNavigation.SharedMonument? {
    val ownerUid =
        intent
            .getStringExtra(MonumentShareNotifications.EXTRA_SHARED_MONUMENT_OWNER_UID)
            ?.takeIf(String::isNotBlank)
    val guideId =
        intent
            .getStringExtra(MonumentShareNotifications.EXTRA_SHARED_MONUMENT_GUIDE_ID)
            ?.takeIf(String::isNotBlank)
    if (ownerUid == null || guideId == null) return null
    return NotificationNavigation.SharedMonument(ownerUid, guideId)
  }
}
