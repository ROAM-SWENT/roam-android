/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.model.permission

import android.content.Context
import android.content.SharedPreferences

/** The outcome of the last location-permission request made by the app. */
enum class LocationPermissionDecision {
  NOT_ASKED,
  GRANTED,
  DENIED,
}

/** Persists the user's location-permission request decision. */
interface LocationPermissionDecisionRepository {
  fun getDecision(): LocationPermissionDecision

  fun saveDecision(decision: LocationPermissionDecision)
}

/** Stores the permission decision locally, independently of Android's current permission state. */
class SharedPreferencesLocationPermissionDecisionRepository(context: Context) :
    LocationPermissionDecisionRepository {

  private val preferences: SharedPreferences =
      context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  override fun getDecision(): LocationPermissionDecision {
    val value =
        preferences.getString(DECISION_KEY, null) ?: return LocationPermissionDecision.NOT_ASKED
    return LocationPermissionDecision.entries.firstOrNull { it.name == value }
        ?: LocationPermissionDecision.NOT_ASKED
  }

  override fun saveDecision(decision: LocationPermissionDecision) {
    preferences.edit().putString(DECISION_KEY, decision.name).apply()
  }

  private companion object {
    const val PREFERENCES_NAME = "location_permission"
    const val DECISION_KEY = "decision"
  }
}
