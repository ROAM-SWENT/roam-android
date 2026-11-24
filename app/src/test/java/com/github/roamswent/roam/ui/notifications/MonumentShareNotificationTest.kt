/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.roamswent.roam.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MonumentShareNotificationTest {
  @Test
  fun showsContentAvatarAndSharedDetailIntent() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = context.getSystemService(NotificationManager::class.java)
    shadowOf(manager).setNotificationsEnabled(true)

    MonumentShareNotifications.show(
        context,
        MonumentShareNotificationData("guide-1", "alice", "Alice", "bob", "The Tower"),
    )

    val notification = shadowOf(manager).allNotifications.single()
    assertEquals(MonumentShareNotifications.CHANNEL_ID, notification.channelId)
    assertEquals(
        context.getString(R.string.notification_monument_share_title),
        notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
    )
    assertEquals(
        context.getString(R.string.notification_monument_share_body, "Alice", "The Tower"),
        notification.extras.getString(Notification.EXTRA_TEXT),
    )
    assertNotNull(notification.extras.getParcelable(Notification.EXTRA_LARGE_ICON))
    val intent = shadowOf(notification.contentIntent).savedIntent
    assertEquals(MonumentShareNotifications.ACTION_OPEN_SHARED_MONUMENT, intent.action)
    assertEquals(
        "alice",
        intent.getStringExtra(MonumentShareNotifications.EXTRA_SHARED_MONUMENT_OWNER_UID),
    )
    assertEquals(
        "guide-1",
        intent.getStringExtra(MonumentShareNotifications.EXTRA_SHARED_MONUMENT_GUIDE_ID),
    )
  }

  @Test
  fun doesNotShowNotificationWhenNotificationsAreDisabled() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = context.getSystemService(NotificationManager::class.java)
    shadowOf(manager).setNotificationsEnabled(false)
    val notificationCount = shadowOf(manager).allNotifications.size

    MonumentShareNotifications.show(
        context,
        MonumentShareNotificationData("guide-2", "alice", "Alice", "bob", "The Bridge"),
    )

    assertEquals(notificationCount, shadowOf(manager).allNotifications.size)
  }

  @Test
  fun cancelAllMonumentShares_removesOnlyMonumentShareNotifications() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = context.getSystemService(NotificationManager::class.java)
    shadowOf(manager).setNotificationsEnabled(true)

    MonumentShareNotifications.show(
        context,
        MonumentShareNotificationData("guide-3", "alice", "Alice", "bob", "The Castle"),
    )
    FriendRequestNotifications.showFriendRequest(
        context,
        FriendRequestNotificationData("request-3", "alice", "Alice", "bob"),
    )

    MonumentShareNotifications.cancelAllMonumentShares(context)

    assertEquals(1, shadowOf(manager).allNotifications.size)
    assertEquals(
        FriendRequestNotifications.CHANNEL_ID,
        shadowOf(manager).allNotifications.single().channelId,
    )
  }

  @Test
  fun cancelAccountNotifications_removesAllAccountNotificationTypes() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = context.getSystemService(NotificationManager::class.java)
    shadowOf(manager).setNotificationsEnabled(true)

    MonumentShareNotifications.show(
        context,
        MonumentShareNotificationData("guide-4", "alice", "Alice", "bob", "The Museum"),
    )
    FriendRequestNotifications.showFriendRequest(
        context,
        FriendRequestNotificationData("request-4", "alice", "Alice", "bob"),
    )

    NotificationCleanup.cancelAccountNotifications(context)

    assertEquals(0, shadowOf(manager).allNotifications.size)
  }
}
