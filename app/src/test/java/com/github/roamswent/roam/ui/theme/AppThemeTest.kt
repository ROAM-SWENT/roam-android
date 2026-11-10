package com.github.roamswent.roam.ui.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.roamswent.roam.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppThemeTest {

  @Test
  fun themeSampleApp_disablesActionBarAndEnablesWindowNoTitle_regressionDetectsActionBarReactivation() {
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
}
