// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
/*
 * Co-authored-by: Mistral Vibe <vibe@mistral.ai>
 */

package com.github.roamswent.roam.ui.components.bottomdock

import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.navigation.Routes
import com.github.roamswent.roam.ui.theme.IconPathColor

private fun dockIcon(name: String, paths: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        .apply(paths)
        .build()

private fun ImageVector.Builder.dockPath(pathBuilder: PathBuilder.() -> Unit) {
  path(
      fill = null,
      stroke = SolidColor(IconPathColor),
      strokeLineWidth = 2f,
      strokeLineCap = StrokeCap.Round,
      strokeLineJoin = StrokeJoin.Round,
      pathBuilder = pathBuilder,
  )
}

private fun PathBuilder.dockOuterCircle() {
  moveTo(21f, 12f)
  curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
  curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
  curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
  curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
  close()
}

private val HomeDockIcon =
    dockIcon("HomeDockIcon") {
      dockPath {
        moveTo(12f, 3f)
        lineTo(21f, 10.5f)
        lineTo(21f, 20f)
        lineTo(3f, 20f)
        lineTo(3f, 10.5f)
        close()
      }
    }

private val FriendsDockIcon =
    dockIcon("FriendsDockIcon") {
      dockPath {
        moveTo(12f, 4f)
        curveTo(13.66f, 4f, 15f, 5.34f, 15f, 7f)
        curveTo(15f, 8.66f, 13.66f, 10f, 12f, 10f)
        curveTo(10.34f, 10f, 9f, 8.66f, 9f, 7f)
        curveTo(9f, 5.34f, 10.34f, 4f, 12f, 4f)
        close()
      }
      dockPath {
        moveTo(5.5f, 7.5f)
        curveTo(6.88f, 7.5f, 8f, 8.62f, 8f, 10f)
        curveTo(8f, 11.38f, 6.88f, 12.5f, 5.5f, 12.5f)
        curveTo(4.12f, 12.5f, 3f, 11.38f, 3f, 10f)
        curveTo(3f, 8.62f, 4.12f, 7.5f, 5.5f, 7.5f)
        close()
      }
      dockPath {
        moveTo(18.5f, 7.5f)
        curveTo(19.88f, 7.5f, 21f, 8.62f, 21f, 10f)
        curveTo(21f, 11.38f, 19.88f, 12.5f, 18.5f, 12.5f)
        curveTo(17.12f, 12.5f, 16f, 11.38f, 16f, 10f)
        curveTo(16f, 8.62f, 17.12f, 7.5f, 18.5f, 7.5f)
        close()
      }
      dockPath {
        moveTo(6f, 20f)
        curveTo(6f, 16.69f, 8.69f, 14f, 12f, 14f)
        curveTo(15.31f, 14f, 18f, 16.69f, 18f, 20f)
      }
      dockPath {
        moveTo(7f, 15f)
        curveTo(4.24f, 15f, 2f, 17.24f, 2f, 20f)
      }
      dockPath {
        moveTo(17f, 15f)
        curveTo(19.76f, 15f, 22f, 17.24f, 22f, 20f)
      }
    }

private val ExplorerIcon =
    dockIcon("ExplorerIcon") {
      dockPath { dockOuterCircle() }
      path(
          fill = SolidColor(IconPathColor),
          pathFillType = PathFillType.NonZero,
      ) {
        moveTo(10.4f, 9.2f)
        lineTo(15.2f, 9.2f)
        lineTo(13.6f, 14.8f)
        lineTo(8.8f, 14.8f)
        close()
      }
    }

private val HistoriqueIcon =
    dockIcon("HistoriqueIcon") {
      dockPath { dockOuterCircle() }
      dockPath {
        moveTo(12f, 7.5f)
        lineTo(12f, 12f)
        lineTo(14.8f, 13.8f)
      }
    }

enum class DockDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val tag: String,
) {
  HOME(Routes.Home, R.string.home_tab_home, HomeDockIcon, C.Tag.dock_tab_home),
  EXPLORE(
      Routes.Explore,
      R.string.home_tab_explore,
      ExplorerIcon,
      C.Tag.dock_tab_explore,
  ),
  COLLECTION(
      Routes.Collection,
      R.string.home_tab_collection,
      HistoriqueIcon,
      C.Tag.dock_tab_collection,
  ),
  FRIENDS(Routes.Friends, R.string.home_tab_friends, FriendsDockIcon, C.Tag.dock_tab_friends),
}
