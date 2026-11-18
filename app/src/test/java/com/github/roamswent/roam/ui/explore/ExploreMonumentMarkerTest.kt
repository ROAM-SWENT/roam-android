// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Codex <codex@openai.com>
package com.github.roamswent.roam.ui.explore

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExploreMonumentMarkerTest {
  private val density = Density(density = 2f)
  private val placeholderPainter = ColorPainter(Color.Blue)
  private val pinColor = 0xff112233.toInt()
  private val borderColor = 0xff445566.toInt()
  private val placeholderColor = 0xff778899.toInt()
  private val thumbnailColor = 0xffabcdef.toInt()

  @Test
  fun createsMarkerWithPlaceholderWhenPhotoIsMissing() {
    val bitmap = renderMarker(thumbnail = null)

    assertEquals(128, bitmap.width)
    assertEquals(140, bitmap.height)
    assertEquals(placeholderColor, bitmap.getPixel(64, 56))
    assertEquals(pinColor, bitmap.getPixel(29, 56))
    assertEquals(borderColor, bitmap.getPixel(19, 56))
  }

  @Test
  fun centerCropsWideThumbnail() {
    val pixels =
        IntArray(48 * 16) { index ->
          if (index % 48 in 16 until 32) thumbnailColor else Color.Red.toArgb()
        }
    val thumbnail = Bitmap.createBitmap(pixels, 48, 16, Bitmap.Config.ARGB_8888)
    val bitmap = renderMarker(thumbnail)

    assertEquals(thumbnailColor, bitmap.getPixel(34, 56))
    assertEquals(thumbnailColor, bitmap.getPixel(94, 56))
  }

  @Test
  fun centerCropsTallThumbnail() {
    val pixels =
        IntArray(16 * 48) { index ->
          if (index / 16 in 16 until 32) thumbnailColor else Color.Red.toArgb()
        }
    val thumbnail = Bitmap.createBitmap(pixels, 16, 48, Bitmap.Config.ARGB_8888)
    val bitmap = renderMarker(thumbnail)

    assertEquals(thumbnailColor, bitmap.getPixel(64, 26))
    assertEquals(thumbnailColor, bitmap.getPixel(64, 86))
  }

  private fun renderMarker(thumbnail: Bitmap?): Bitmap =
      createMonumentMarkerBitmap(
          thumbnail = thumbnail,
          placeholderPainter = placeholderPainter,
          density = density,
          pinColor = pinColor,
          borderColor = borderColor,
          placeholderColor = placeholderColor,
      )
}
