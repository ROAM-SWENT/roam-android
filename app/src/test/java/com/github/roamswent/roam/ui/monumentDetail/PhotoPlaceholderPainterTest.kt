package com.github.roamswent.roam.ui.monumentDetail

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoPlaceholderPainterTest {
  private val background = Color.White
  private val painter =
      PhotoPlaceholderPainter(
          icon = ColorPainter(Color.Black),
          background = background,
          iconTint = Color.Red,
          iconSize = 40.dp,
      )

  @Test
  fun hasNoIntrinsicSizeSoItIsNotScaledUpToCoverTheArea() {
    assertEquals(Size.Unspecified, painter.intrinsicSize)
  }

  @Test
  fun drawsAFixedSizeIconAtTheCentreOnTheBackground() {
    // At density 1, the 40dp icon covers pixels 30..69 of the 100px square.
    val pixels = draw(painter, side = 100)

    assertEquals(background, pixels[0, 0])
    assertEquals(background, pixels[99, 99])
    assertEquals(background, pixels[29, 50])
    assertEquals(background, pixels[70, 50])
    assertNotEquals(background, pixels[50, 50])
    assertNotEquals(background, pixels[30, 30])
    assertNotEquals(background, pixels[69, 69])
  }

  @Test
  fun keepsTheIconCentredInANonSquareArea() {
    val pixels = draw(painter, side = 100, height = 200)

    assertEquals(background, pixels[50, 20])
    assertNotEquals(background, pixels[50, 100])
    assertEquals(background, pixels[50, 180])
  }

  private fun draw(painter: PhotoPlaceholderPainter, side: Int, height: Int = side) =
      ImageBitmap(side, height)
          .also { bitmap ->
            val size = Size(side.toFloat(), height.toFloat())
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) {
              with(painter) { draw(size) }
            }
          }
          .toPixelMap()
}
