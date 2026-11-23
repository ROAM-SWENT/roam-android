/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.notifications

import android.content.Intent

sealed interface NotificationNavigation {
  data object Friends : NotificationNavigation

  data class SharedMonument(val ownerUid: String, val guideId: String) : NotificationNavigation
}

object NotificationNavigationParser {
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
