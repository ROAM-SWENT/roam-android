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
    for (bracket in bracketGeometry(size.width, size.height, arm, inset)) {
      val path = Path()
      bracket.points.forEachIndexed { index, (x, y) ->
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
      }
      drawPath(
          path = path,
          color = RETICLE_COLOR,
          style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
      )
    }
  }
}

internal data class BracketLine(val points: List<Pair<Float, Float>>)

internal fun bracketGeometry(
    width: Float,
    height: Float,
    arm: Float,
    inset: Float,
): List<BracketLine> {
  val left = inset
  val top = inset
  val right = width - inset
  val bottom = height - inset
  return listOf(
      BracketLine(listOf(left to top + arm, left to top, left + arm to top)),
      BracketLine(listOf(right to top + arm, right to top, right - arm to top)),
      BracketLine(listOf(right to bottom - arm, right to bottom, right - arm to bottom)),
      BracketLine(listOf(left to bottom - arm, left to bottom, left + arm to bottom)),
  )
}
