// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.explore

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
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

  @Test
  fun createsMarkerWithPlaceholderWhenPhotoIsMissing() {
    val bitmap =
        createMonumentMarkerBitmap(
            thumbnail = null,
            placeholderPainter = placeholderPainter,
            density = density,
            pinColor = 0xff112233.toInt(),
            borderColor = 0xff445566.toInt(),
            placeholderColor = 0xff778899.toInt(),
        )

    assertEquals(128, bitmap.width)
    assertEquals(140, bitmap.height)
    assertEquals(0xff778899.toInt(), bitmap.getPixel(64, 52))
  }

  @Test
  fun createsMarkerWithCroppedThumbnailWhenPhotoIsAvailable() {
    val thumbnail = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
    thumbnail.eraseColor(0xffabcdef.toInt())

    val bitmap =
        createMonumentMarkerBitmap(
            thumbnail = thumbnail,
            placeholderPainter = placeholderPainter,
            density = density,
            pinColor = 0xff112233.toInt(),
            borderColor = 0xff445566.toInt(),
            placeholderColor = 0xff778899.toInt(),
        )

    assertEquals(128, bitmap.width)
    assertEquals(140, bitmap.height)
    assertEquals(0xffabcdef.toInt(), bitmap.getPixel(64, 52))
  }
}
