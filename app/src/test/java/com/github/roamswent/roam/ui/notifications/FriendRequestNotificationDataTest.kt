/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the notification payload contract shared by FCM and the Android app. */
class FriendRequestNotificationDataTest {
  @Test
  fun `from parses a friend request payload`() {
    val notification =
        FriendRequestNotificationData.from(
            mapOf(
                "type" to "friend_request",
                "requestId" to "alice~bob",
                "requesterUid" to "alice",
                "requesterUsername" to "Alice",
                "recipientUid" to "bob",
            )
        )

    assertEquals(FriendRequestNotificationData("alice~bob", "alice", "Alice", "bob"), notification)
    assertTrue(checkNotNull(notification).isForAccount("bob"))
    assertFalse(checkNotNull(notification).isForAccount("alice"))
    assertFalse(checkNotNull(notification).isForAccount(null))
  }

  @Test
  fun `from rejects unsupported or incomplete payloads`() {
    assertNull(FriendRequestNotificationData.from(mapOf("type" to "other")))
    assertNull(
        FriendRequestNotificationData.from(
            mapOf(
                "type" to "friend_request",
                "requestId" to "alice~bob",
                "requesterUid" to "alice",
            )
        )
    )
    assertNull(
        FriendRequestNotificationData.from(
            mapOf(
                "type" to "friend_request",
                "requestId" to "",
                "requesterUid" to "alice",
                "requesterUsername" to "Alice",
            )
        )
    )
    assertNull(
        FriendRequestNotificationData.from(
            mapOf(
                "type" to "friend_request",
                "requestId" to "alice~bob",
                "requesterUid" to "alice",
                "requesterUsername" to "Alice",
                "recipientUid" to "",
            )
        )
    )
  }
}
