/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.model.permission

import android.content.Context
import android.content.SharedPreferences

/** The outcome of the last notification-permission request made by the app. */
enum class NotificationPermissionDecision {
  NOT_ASKED,
  GRANTED,
  DENIED,
}

/** Persists whether the app has already requested permission to post notifications. */
interface NotificationPermissionDecisionRepository {
  fun getDecision(): NotificationPermissionDecision

  fun saveDecision(decision: NotificationPermissionDecision)
}

/** Stores the notification-permission decision locally. */
class SharedPreferencesNotificationPermissionDecisionRepository(context: Context) :
    NotificationPermissionDecisionRepository {

  private val preferences: SharedPreferences =
      context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  override fun getDecision(): NotificationPermissionDecision {
    val value =
        preferences.getString(DECISION_KEY, null) ?: return NotificationPermissionDecision.NOT_ASKED
    return NotificationPermissionDecision.entries.firstOrNull { it.name == value }
        ?: NotificationPermissionDecision.NOT_ASKED
  }

  override fun saveDecision(decision: NotificationPermissionDecision) {
    preferences.edit().putString(DECISION_KEY, decision.name).apply()
  }

  private companion object {
    const val PREFERENCES_NAME = "notification_permission"
    const val DECISION_KEY = "decision"
  }
}
