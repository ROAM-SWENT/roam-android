package com.github.roamswent.roam.ui.scan.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class FramingReticleTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun reticleDrawsWhiteBracketsAtCornersNotCenter() {
    composeTestRule.setContent {
      SampleAppTheme {
        Box(Modifier.size(300.dp).background(ComposeColor.Black)) {
          FramingReticle(Modifier.fillMaxSize())
        }
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.scan_framing_reticle).assertIsDisplayed()

    val bitmap =
        composeTestRule.onNodeWithTag(C.Tag.scan_framing_reticle).captureToImage().asAndroidBitmap()
    val pxPerDp = bitmap.width / 300f
    val window = (40f * pxPerDp).toInt()
    val halfWindow = window / 2
    val centerWindow = (60f * pxPerDp).toInt()
    val centerX = bitmap.width / 2
    val centerY = bitmap.height / 2

    assertTrue("top-left bracket missing", hasWhitePixel(bitmap, 0, 0, window, window))
    assertTrue(
        "top-right bracket missing",
        hasWhitePixel(bitmap, bitmap.width - window, 0, bitmap.width, window),
    )
    assertTrue(
        "bottom-right bracket missing",
        hasWhitePixel(
            bitmap,
            bitmap.width - window,
            bitmap.height - window,
            bitmap.width,
            bitmap.height,
        ),
    )
    assertTrue(
        "bottom-left bracket missing",
        hasWhitePixel(bitmap, 0, bitmap.height - window, window, bitmap.height),
    )
    assertFalse(
        "center should not contain any bracket stroke",
        hasWhitePixel(
            bitmap,
            centerX - centerWindow / 2,
            centerY - centerWindow / 2,
            centerX + centerWindow / 2,
            centerY + centerWindow / 2,
        ),
    )
    assertFalse(
        "top edge midpoint should not contain any bracket stroke",
        hasWhitePixel(bitmap, centerX - halfWindow, 0, centerX + halfWindow, window),
    )
    assertFalse(
        "bottom edge midpoint should not contain any bracket stroke",
        hasWhitePixel(
            bitmap,
            centerX - halfWindow,
            bitmap.height - window,
            centerX + halfWindow,
            bitmap.height,
        ),
    )
    assertFalse(
        "left edge midpoint should not contain any bracket stroke",
        hasWhitePixel(bitmap, 0, centerY - halfWindow, window, centerY + halfWindow),
    )
    assertFalse(
        "right edge midpoint should not contain any bracket stroke",
        hasWhitePixel(
            bitmap,
            bitmap.width - window,
            centerY - halfWindow,
            bitmap.width,
            centerY + halfWindow,
        ),
    )
  }

  private fun hasWhitePixel(
      bitmap: Bitmap,
      x0: Int,
      y0: Int,
      x1: Int,
      y1: Int,
  ): Boolean {
    for (y in y0 until y1) {
      for (x in x0 until x1) {
        val pixel = bitmap.getPixel(x, y)
        if (
            Color.alpha(pixel) > 0 &&
                Color.red(pixel) > 200 &&
                Color.green(pixel) > 200 &&
                Color.blue(pixel) > 200
        ) {
          return true
        }
      }
    }
    return false
  }
}
