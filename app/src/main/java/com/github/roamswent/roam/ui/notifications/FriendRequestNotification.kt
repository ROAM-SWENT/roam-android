/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.roamswent.roam.MainActivity
import com.github.roamswent.roam.R
import com.github.roamswent.roam.ui.components.ProfilePictureBitmap

private const val DATA_TYPE = "type"
private const val DATA_REQUEST_ID = "requestId"
private const val DATA_REQUESTER_UID = "requesterUid"
private const val DATA_REQUESTER_USERNAME = "requesterUsername"
private const val DATA_RECIPIENT_UID = "recipientUid"
private const val TYPE_FRIEND_REQUEST = "friend_request"
private const val ACTION_ACCEPT = "accept"
private const val ACTION_DECLINE = "decline"

/** Validated data required to render a friend-request notification. */
data class FriendRequestNotificationData(
    val requestId: String,
    val requesterUid: String,
    val requesterUsername: String,
    val recipientUid: String,
) {
  /** Returns whether the notification belongs to the currently signed-in account. */
  fun isForAccount(uid: String?): Boolean = uid != null && recipientUid == uid

  companion object {
    /**
     * Converts a supported FCM data payload into notification data.
     *
     * Returns null for unknown notification types or missing identifiers, so malformed pushes
     * cannot create actions against an incomplete request.
     */
    fun from(data: Map<String, String>): FriendRequestNotificationData? {
      if (data[DATA_TYPE] != TYPE_FRIEND_REQUEST) return null
      val requestId = data[DATA_REQUEST_ID]?.takeIf(String::isNotBlank) ?: return null
      val requesterUid = data[DATA_REQUESTER_UID]?.takeIf(String::isNotBlank) ?: return null
      val requesterUsername =
          data[DATA_REQUESTER_USERNAME]?.takeIf(String::isNotBlank) ?: return null
      val recipientUid = data[DATA_RECIPIENT_UID]?.takeIf(String::isNotBlank) ?: return null
      return FriendRequestNotificationData(requestId, requesterUid, requesterUsername, recipientUid)
    }
  }
}

/** The operation invoked by one of the friend-request notification buttons. */
internal enum class FriendRequestAction(val value: String) {
  ACCEPT(ACTION_ACCEPT),
  DECLINE(ACTION_DECLINE);

  companion object {
    /** Resolves a serialized action value, rejecting values not created by this app. */
    fun from(value: String?): FriendRequestAction? = entries.firstOrNull { it.value == value }
  }
}

/** Builds Android notifications and intents for the friend-request notification feature. */
internal object FriendRequestNotifications {
  const val CHANNEL_ID = "friend_requests"
  const val ACTION_OPEN_FRIENDS = "com.github.roamswent.roam.OPEN_FRIENDS"
  const val EXTRA_REQUEST_ID = "friend_request_id"
  const val EXTRA_RECIPIENT_UID = "friend_request_recipient_uid"
  const val EXTRA_ACTION = "friend_request_action"

  /** Creates the Android 8+ notification channel; repeated calls are safe. */
  fun createChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
          android.app.NotificationChannel(
              CHANNEL_ID,
              context.getString(R.string.notification_friend_requests_channel),
              android.app.NotificationManager.IMPORTANCE_HIGH,
          )
      context
          .getSystemService(android.app.NotificationManager::class.java)
          .createNotificationChannel(channel)
    }
  }

  /**
   * Displays an incoming request with Accept and Decline actions when notifications are allowed.
   */
  fun showFriendRequest(context: Context, data: FriendRequestNotificationData) {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
    createChannel(context)

    val body = context.getString(R.string.notification_friend_request_body, data.requesterUsername)
    val title =
        SpannableString(context.getString(R.string.notification_friend_request_title)).apply {
          setSpan(
              StyleSpan(Typeface.BOLD),
              0,
              length,
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
          )
        }
    val notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_friend)
            .setLargeIcon(ProfilePictureBitmap.create(context, data.requesterUsername))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openFriendsPendingIntent(context, data.requestId))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                NotificationCompat.Action.Builder(
                        null,
                        context.getString(R.string.notification_friend_request_accept),
                        actionPendingIntent(
                            context,
                            data.requestId,
                            data.recipientUid,
                            FriendRequestAction.ACCEPT,
                        ),
                    )
                    .build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                        null,
                        context.getString(R.string.notification_friend_request_decline),
                        actionPendingIntent(
                            context,
                            data.requestId,
                            data.recipientUid,
                            FriendRequestAction.DECLINE,
                        ),
                    )
                    .build()
            )
            .build()

    NotificationManagerCompat.from(context).notify(data.requestId.hashCode(), notification)
  }

  /** Dismisses the notification associated with [requestId]. */
  fun cancel(context: Context, requestId: String) {
    NotificationManagerCompat.from(context).cancel(requestId.hashCode())
  }

  /** Dismisses visible friend-request notifications when the account signs out. */
  fun cancelAllFriendRequests(context: Context) {
    NotificationCleanup.cancelChannels(context, setOf(CHANNEL_ID))
  }

  /** Replaces a failed action notification with an explanation and a Friends screen shortcut. */
  fun showActionFailure(context: Context, requestId: String) {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
    createChannel(context)
    val notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_friend)
            .setContentTitle(context.getString(R.string.notification_friend_request_title))
            .setContentText(context.getString(R.string.notification_friend_request_action_failed))
            .setContentIntent(openFriendsPendingIntent(context, requestId))
            .setAutoCancel(true)
            .build()
    NotificationManagerCompat.from(context).notify(requestId.hashCode(), notification)
  }

  private fun openFriendsPendingIntent(context: Context, requestId: String): PendingIntent {
    val intent =
        Intent(context, MainActivity::class.java).apply {
          action = ACTION_OPEN_FRIENDS
          putExtra(EXTRA_REQUEST_ID, requestId)
          flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    return PendingIntent.getActivity(
        context,
        requestId.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun actionPendingIntent(
      context: Context,
      requestId: String,
      recipientUid: String,
      action: FriendRequestAction,
  ): PendingIntent {
    val intent =
        Intent(context, FriendRequestActionReceiver::class.java).apply {
          putExtra(EXTRA_REQUEST_ID, requestId)
          putExtra(EXTRA_RECIPIENT_UID, recipientUid)
          putExtra(EXTRA_ACTION, action.value)
        }
    val requestCode = 31 * requestId.hashCode() + action.value.hashCode()
    return PendingIntent.getBroadcast(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }
}
