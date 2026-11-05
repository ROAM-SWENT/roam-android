// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.theme

import android.view.View
import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.view.WindowCompat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemeTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun statusBarUsesDarkIconsForLightThemeAndLightIconsForDarkTheme() {
    var darkTheme by mutableStateOf(false)
    var window: Window? = null
    var view: View? = null

    composeTestRule.setContent {
      val activity = requireNotNull(LocalActivity.current)
      val currentView = LocalView.current
      SampleAppTheme(darkTheme = darkTheme, dynamicColor = false) {
        SideEffect {
          window = activity.window
          view = currentView
        }
      }
    }

    composeTestRule.runOnIdle {
      val currentWindow = requireNotNull(window)
      val currentView = requireNotNull(view)
      assertTrue(
          WindowCompat.getInsetsController(currentWindow, currentView).isAppearanceLightStatusBars
      )
      darkTheme = true
    }

    composeTestRule.runOnIdle {
      assertFalse(
          WindowCompat.getInsetsController(requireNotNull(window), requireNotNull(view))
              .isAppearanceLightStatusBars
      )
    }
  }
}
