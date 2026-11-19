// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.explore

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import com.github.roamswent.roam.ui.theme.offlineMapBorder
import com.github.roamswent.roam.ui.theme.offlineMapLand
import com.github.roamswent.roam.ui.theme.offlineMapWater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExploreOfflineMapColorsTest {
  @get:Rule val composeTestRule = createComposeRule()

  private fun colorsIn(darkTheme: Boolean): Pair<OfflineMapColors, OfflineMapColors> {
    lateinit var read: OfflineMapColors
    lateinit var expected: OfflineMapColors
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = darkTheme, dynamicColor = false) {
        read = offlineMapColors()
        expected =
            OfflineMapColors(
                sea = MaterialTheme.offlineMapWater,
                land = MaterialTheme.offlineMapLand,
                border = MaterialTheme.offlineMapBorder,
            )
      }
    }
    composeTestRule.waitForIdle()
    return read to expected
  }

  @Test
  fun lightThemeColorsComeFromTheOfflineMapTokens() {
    val (read, expected) = colorsIn(darkTheme = false)

    assertEquals(expected, read)
    assertNotEquals(read.sea, read.land)
  }

  @Test
  fun darkThemeColorsComeFromTheOfflineMapTokens() {
    val (read, expected) = colorsIn(darkTheme = true)

    assertEquals(expected, read)
    assertNotEquals(read.sea, read.land)
  }
}
