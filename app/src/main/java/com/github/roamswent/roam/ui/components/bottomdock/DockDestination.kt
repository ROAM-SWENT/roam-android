/*
 * Co-authored-by: Mistral Vibe <vibe@mistral.ai>
 */

package com.github.roamswent.roam.ui.components.bottomdock

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.navigation.Routes

private val HomeDockIcon =
    ImageVector.Builder(
            name = "HomeDockIcon",
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

private val ProfileDockIcon =
    ImageVector.Builder(
            name = "ProfileDockIcon",
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

private val ExplorerIcon =
    ImageVector.Builder(
            name = "ExplorerIcon",
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
            moveTo(21f, 12f)
            curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
            curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
            curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
            curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
            close()
          }
          path(
              fill = SolidColor(Color.Black),
              pathFillType = PathFillType.NonZero,
          ) {
            moveTo(10.5f, 9.5f)
            lineTo(14.8f, 9.2f)
            lineTo(13.5f, 14.5f)
            lineTo(9.2f, 14.8f)
            close()
          }
        }
        .build()

private val HistoriqueIcon =
    ImageVector.Builder(
            name = "HistoriqueIcon",
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
            moveTo(21f, 12f)
            curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
            curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
            curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
            curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
            close()
          }
          path(
              fill = null,
              stroke = SolidColor(Color.Black),
              strokeLineWidth = 2f,
              strokeLineCap = StrokeCap.Round,
              strokeLineJoin = StrokeJoin.Round,
          ) {
            moveTo(12f, 7.5f)
            lineTo(12f, 12f)
            lineTo(14.8f, 13.8f)
          }
        }
        .build()

enum class DockDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val tag: String,
) {
  HOME(Routes.Home, R.string.home_tab_home, HomeDockIcon, C.Tag.dock_tab_home),
  EXPLORE("", R.string.home_tab_explore, ExplorerIcon, C.Tag.dock_tab_explore),
  COLLECTION("", R.string.home_tab_collection, HistoriqueIcon, C.Tag.dock_tab_collection),
  SETTINGS(Routes.Settings, R.string.home_tab_profile, ProfileDockIcon, C.Tag.dock_tab_settings),
}
