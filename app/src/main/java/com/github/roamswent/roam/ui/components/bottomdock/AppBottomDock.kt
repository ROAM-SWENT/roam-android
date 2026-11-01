/*
 * Co-authored-by: Mistral Vibe <vibe@mistral.ai>
 */

package com.github.roamswent.roam.ui.components.bottomdock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.DockOutline
import com.github.roamswent.roam.ui.theme.HomeActiveTab
import com.github.roamswent.roam.ui.theme.HomeInactiveTab
import com.github.roamswent.roam.ui.theme.ScanTeal

@Composable
fun AppBottomDock(
    selected: DockDestination,
    onDestinationSelected: (DockDestination) -> Unit,
    onScanClick: () -> Unit,
    cameraAvailable: Boolean = true,
    modifier: Modifier = Modifier,
) {
  val barContentHeight = 80.dp
  val haloSize = 72.dp
  val fabSize = 56.dp
  // How far the halo rises above the white bar's top edge.
  val protrusion = haloSize / 2

  // Outer container is fully transparent; only the bar and the halo paint white.
  Box(
      modifier =
          modifier.fillMaxWidth().background(Color.Transparent).semantics {
            testTag = C.Tag.dock_container
          }
  ) {
    // White bar. padding(top = protrusion) comes BEFORE background(White), so the
    // top 36.dp stay transparent and the pure white starts exactly at y = 36.dp.
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .padding(top = protrusion)
                .background(Color.White)
                .navigationBarsPadding()
                .height(barContentHeight),
    ) {
      Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
        Box(
            modifier = Modifier.weight(1f).padding(bottom = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
          DockTabItem(
              destination = DockDestination.HOME,
              selected = selected == DockDestination.HOME,
              onDestinationSelected = onDestinationSelected,
          )
        }
        Box(
            modifier = Modifier.weight(1f).padding(bottom = 10.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
          Text(
              text =
                  if (cameraAvailable) stringResource(R.string.home_tab_scan)
                  else stringResource(R.string.home_scan_unavailable),
              color = if (cameraAvailable) ScanTeal else MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              modifier =
                  Modifier.padding(bottom = 6.dp).semantics { testTag = C.Tag.dock_scan_label },
          )
        }
        Box(
            modifier = Modifier.weight(1f).padding(bottom = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
          DockTabItem(
              destination = DockDestination.SETTINGS,
              selected = selected == DockDestination.SETTINGS,
              onDestinationSelected = onDestinationSelected,
          )
        }
      }
    }

    // Halo: TopCenter of the outer Box, so the 72.dp circle spans y=0..72 and its
    // center sits exactly on the bar's top edge (y = 36.dp). The 56.dp FAB is
    // centered in the halo, so it spans y=8..64 and clears the label at the bottom.
    Box(
        modifier =
            Modifier.align(Alignment.TopCenter)
                .size(haloSize)
                .background(
                    color =
                        if (cameraAvailable) Color.White
                        else MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
      IconButton(
          onClick = onScanClick,
          enabled = cameraAvailable,
          modifier =
              Modifier.size(fabSize)
                  .background(
                      color =
                          if (cameraAvailable) ScanTeal
                          else MaterialTheme.colorScheme.surfaceVariant,
                      shape = CircleShape,
                  )
                  .semantics { testTag = C.Tag.dock_scan_button },
      ) {
        Icon(
            imageVector =
                if (cameraAvailable) Icons.Default.CenterFocusStrong
                else Icons.Default.NoPhotography,
            contentDescription =
                if (cameraAvailable) stringResource(R.string.home_tab_scan)
                else stringResource(R.string.home_scan_unavailable),
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
      }
    }

    // 1.dp outline drawn last: bar top edge on both sides of the halo plus the
    // halo's upper semicircle, locked to y = protrusion (the bar's top edge).
    Canvas(modifier = Modifier.matchParentSize()) {
      val barTop = protrusion.toPx()
      val haloRadius = haloSize.toPx() / 2f
      val centerX = size.width / 2f
      val outline =
          Path().apply {
            moveTo(0f, barTop)
            lineTo(centerX - haloRadius, barTop)
            arcTo(
                Rect(centerX - haloRadius, 0f, centerX + haloRadius, haloSize.toPx()),
                180f,
                180f,
                false,
            )
            lineTo(size.width, barTop)
          }
      drawPath(path = outline, color = DockOutline, style = Stroke(width = 1.dp.toPx()))
    }
  }
}

@Composable
private fun DockTabItem(
    destination: DockDestination,
    selected: Boolean,
    onDestinationSelected: (DockDestination) -> Unit,
) {
  val color = if (selected) HomeActiveTab else HomeInactiveTab

  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier =
          Modifier.defaultMinSize(minWidth = 68.dp, minHeight = 56.dp)
              .clip(RoundedCornerShape(12.dp))
              .clickable(enabled = !selected) { onDestinationSelected(destination) }
              .semantics { testTag = destination.tag }
              .padding(horizontal = 12.dp, vertical = 6.dp),
  ) {
    Icon(
        imageVector = destination.icon,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(24.dp),
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(destination.labelRes),
        color = color,
        fontSize = 11.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
    )
  }
}
