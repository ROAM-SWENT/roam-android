// Co-authored-by: Gemini 4 Hydrogen <noreply@google.com>

package com.github.roamswent.roam.ui.theme

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.github.roamswent.roam.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AppThemeTest {

  @Test
  fun themeSampleApp_disablesActionBarAndEnablesWindowNoTitle_regressionDetectsActionBarReactivation() {
    assertThemeDisablesActionBar()
  }

  @Test
  @Config(qualifiers = "night")
  fun themeSampleApp_underNightQualifier_disablesActionBar_regressionReactivatingActionBarInNightThemeVariant() {
    assertThemeDisablesActionBar()
  }

  private fun assertThemeDisablesActionBar() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val theme = context.theme
    theme.applyStyle(R.style.Theme_SampleApp, true)

    val attrs =
        intArrayOf(
            android.R.attr.windowNoTitle,
            android.R.attr.windowActionBar,
            androidx.appcompat.R.attr.windowActionBar,
            androidx.appcompat.R.attr.windowNoTitle,
        )
    val typedArray = theme.obtainStyledAttributes(attrs)
    try {
      assertTrue(typedArray.getBoolean(0, false))
      assertFalse(typedArray.getBoolean(1, true))
      assertFalse(typedArray.getBoolean(2, true))
      assertTrue(typedArray.getBoolean(3, false))
    } finally {
      typedArray.recycle()
    }
  }

  @Test
  fun themeSampleApp_resolvesLightWindowBackground_regressionDetectsMissingWindowBackground() {
    assertWindowBackgroundColor(Color.parseColor("#F2F4F3"))
  }

  @Test
  @Config(qualifiers = "night")
  fun themeSampleApp_underNightQualifier_resolvesDarkWindowBackground_regressionDetectsMissingNightWindowBackground() {
    assertWindowBackgroundColor(Color.parseColor("#1B1B1E"))
  }

  private fun assertWindowBackgroundColor(expectedColor: Int) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val theme = context.theme
    theme.applyStyle(R.style.Theme_SampleApp, true)

    val typedArray = theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
    try {
      assertEquals(expectedColor, typedArray.getColor(0, 0))
    } finally {
      typedArray.recycle()
    }
  }
}
