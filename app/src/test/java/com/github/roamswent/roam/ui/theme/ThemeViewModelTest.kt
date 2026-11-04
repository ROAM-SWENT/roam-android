/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.theme

import androidx.lifecycle.ViewModelStore
import com.github.roamswent.roam.model.theme.ThemeMode
import com.github.roamswent.roam.model.theme.ThemePreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun themeMode_waitsForStoredPreferenceBeforePublishingValue() =
      runTest(dispatcher) {
        val repository = FakeThemePreferencesRepository(ThemeMode.DARK)
        val viewModel = ThemeViewModel(repository)
        val store = ViewModelStore().apply { put("theme", viewModel) }

        try {
          assertNull(viewModel.themeMode.value)

          runCurrent()

          assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
        } finally {
          store.clear()
        }
      }

  @Test
  fun setThemeMode_persistsSelectionAndUpdatesState() =
      runTest(dispatcher) {
        val repository = FakeThemePreferencesRepository(ThemeMode.AUTO)
        val viewModel = ThemeViewModel(repository)
        val store = ViewModelStore().apply { put("theme", viewModel) }

        try {
          runCurrent()
          viewModel.setThemeMode(ThemeMode.LIGHT)
          runCurrent()

          assertEquals(ThemeMode.LIGHT, repository.storedMode.value)
          assertEquals(ThemeMode.LIGHT, viewModel.themeMode.value)
        } finally {
          store.clear()
        }
      }

  private class FakeThemePreferencesRepository(initialMode: ThemeMode) :
      ThemePreferencesRepository {
    val storedMode = MutableStateFlow(initialMode)
    override val themeMode: Flow<ThemeMode> = storedMode

    override suspend fun setThemeMode(themeMode: ThemeMode) {
      storedMode.value = themeMode
    }
  }
}
