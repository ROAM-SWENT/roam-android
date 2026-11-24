/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonumentShareNotificationDataTest {
  @Test
  fun `from parses a monument share payload`() {
    val notification =
        MonumentShareNotificationData.from(
            mapOf(
                "type" to "monument_share",
                "guideId" to "guide-1",
                "senderUid" to "alice",
                "senderUsername" to "Alice",
                "recipientUid" to "bob",
                "monumentName" to "The Tower",
            )
        )

    assertEquals(
        MonumentShareNotificationData("guide-1", "alice", "Alice", "bob", "The Tower"),
        notification,
    )
    assertTrue(checkNotNull(notification).isForAccount("bob"))
    assertFalse(checkNotNull(notification).isForAccount("alice"))
    assertFalse(checkNotNull(notification).isForAccount(null))
  }

  @Test
  fun `from rejects a payload with an invalid required field`() {
    val payload =
        mutableMapOf(
            "type" to "monument_share",
            "guideId" to "guide-1",
            "senderUid" to "alice",
            "senderUsername" to "Alice",
            "recipientUid" to "bob",
            "monumentName" to "The Tower",
        )

    listOf("guideId", "senderUid", "senderUsername", "recipientUid", "monumentName").forEach {
      payload[it] = " "
      assertNull(MonumentShareNotificationData.from(payload))
      payload[it] =
          when (it) {
            "guideId" -> "guide-1"
            "senderUid" -> "alice"
            "senderUsername" -> "Alice"
            "recipientUid" -> "bob"
            else -> "The Tower"
          }
    }
  }

  @Test
  fun `from rejects a payload with a missing or incorrect type`() {
    assertNull(MonumentShareNotificationData.from(emptyMap()))
    assertNull(MonumentShareNotificationData.from(mapOf("type" to "friend_request")))
  }
}
