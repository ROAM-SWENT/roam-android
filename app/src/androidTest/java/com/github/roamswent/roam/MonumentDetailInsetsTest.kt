// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.monumentDetail.MonumentDetailScreen
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonumentDetailInsetsTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun detailControlsStayOutsideSystemBarsInBothThemes() {
    composeTestRule.activity.runOnUiThread {
      WindowCompat.setDecorFitsSystemWindows(composeTestRule.activity.window, false)
    }
    var darkTheme by mutableStateOf(false)
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = darkTheme, dynamicColor = false) {
        MonumentDetailScreen(
            image = ColorPainter(Color.Gray),
            guide =
                MonumentGuide(
                    monumentName = "Louvre",
                    confidence = 0.9,
                    history = "History. ".repeat(120),
                    facts = emptyList(),
                    culture = "Culture",
                    language = "en",
                ),
            onBackClick = {},
        )
      }
    }

    for (dark in listOf(false, true)) {
      composeTestRule.runOnIdle { darkTheme = dark }
      composeTestRule.onNodeWithTag(C.Tag.monument_detail_culture).performScrollTo()
      composeTestRule.onNodeWithTag(C.Tag.monument_detail_culture).assertIsDisplayed()
      val screen =
          composeTestRule
              .onNodeWithTag(C.Tag.monument_detail_screen_container)
              .getUnclippedBoundsInRoot()
      val back =
          composeTestRule
              .onNodeWithTag(C.Tag.monument_detail_back_button)
              .getUnclippedBoundsInRoot()
      val culture =
          composeTestRule.onNodeWithTag(C.Tag.monument_detail_culture).getUnclippedBoundsInRoot()
      val insets =
          checkNotNull(ViewCompat.getRootWindowInsets(composeTestRule.activity.window.decorView))
              .getInsets(WindowInsetsCompat.Type.systemBars())
      val density = composeTestRule.density
      with(density) {
        assertTrue(back.top >= screen.top + insets.top.toDp())
        assertTrue(culture.bottom <= screen.bottom - insets.bottom.toDp())
      }
    }
  }
}
