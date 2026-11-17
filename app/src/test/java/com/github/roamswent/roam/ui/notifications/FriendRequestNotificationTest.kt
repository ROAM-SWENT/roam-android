/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.test.core.app.ApplicationProvider
import com.github.roamswent.roam.R
import com.github.roamswent.roam.ui.components.ProfilePictureBitmap
import com.github.roamswent.roam.ui.components.profilePictureInitial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Verifies the notification content and inline request actions shown to Android users. */
@RunWith(RobolectricTestRunner::class)
class FriendRequestNotificationTest {
  @Test
  @Suppress("DEPRECATION")
  fun displaysLocalizedRequestTextAndAcceptDeclineActions() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = context.getSystemService(NotificationManager::class.java)
    shadowOf(manager).setNotificationsEnabled(true)

    FriendRequestNotifications.showFriendRequest(
        context,
        FriendRequestNotificationData("alice~bob", "alice", "Alice", "bob"),
    )

    val notification = shadowOf(manager).allNotifications.single()
    val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)
    assertEquals(context.getString(R.string.notification_friend_request_title), title.toString())
    assertTrue(title is Spanned)
    val titleSpans = title as Spanned
    assertEquals(
        Typeface.BOLD,
        titleSpans.getSpans(0, title.length, StyleSpan::class.java).single().style,
    )
    assertEquals(
        context.getString(R.string.notification_friend_request_body, "Alice"),
        notification.extras.getString(Notification.EXTRA_TEXT),
    )
    assertNotNull(notification.extras.getParcelable(Notification.EXTRA_LARGE_ICON))
    assertEquals(2, notification.actions.size)
    assertEquals(
        context.getString(R.string.notification_friend_request_accept),
        notification.actions[0].title.toString(),
    )
    assertEquals(
        context.getString(R.string.notification_friend_request_decline),
        notification.actions[1].title.toString(),
    )
  }

  @Test
  fun requesterAvatarIsCircularAndIncludesTheInitial() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val avatar = ProfilePictureBitmap.create(context, "Alice")

    assertEquals(avatar.width, avatar.height)
    assertTrue(avatar.width > 1)
    assertEquals("A", profilePictureInitial("Alice"))
    assertEquals("?", profilePictureInitial("   "))
  }
}
