/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.notifications

import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.github.roamswent.roam.model.notification.FirestoreNotificationInstallationRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives notification data pushes and refreshes this installation's registered FID. */
class NotificationMessagingService : FirebaseMessagingService() {
  /** Persists refreshed FCM installation IDs so future pushes reach this installation. */
  override fun onRegistered(installationId: String) {
    if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
    // Let this short write finish even if Android stops the service after the callback returns.
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      try {
        FirestoreNotificationInstallationRepository().saveInstallationId(installationId)
      } catch (exception: Exception) {
        Log.e(TAG, "Could not save refreshed notification installation ID.", exception)
      }
    }
  }

  /** Converts supported data messages into local notifications with request actions. */
  override fun onMessageReceived(message: RemoteMessage) {
    val notification = FriendRequestNotificationData.from(message.data)
    val sharedMonument = MonumentShareNotificationData.from(message.data)
    if (notification == null && sharedMonument == null) {
      Log.w(TAG, "Ignoring an invalid or unsupported push notification.")
      return
    }
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    if (
        notification != null && !notification.isForAccount(currentUid) ||
            sharedMonument != null && !sharedMonument.isForAccount(currentUid)
    ) {
      Log.w(TAG, "Ignoring a notification registered to a different account.")
      return
    }
    if (notification != null) {
      FriendRequestNotifications.showFriendRequest(this, notification)
    } else {
      MonumentShareNotifications.show(this, checkNotNull(sharedMonument))
    }
  }

  private companion object {
    const val TAG = "NotificationMessaging"
  }
}
