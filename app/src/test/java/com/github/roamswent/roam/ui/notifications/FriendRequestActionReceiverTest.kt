/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.github.roamswent.roam.model.firestore.RoamFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FriendRequestActionReceiverTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val auth = mockk<FirebaseAuth>()
  private val user = mockk<FirebaseUser>()
  private val firestore = mockk<FirebaseFirestore>(relaxed = true)

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun onReceive_ignoresIncompleteIntent() {
    mockkStatic(FirebaseAuth::class)
    every { FirebaseAuth.getInstance() } returns auth
    mockkObject(FriendRequestNotifications)

    FriendRequestActionReceiver().onReceive(context, Intent())

    verify(exactly = 0) { FriendRequestNotifications.cancel(any(), any()) }
  }

  @Test
  fun onReceive_cancelsNotificationForAnotherAccount() {
    mockkStatic(FirebaseAuth::class)
    every { FirebaseAuth.getInstance() } returns auth
    every { auth.currentUser } returns user
    every { user.uid } returns "user-b"
    mockkObject(FriendRequestNotifications)
    every { FriendRequestNotifications.cancel(any(), any()) } just Runs
    val intent =
        Intent().apply {
          putExtra(FriendRequestNotifications.EXTRA_REQUEST_ID, "request")
          putExtra(FriendRequestNotifications.EXTRA_RECIPIENT_UID, "user-c")
          putExtra(FriendRequestNotifications.EXTRA_ACTION, "accept")
        }

    FriendRequestActionReceiver().onReceive(context, intent)

    verify { FriendRequestNotifications.cancel(context, "request") }
  }

  @Test
  fun onReceive_cancelsNotificationWhenSignedOut() {
    mockkStatic(FirebaseAuth::class)
    every { FirebaseAuth.getInstance() } returns auth
    every { auth.currentUser } returns null
    mockkObject(FriendRequestNotifications)
    every { FriendRequestNotifications.cancel(any(), any()) } just Runs

    FriendRequestActionReceiver().onReceive(context, validIntent("accept"))

    verify { FriendRequestNotifications.cancel(context, "request") }
  }

  @Test
  fun onReceive_acceptsRequestAndCancelsNotification() {
    mockkStatic(FirebaseAuth::class)
    every { FirebaseAuth.getInstance() } returns auth
    every { auth.currentUser } returns user
    every { user.uid } returns "user-b"
    mockkObject(RoamFirestore)
    every { RoamFirestore.instance } returns firestore
    mockkConstructor(com.github.roamswent.roam.model.friend.FirestoreFriendRepository::class)
    coEvery {
      anyConstructed<com.github.roamswent.roam.model.friend.FirestoreFriendRepository>()
          .acceptFriendRequest("request")
    } returns Unit
    mockkObject(FriendRequestNotifications)
    every { FriendRequestNotifications.cancel(any(), any()) } just Runs
    val intent = validIntent("accept")

    val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
    val receiver = spyk(FriendRequestActionReceiver())
    every { receiver.goAsync() } returns pendingResult
    receiver.onReceive(context, intent)

    coVerify(timeout = 2_000) {
      anyConstructed<com.github.roamswent.roam.model.friend.FirestoreFriendRepository>()
          .acceptFriendRequest("request")
    }
    verify { FriendRequestNotifications.cancel(context, "request") }
  }

  @Test
  fun onReceive_rejectsRequestAndCancelsNotification() {
    mockkStatic(FirebaseAuth::class)
    every { FirebaseAuth.getInstance() } returns auth
    every { auth.currentUser } returns user
    every { user.uid } returns "user-b"
    mockkObject(RoamFirestore)
    every { RoamFirestore.instance } returns firestore
    mockkConstructor(com.github.roamswent.roam.model.friend.FirestoreFriendRepository::class)
    coEvery {
      anyConstructed<com.github.roamswent.roam.model.friend.FirestoreFriendRepository>()
          .rejectFriendRequest("request")
    } returns Unit
    mockkObject(FriendRequestNotifications)
    every { FriendRequestNotifications.cancel(any(), any()) } just Runs
    val intent = validIntent("decline")

    val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
    val receiver = spyk(FriendRequestActionReceiver())
    every { receiver.goAsync() } returns pendingResult
    receiver.onReceive(context, intent)

    coVerify(timeout = 2_000) {
      anyConstructed<com.github.roamswent.roam.model.friend.FirestoreFriendRepository>()
          .rejectFriendRequest("request")
    }
    verify { FriendRequestNotifications.cancel(context, "request") }
  }

  @Test
  fun onReceive_showsFailureWhenAcceptThrows() {
    mockkStatic(FirebaseAuth::class)
    every { FirebaseAuth.getInstance() } returns auth
    every { auth.currentUser } returns user
    every { user.uid } returns "user-b"
    mockkObject(RoamFirestore)
    every { RoamFirestore.instance } returns firestore
    mockkConstructor(com.github.roamswent.roam.model.friend.FirestoreFriendRepository::class)
    coEvery {
      anyConstructed<com.github.roamswent.roam.model.friend.FirestoreFriendRepository>()
          .acceptFriendRequest("request")
    } throws IllegalStateException("failure")
    mockkObject(FriendRequestNotifications)
    every { FriendRequestNotifications.showActionFailure(any(), any()) } just Runs

    val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
    val receiver = spyk(FriendRequestActionReceiver())
    every { receiver.goAsync() } returns pendingResult
    receiver.onReceive(context, validIntent("accept"))

    coVerify(timeout = 2_000) {
      anyConstructed<com.github.roamswent.roam.model.friend.FirestoreFriendRepository>()
          .acceptFriendRequest("request")
    }
    verify { FriendRequestNotifications.showActionFailure(context, "request") }
    verify { pendingResult.finish() }
  }

  @Test
  fun onReceive_showsFailureWhenDeclineThrows() {
    mockkStatic(FirebaseAuth::class)
    every { FirebaseAuth.getInstance() } returns auth
    every { auth.currentUser } returns user
    every { user.uid } returns "user-b"
    mockkObject(RoamFirestore)
    every { RoamFirestore.instance } returns firestore
    mockkConstructor(com.github.roamswent.roam.model.friend.FirestoreFriendRepository::class)
    coEvery {
      anyConstructed<com.github.roamswent.roam.model.friend.FirestoreFriendRepository>()
          .rejectFriendRequest("request")
    } throws IllegalStateException("failure")
    mockkObject(FriendRequestNotifications)
    every { FriendRequestNotifications.showActionFailure(any(), any()) } just Runs

    val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
    val receiver = spyk(FriendRequestActionReceiver())
    every { receiver.goAsync() } returns pendingResult
    receiver.onReceive(context, validIntent("decline"))

    coVerify(timeout = 2_000) {
      anyConstructed<com.github.roamswent.roam.model.friend.FirestoreFriendRepository>()
          .rejectFriendRequest("request")
    }
    verify { FriendRequestNotifications.showActionFailure(context, "request") }
    verify { pendingResult.finish() }
  }

  private fun validIntent(action: String) =
      Intent().apply {
        putExtra(FriendRequestNotifications.EXTRA_REQUEST_ID, "request")
        putExtra(FriendRequestNotifications.EXTRA_RECIPIENT_UID, "user-b")
        putExtra(FriendRequestNotifications.EXTRA_ACTION, action)
      }
}
