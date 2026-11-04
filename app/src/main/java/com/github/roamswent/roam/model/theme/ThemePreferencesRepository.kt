/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.model.theme

import kotlinx.coroutines.flow.Flow

interface ThemePreferencesRepository {
  val themeMode: Flow<ThemeMode>

  suspend fun setThemeMode(themeMode: ThemeMode)
}
