/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.notifications

import android.content.Context
import androidx.core.app.NotificationManagerCompat

/** Coordinates cleanup of account-scoped notifications when the user signs out. */
internal object NotificationCleanup {
  private val accountNotificationChannels =
      setOf(
          FriendRequestNotifications.CHANNEL_ID,
          MonumentShareNotifications.CHANNEL_ID,
      )

  /** Dismisses all notifications that belong to the signed-in account. */
  fun cancelAccountNotifications(context: Context) {
    val manager = NotificationManagerCompat.from(context)
    manager.activeNotifications
        .filter { it.notification.channelId in accountNotificationChannels }
        .forEach { manager.cancel(it.tag, it.id) }
  }

  /** Dismisses notifications belonging to the supplied channels. */
  fun cancelChannels(context: Context, channels: Set<String>) {
    val manager = NotificationManagerCompat.from(context)
    manager.activeNotifications
        .filter { it.notification.channelId in channels }
        .forEach { manager.cancel(it.tag, it.id) }
  }
}
