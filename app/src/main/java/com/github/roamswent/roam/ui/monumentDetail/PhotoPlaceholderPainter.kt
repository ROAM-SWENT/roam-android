package com.github.roamswent.roam.ui.monumentDetail

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp

/**
 * Fills the whole area with [background] and draws [icon] tinted with [iconTint] at its centre, at
 * a fixed [iconSize]. It has no intrinsic size, so a cropping `Image` shows it as is instead of
 * scaling the icon up to cover the area.
 */
class PhotoPlaceholderPainter(
    private val icon: Painter,
    private val background: Color,
    private val iconTint: Color,
    private val iconSize: Dp,
) : Painter() {
  override val intrinsicSize: Size = Size.Unspecified

  override fun DrawScope.onDraw() {
    drawRect(background)
    val iconPx = iconSize.toPx()
    translate(left = (size.width - iconPx) / 2, top = (size.height - iconPx) / 2) {
      with(icon) { draw(size = Size(iconPx, iconPx), colorFilter = ColorFilter.tint(iconTint)) }
    }
  }
}
