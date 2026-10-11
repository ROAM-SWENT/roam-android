/*
 * Co-authored-by: Mistral Vibe <noreply@mistral.ai>
 */

package com.github.roamswent.roam.ui.scan.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.resources.C

private val RETICLE_STROKE_WIDTH = 3.dp
private val RETICLE_ARM_LENGTH = 32.dp
private val RETICLE_COLOR = Color.White

/**
 * Four white corner brackets framing the monument capture area. Purely decorative: no clicks, no
 * behavior. The brackets extend inward from each corner of the bounds this composable receives.
 */
@Composable
fun FramingReticle(modifier: Modifier = Modifier) {
  Canvas(modifier = modifier.semantics { testTag = C.Tag.scan_framing_reticle }) {
    val strokeWidth = RETICLE_STROKE_WIDTH.toPx()
    val arm = RETICLE_ARM_LENGTH.toPx()
    val inset = strokeWidth / 2f
    val path = Path()
    for (corner in 0 until 4) {
      val left = corner == 0 || corner == 3
      val top = corner == 0 || corner == 1
      val xEdge = if (left) inset else size.width - inset
      val yEdge = if (top) inset else size.height - inset
      val xInner = if (left) xEdge + arm else xEdge - arm
      val yInner = if (top) yEdge + arm else yEdge - arm
      path.apply {
        moveTo(xEdge, yInner)
        lineTo(xEdge, yEdge)
        lineTo(xInner, yEdge)
      }
    }
    drawPath(
        path = path,
        color = RETICLE_COLOR,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
  }
}
