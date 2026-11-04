/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.github.roamswent.roam.model.theme.ThemePreferencesRepository

class ThemeViewModelFactory(private val repository: ThemePreferencesRepository) :
    ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    require(modelClass.isAssignableFrom(ThemeViewModel::class.java))
    return ThemeViewModel(repository) as T
  }
}
