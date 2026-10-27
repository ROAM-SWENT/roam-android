package com.github.roamswent.roam.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C

private val HomeTabIcon =
    ImageVector.Builder(
            name = "HomeTabIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        .apply {
          path(
              fill = null,
              stroke = SolidColor(Color.Black),
              strokeLineWidth = 2f,
              strokeLineCap = StrokeCap.Round,
              strokeLineJoin = StrokeJoin.Round,
          ) {
            moveTo(12f, 3f)
            lineTo(21f, 10.5f)
            lineTo(21f, 20f)
            lineTo(3f, 20f)
            lineTo(3f, 10.5f)
            close()
          }
        }
        .build()

private val ProfileTabIcon =
    ImageVector.Builder(
            name = "ProfileTabIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        .apply {
          path(
              fill = null,
              stroke = SolidColor(Color.Black),
              strokeLineWidth = 2f,
              strokeLineCap = StrokeCap.Round,
              strokeLineJoin = StrokeJoin.Round,
          ) {
            moveTo(12f, 4f)
            curveTo(14.21f, 4f, 16f, 5.79f, 16f, 8f)
            curveTo(16f, 10.21f, 14.21f, 12f, 12f, 12f)
            curveTo(9.79f, 12f, 8f, 10.21f, 8f, 8f)
            curveTo(8f, 5.79f, 9.79f, 4f, 12f, 4f)
            close()
            moveTo(5f, 20f)
            curveTo(5f, 16.13f, 8.13f, 13f, 12f, 13f)
            curveTo(15.87f, 13f, 19f, 16.13f, 19f, 20f)
          }
        }
        .build()

enum class HomeBottomTab(val labelRes: Int, val icon: ImageVector, val tag: String) {
  HOME(R.string.home_tab_home, HomeTabIcon, C.Tag.home_tab_home),
  SCAN(R.string.home_tab_scan, Icons.Filled.CenterFocusStrong, C.Tag.home_tab_scan),
  PROFILE(R.string.home_tab_profile, ProfileTabIcon, C.Tag.home_tab_profile),
}
