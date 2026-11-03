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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.DockBarBackground
import com.github.roamswent.roam.ui.theme.DockOutline
import com.github.roamswent.roam.ui.theme.DockScanIcon
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
                .background(DockBarBackground)
                .navigationBarsPadding()
                .height(barContentHeight),
    ) {
      Row(
          modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
          verticalAlignment = Alignment.Bottom,
      ) {
        DockTabSlot(
            destination = DockDestination.HOME,
            selected = selected,
            onDestinationSelected = onDestinationSelected,
        )
        DockTabSlot(
            destination = DockDestination.EXPLORE,
            selected = selected,
            onDestinationSelected = onDestinationSelected,
        )
        Box(
            modifier = Modifier.weight(1.75f).fillMaxHeight().padding(top = 12.dp, bottom = 10.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
          AutoResizedTabLabel(
              text =
                  if (cameraAvailable) stringResource(R.string.home_tab_scan)
                  else stringResource(R.string.home_scan_unavailable),
              color = if (cameraAvailable) ScanTeal else MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = FontWeight.SemiBold,
              modifier =
                  Modifier.wrapContentWidth(unbounded = true).padding(top = 34.dp).semantics {
                    testTag = C.Tag.dock_scan_label
                  },
          )
        }
        DockTabSlot(
            destination = DockDestination.COLLECTION,
            selected = selected,
            onDestinationSelected = onDestinationSelected,
        )
        DockTabSlot(
            destination = DockDestination.SETTINGS,
            selected = selected,
            onDestinationSelected = onDestinationSelected,
        )
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
                        if (cameraAvailable) DockBarBackground
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
            tint = DockScanIcon,
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
private fun RowScope.DockTabSlot(
    destination: DockDestination,
    selected: DockDestination,
    onDestinationSelected: (DockDestination) -> Unit,
) {
  Box(
      modifier = Modifier.weight(1f).fillMaxHeight().padding(top = 12.dp, bottom = 10.dp),
      contentAlignment = Alignment.TopCenter,
  ) {
    DockTabItem(
        destination = destination,
        selected = selected == destination,
        onDestinationSelected = onDestinationSelected,
    )
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
              .clickable(enabled = destination.route.isNotEmpty() && !selected) {
                onDestinationSelected(destination)
              }
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
    AutoResizedTabLabel(
        text = stringResource(destination.labelRes),
        color = color,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
    )
  }
}

@Composable
private fun AutoResizedTabLabel(
    text: String,
    color: Color,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
    initialFontSize: TextUnit = 11.sp,
    minFontSize: TextUnit = 8.5.sp,
) {
  var readyToDraw by remember(text) { mutableStateOf(false) }
  var scaledFontSize by remember(text) { mutableStateOf(initialFontSize) }

  Text(
      text = text,
      color = color,
      fontSize = scaledFontSize,
      fontWeight = fontWeight,
      textAlign = TextAlign.Center,
      maxLines = 1,
      softWrap = false,
      onTextLayout = { textLayoutResult ->
        if (textLayoutResult.didOverflowWidth && scaledFontSize > minFontSize) {
          scaledFontSize = (scaledFontSize.value - 0.5f).sp
          readyToDraw = false
        } else {
          readyToDraw = true
        }
      },
      modifier = modifier.then(Modifier.drawWithContent { if (readyToDraw) drawContent() }),
  )
}
