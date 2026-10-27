/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.model.interests

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/** The user's saved interests and whether they have been asked about them. */
data class UserInterestsState(val hasBeenAsked: Boolean, val interests: List<String>)

/** Persists the user's interests and whether they have been asked to choose them. */
interface UserInterestsRepository {
  fun getUserInterests(): UserInterestsState

  fun saveInterests(interests: List<String>)

  fun skipInterests()
}

/** Stores user interests locally. */
class SharedPreferencesUserInterestsRepository(context: Context) : UserInterestsRepository {

  private val preferences: SharedPreferences =
      context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  override fun getUserInterests(): UserInterestsState {
    val serializedInterests = preferences.getString(INTERESTS_KEY, null)
    val interests =
        serializedInterests?.let { stored ->
          val jsonArray = JSONArray(stored)
          List(jsonArray.length()) { index -> jsonArray.getString(index) }
        } ?: emptyList()

    return UserInterestsState(
        hasBeenAsked = preferences.getBoolean(HAS_BEEN_ASKED_KEY, false),
        interests = interests,
    )
  }

  override fun saveInterests(interests: List<String>) {
    preferences
        .edit()
        .putBoolean(HAS_BEEN_ASKED_KEY, true)
        .putString(INTERESTS_KEY, JSONArray(interests).toString())
        .apply()
  }

  override fun skipInterests() {
    preferences
        .edit()
        .putBoolean(HAS_BEEN_ASKED_KEY, true)
        .putString(INTERESTS_KEY, JSONArray().toString())
        .apply()
  }

  private companion object {
    const val PREFERENCES_NAME = "user_interests"
    const val HAS_BEEN_ASKED_KEY = "has_been_asked"
    const val INTERESTS_KEY = "interests"
  }
}
