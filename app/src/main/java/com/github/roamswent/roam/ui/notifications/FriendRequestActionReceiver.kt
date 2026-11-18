/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.github.roamswent.roam.model.friend.FirestoreFriendRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Applies Accept or Decline from an Android notification action. */
class FriendRequestActionReceiver : BroadcastReceiver() {
  /**
   * Completes the authenticated repository operation off the main thread.
   *
   * goAsync keeps the receiver alive while Firestore completes; failures leave a notification that
   * routes the user to Friends so the request can be handled in the app.
   */
  override fun onReceive(context: Context, intent: Intent) {
    val requestId =
        intent
            .getStringExtra(FriendRequestNotifications.EXTRA_REQUEST_ID)
            ?.takeIf(String::isNotBlank)
    val recipientUid =
        intent
            .getStringExtra(FriendRequestNotifications.EXTRA_RECIPIENT_UID)
            ?.takeIf(String::isNotBlank)
    val action =
        FriendRequestAction.from(intent.getStringExtra(FriendRequestNotifications.EXTRA_ACTION))
    if (requestId == null || recipientUid == null || action == null) {
      Log.w(TAG, "Ignoring an invalid friend-request notification action.")
      return
    }
    if (FirebaseAuth.getInstance().currentUser?.uid != recipientUid) {
      Log.w(TAG, "Ignoring a notification action for a different account.")
      FriendRequestNotifications.cancel(context, requestId)
      return
    }

    val pendingResult = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      try {
        val repository = FirestoreFriendRepository()
        when (action) {
          FriendRequestAction.ACCEPT -> repository.acceptFriendRequest(requestId)
          FriendRequestAction.DECLINE -> repository.rejectFriendRequest(requestId)
        }
        FriendRequestNotifications.cancel(context, requestId)
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Log.e(TAG, "Could not process friend-request notification action.", exception)
        FriendRequestNotifications.showActionFailure(context, requestId)
      } finally {
        pendingResult.finish()
      }
    }
  }

  private companion object {
    const val TAG = "FriendRequestAction"
  }
}
