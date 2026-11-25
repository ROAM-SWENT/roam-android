/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.roamswent.roam.model.firestore.RoamFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.RemoteMessage
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class NotificationMessagingServiceTest {
  private val auth = mockk<FirebaseAuth>()
  private val user = mockk<FirebaseUser>()
  private val firestore = mockk<FirebaseFirestore>(relaxed = true)
  private val context = ApplicationProvider.getApplicationContext<Context>()

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun onMessageReceived_ignoresMalformedPayload() {
    mockkStatic(FirebaseAuth::class)
    every { FirebaseAuth.getInstance() } returns auth
    mockkObject(FriendRequestNotifications)

    NotificationMessagingService().onMessageReceived(RemoteMessage.Builder("sender").build())

    verify(exactly = 0) { FriendRequestNotifications.showFriendRequest(any(), any()) }
  }

  @Test
  fun onMessageReceived_ignoresPayloadForAnotherAccount() {
    mockkStatic(FirebaseAuth::class)
    every { FirebaseAuth.getInstance() } returns auth
    every { auth.currentUser } returns user
    every { user.uid } returns "user-b"
    mockkObject(FriendRequestNotifications)

    NotificationMessagingService()
        .onMessageReceived(
            RemoteMessage.Builder("sender")
                .setData(
                    mapOf(
                        "type" to "friend_request",
                        "requestId" to "request",
                        "requesterUid" to "user-a",
                        "requesterUsername" to "Alice",
                        "recipientUid" to "user-c",
                    )
                )
                .build()
        )

    verify(exactly = 0) { FriendRequestNotifications.showFriendRequest(any(), any()) }
  }

  @Test
  fun onMessageReceived_ignoresMonumentShareForAnotherAccountOrWhenSignedOut() {
    mockkStatic(FirebaseAuth::class)
    every { FirebaseAuth.getInstance() } returns auth
    every { auth.currentUser } returns user
    every { user.uid } returns "user-b"
    mockkObject(MonumentShareNotifications)

    val message =
        RemoteMessage.Builder("sender")
            .setData(
                mapOf(
                    "type" to "monument_share",
                    "guideId" to "guide",
                    "senderUid" to "user-a",
                    "senderUsername" to "Alice",
                    "recipientUid" to "user-c",
                    "monumentName" to "The Tower",
                )
            )
            .build()

    NotificationMessagingService().onMessageReceived(message)
    every { auth.currentUser } returns null
    NotificationMessagingService().onMessageReceived(message)

    verify(exactly = 0) { MonumentShareNotifications.show(any(), any()) }
  }

  @Test
  fun onMessageReceived_showsPayloadForCurrentAccount() {
    mockkStatic(FirebaseAuth::class)
    every { FirebaseAuth.getInstance() } returns auth
    every { auth.currentUser } returns user
    every { user.uid } returns "user-b"
    mockkObject(FriendRequestNotifications)
    every { FriendRequestNotifications.showFriendRequest(any(), any()) } just Runs

    NotificationMessagingService()
        .onMessageReceived(
            RemoteMessage.Builder("sender")
                .setData(
                    mapOf(
                        "type" to "friend_request",
                        "requestId" to "request",
                        "requesterUid" to "user-a",
                        "requesterUsername" to "Alice",
                        "recipientUid" to "user-b",
                    )
                )
                .build()
        )

    verify {
      FriendRequestNotifications.showFriendRequest(
          any(),
          FriendRequestNotificationData("request", "user-a", "Alice", "user-b"),
      )
    }
  }

  @Test
  fun onRegistered_whenNotificationsDisabled_doesNotPersistInstallation() {
    val manager = context.getSystemService(android.app.NotificationManager::class.java)
    shadowOf(manager).setNotificationsEnabled(false)

    Robolectric.buildService(NotificationMessagingService::class.java)
        .create()
        .get()
        .onRegistered("fid-a")
  }

  @Test
  fun onRegistered_whenNotificationsEnabled_persistsInstallation() {
    val manager = context.getSystemService(android.app.NotificationManager::class.java)
    shadowOf(manager).setNotificationsEnabled(true)
    mockkObject(RoamFirestore)
    every { RoamFirestore.instance } returns firestore
    mockkStatic(FirebaseAuth::class)
    every { FirebaseAuth.getInstance() } returns auth
    mockkConstructor(
        com.github.roamswent.roam.model.notification
            .FirestoreNotificationInstallationRepository::class
    )
    coEvery {
      anyConstructed<
              com.github.roamswent.roam.model.notification.FirestoreNotificationInstallationRepository
          >()
          .saveInstallationId("fid-a")
    } returns Unit

    Robolectric.buildService(NotificationMessagingService::class.java)
        .create()
        .get()
        .onRegistered("fid-a")

    coVerify(timeout = 2_000) {
      anyConstructed<
              com.github.roamswent.roam.model.notification.FirestoreNotificationInstallationRepository
          >()
          .saveInstallationId("fid-a")
    }
  }

  @Test
  fun onRegistered_whenPersistenceFails_doesNotPropagateFromCallback() {
    val manager = context.getSystemService(android.app.NotificationManager::class.java)
    shadowOf(manager).setNotificationsEnabled(true)
    mockkObject(RoamFirestore)
    every { RoamFirestore.instance } returns firestore
    mockkStatic(FirebaseAuth::class)
    every { FirebaseAuth.getInstance() } returns auth
    mockkConstructor(
        com.github.roamswent.roam.model.notification
            .FirestoreNotificationInstallationRepository::class
    )
    coEvery {
      anyConstructed<
              com.github.roamswent.roam.model.notification.FirestoreNotificationInstallationRepository
          >()
          .saveInstallationId("fid-a")
    } throws IllegalStateException("write failed")

    Robolectric.buildService(NotificationMessagingService::class.java)
        .create()
        .get()
        .onRegistered("fid-a")

    coVerify(timeout = 2_000) {
      anyConstructed<
              com.github.roamswent.roam.model.notification.FirestoreNotificationInstallationRepository
          >()
          .saveInstallationId("fid-a")
    }
  }
}
