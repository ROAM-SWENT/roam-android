/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.model.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themePreferencesDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "theme_preferences")

class DataStoreThemePreferencesRepository
internal constructor(private val dataStore: DataStore<Preferences>) : ThemePreferencesRepository {
  constructor(context: Context) : this(context.applicationContext.themePreferencesDataStore)

  override val themeMode: Flow<ThemeMode> =
      dataStore.data.map { preferences -> ThemeMode.fromStorageValue(preferences[THEME_MODE_KEY]) }

  override suspend fun setThemeMode(themeMode: ThemeMode) {
    dataStore.edit { preferences -> preferences[THEME_MODE_KEY] = themeMode.name }
  }

  private companion object {
    val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
  }
}
