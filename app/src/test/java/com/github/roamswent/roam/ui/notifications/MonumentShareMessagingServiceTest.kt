/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.notifications

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.messaging.RemoteMessage
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MonumentShareMessagingServiceTest {
  private val auth = mockk<FirebaseAuth>()
  private val user = mockk<FirebaseUser>()

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun showsMonumentShareForCurrentAccount() {
    mockkStatic(FirebaseAuth::class)
    every { FirebaseAuth.getInstance() } returns auth
    every { auth.currentUser } returns user
    every { user.uid } returns "user-b"
    mockkObject(MonumentShareNotifications)
    every { MonumentShareNotifications.show(any(), any()) } just Runs

    NotificationMessagingService()
        .onMessageReceived(
            RemoteMessage.Builder("sender")
                .setData(
                    mapOf(
                        "type" to "monument_share",
                        "guideId" to "guide-1",
                        "senderUid" to "user-a",
                        "senderUsername" to "Alice",
                        "recipientUid" to "user-b",
                        "monumentName" to "The Tower",
                    )
                )
                .build()
        )

    verify {
      MonumentShareNotifications.show(
          any(),
          MonumentShareNotificationData("guide-1", "user-a", "Alice", "user-b", "The Tower"),
      )
    }
  }
}
