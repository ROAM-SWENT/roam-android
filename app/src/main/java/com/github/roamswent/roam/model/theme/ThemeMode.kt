/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.model.theme

enum class ThemeMode {
  AUTO,
  LIGHT,
  DARK;

  companion object {
    fun fromStorageValue(value: String?): ThemeMode =
        entries.firstOrNull { it.name == value } ?: AUTO
  }
}
