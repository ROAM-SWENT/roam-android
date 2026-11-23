/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.roamswent.roam.MainActivity
import com.github.roamswent.roam.R
import com.github.roamswent.roam.ui.components.ProfilePictureBitmap

private const val DATA_TYPE = "type"
private const val DATA_GUIDE_ID = "guideId"
private const val DATA_SENDER_UID = "senderUid"
private const val DATA_SENDER_USERNAME = "senderUsername"
private const val DATA_RECIPIENT_UID = "recipientUid"
private const val DATA_MONUMENT_NAME = "monumentName"
private const val TYPE_MONUMENT_SHARE = "monument_share"

data class MonumentShareNotificationData(
    val guideId: String,
    val senderUid: String,
    val senderUsername: String,
    val recipientUid: String,
    val monumentName: String,
) {
  fun isForAccount(uid: String?): Boolean = uid != null && recipientUid == uid

  companion object {
    fun from(data: Map<String, String>): MonumentShareNotificationData? {
      if (data[DATA_TYPE] != TYPE_MONUMENT_SHARE) return null
      val guideId = data[DATA_GUIDE_ID]?.takeIf(String::isNotBlank) ?: return null
      val senderUid = data[DATA_SENDER_UID]?.takeIf(String::isNotBlank) ?: return null
      val senderUsername = data[DATA_SENDER_USERNAME]?.takeIf(String::isNotBlank) ?: return null
      val recipientUid = data[DATA_RECIPIENT_UID]?.takeIf(String::isNotBlank) ?: return null
      val monumentName = data[DATA_MONUMENT_NAME]?.takeIf(String::isNotBlank) ?: return null
      return MonumentShareNotificationData(
          guideId,
          senderUid,
          senderUsername,
          recipientUid,
          monumentName,
      )
    }
  }
}

internal object MonumentShareNotifications {
  const val ACTION_OPEN_SHARED_MONUMENT = "com.github.roamswent.roam.OPEN_SHARED_MONUMENT"
  const val EXTRA_SHARED_MONUMENT_OWNER_UID = "shared_monument_owner_uid"
  const val EXTRA_SHARED_MONUMENT_GUIDE_ID = "shared_monument_guide_id"

  fun show(context: Context, data: MonumentShareNotificationData) {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
    FriendRequestNotifications.createChannel(context)
    val body =
        context.getString(
            R.string.notification_monument_share_body,
            data.senderUsername,
            data.monumentName,
        )
    val title =
        SpannableString(context.getString(R.string.notification_monument_share_title)).apply {
          setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    val notification =
        NotificationCompat.Builder(context, FriendRequestNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_friend)
            .setLargeIcon(ProfilePictureBitmap.create(context, data.senderUsername))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openPendingIntent(context, data))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    NotificationManagerCompat.from(context).notify(notificationId(data), notification)
  }

  private fun openPendingIntent(
      context: Context,
      data: MonumentShareNotificationData,
  ): PendingIntent {
    val intent =
        Intent(context, MainActivity::class.java).apply {
          action = ACTION_OPEN_SHARED_MONUMENT
          putExtra(EXTRA_SHARED_MONUMENT_OWNER_UID, data.senderUid)
          putExtra(EXTRA_SHARED_MONUMENT_GUIDE_ID, data.guideId)
          flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    return PendingIntent.getActivity(
        context,
        notificationId(data),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun notificationId(data: MonumentShareNotificationData): Int =
      31 * data.senderUid.hashCode() + data.guideId.hashCode()
}
