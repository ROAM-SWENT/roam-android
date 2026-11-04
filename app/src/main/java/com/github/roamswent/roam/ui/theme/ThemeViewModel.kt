/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.roamswent.roam.model.theme.ThemeMode
import com.github.roamswent.roam.model.theme.ThemePreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ThemeViewModel(private val repository: ThemePreferencesRepository) : ViewModel() {
  private val _themeMode = MutableStateFlow<ThemeMode?>(null)
  val themeMode: StateFlow<ThemeMode?> = _themeMode.asStateFlow()

  init {
    viewModelScope.launch { repository.themeMode.collect { _themeMode.value = it } }
  }

  fun setThemeMode(themeMode: ThemeMode) {
    viewModelScope.launch { repository.setThemeMode(themeMode) }
  }
}
