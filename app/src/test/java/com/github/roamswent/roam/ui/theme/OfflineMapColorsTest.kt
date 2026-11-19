// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OfflineMapColorsTest {
  @get:Rule val composeTestRule = createComposeRule()

  private fun colorsIn(darkTheme: Boolean): List<Color> {
    val colors = mutableListOf<Color>()
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = darkTheme, dynamicColor = false) {
        colors += MaterialTheme.offlineMapWater
        colors += MaterialTheme.offlineMapLand
        colors += MaterialTheme.offlineMapBorder
        colors += MaterialTheme.colorScheme.background
        colors += MaterialTheme.colorScheme.surfaceVariant
        colors += MaterialTheme.colorScheme.outline
      }
    }
    composeTestRule.waitForIdle()
    return colors
  }

  @Test
  fun lightThemeUsesTheLightOfflineMapColors() {
    val colors = colorsIn(darkTheme = false)

    assertEquals(OfflineMapWaterLight, colors[0])
    assertEquals(OfflineMapLandLight, colors[1])
    assertEquals(OfflineMapBorderLight, colors[2])
  }

  @Test
  fun darkThemeUsesTheColorSchemeRoles() {
    val colors = colorsIn(darkTheme = true)

    assertEquals(colors[3], colors[0])
    assertEquals(colors[4], colors[1])
    assertEquals(colors[5], colors[2])
  }
}
