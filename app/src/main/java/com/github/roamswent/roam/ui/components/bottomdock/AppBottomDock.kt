/*
 * Co-authored-by: Mistral Vibe <vibe@mistral.ai>
 * Co-authored-by: Google Antigravity <noreply@google.com>
 */

package com.github.roamswent.roam.ui.components.bottomdock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.DockLabelFontSize
import com.github.roamswent.roam.ui.theme.DockLabelMinFontSize
import com.github.roamswent.roam.ui.theme.DockScanIcon
import com.github.roamswent.roam.ui.theme.ScanTeal
import kotlin.math.roundToInt

@Composable
fun AppBottomDock(
    selected: DockDestination?,
    onDestinationSelected: (DockDestination) -> Unit,
    onScanClick: () -> Unit,
    cameraAvailable: Boolean = true,
    modifier: Modifier = Modifier,
) {
  val barContentHeight = 80.dp
  val haloSize = 72.dp
  val fabSize = 56.dp
  val protrusion = haloSize / 2

  val homeLabel = stringResource(DockDestination.HOME.labelRes)
  val exploreLabel = stringResource(DockDestination.EXPLORE.labelRes)
  val collectionLabel = stringResource(DockDestination.COLLECTION.labelRes)
  val friendsLabel = stringResource(DockDestination.FRIENDS.labelRes)
  val scanLabel =
      if (cameraAvailable) stringResource(R.string.home_tab_scan)
      else stringResource(R.string.home_scan_unavailable_short)

  val textMeasurer = rememberTextMeasurer()
  val density = LocalDensity.current
  val measurementDensity = remember(density) { Density(density = density.density, fontScale = 1f) }

  BoxWithConstraints(
      modifier =
          modifier.fillMaxWidth().background(Color.Transparent).semantics {
            testTag = C.Tag.dock_container
          }
  ) {
    val labelFontSize =
        remember(
            maxWidth,
            cameraAvailable,
            homeLabel,
            exploreLabel,
            collectionLabel,
            friendsLabel,
            scanLabel,
            measurementDensity,
        ) {
          computeDockLabelFontSize(
              maxWidth = maxWidth,
              tabLabels = listOf(homeLabel, exploreLabel, collectionLabel, friendsLabel),
              scanLabel = scanLabel,
              textMeasurer = textMeasurer,
              density = measurementDensity,
          )
        }

    Box(
        modifier =
            Modifier.fillMaxWidth()
                .padding(top = protrusion)
                .background(MaterialTheme.colorScheme.surface)
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
            labelFontSize = labelFontSize,
        )
        DockTabSlot(
            destination = DockDestination.EXPLORE,
            selected = selected,
            onDestinationSelected = onDestinationSelected,
            labelFontSize = labelFontSize,
        )
        Box(
            modifier = Modifier.weight(1.25f).fillMaxHeight().padding(top = 12.dp, bottom = 10.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
          val unavailableDescription = stringResource(R.string.home_scan_unavailable)
          AutoResizedTabLabel(
              text = scanLabel,
              color = if (cameraAvailable) ScanTeal else MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = FontWeight.SemiBold,
              fontSize = labelFontSize,
              modifier =
                  (if (cameraAvailable) Modifier.wrapContentWidth(unbounded = true) else Modifier)
                      .padding(top = 34.dp)
                      .semantics {
                        testTag = C.Tag.dock_scan_label
                        if (!cameraAvailable) {
                          contentDescription = unavailableDescription
                        }
                      },
          )
        }
        DockTabSlot(
            destination = DockDestination.COLLECTION,
            selected = selected,
            onDestinationSelected = onDestinationSelected,
            labelFontSize = labelFontSize,
        )
        DockTabSlot(
            destination = DockDestination.FRIENDS,
            selected = selected,
            onDestinationSelected = onDestinationSelected,
            labelFontSize = labelFontSize,
        )
      }
    }

    Box(
        modifier =
            Modifier.align(Alignment.TopCenter)
                .size(haloSize)
                .background(
                    color =
                        if (cameraAvailable) MaterialTheme.colorScheme.surface
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
                if (cameraAvailable) stringResource(R.string.home_scan_description)
                else stringResource(R.string.home_scan_unavailable),
            tint = DockScanIcon,
            modifier = Modifier.size(28.dp),
        )
      }
    }

    val outlineColor = MaterialTheme.colorScheme.outlineVariant
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
      drawPath(
          path = outline,
          color = outlineColor,
          style = Stroke(width = 1.dp.toPx()),
      )
    }
  }
}

private fun computeDockLabelFontSize(
    maxWidth: Dp,
    tabLabels: List<String>,
    scanLabel: String,
    textMeasurer: TextMeasurer,
    density: Density,
    minFontSize: TextUnit = DockLabelMinFontSize,
): TextUnit {
  val contentWidth = maxWidth - 16.dp
  val tabSlotWidth = contentWidth / 5.25f
  val scanSlotWidth = tabSlotWidth * 1.25f
  val tabBudget = tabSlotWidth - 8.dp
  val scanBudget = scanSlotWidth

  if (tabBudget <= 0.dp || scanBudget <= 0.dp) {
    return minFontSize
  }

  val tabBudgetPx = with(density) { tabBudget.toPx() }
  val scanBudgetPx = with(density) { scanBudget.toPx() }

  val stepCount = ((DockLabelFontSize.value - minFontSize.value) / 0.5f).roundToInt()
  val candidateSizes = (0..stepCount).map { i -> (DockLabelFontSize.value - i * 0.5f).sp }

  return candidateSizes.firstOrNull { size ->
    val tabsFit = tabLabels.all { label ->
      val result =
          textMeasurer.measure(
              text = label,
              style = TextStyle(fontSize = size, fontWeight = FontWeight.Bold),
              softWrap = false,
              maxLines = 1,
              density = density,
          )
      result.size.width <= tabBudgetPx
    }
    val scanFits =
        textMeasurer
            .measure(
                text = scanLabel,
                style = TextStyle(fontSize = size, fontWeight = FontWeight.Bold),
                softWrap = false,
                maxLines = 1,
                density = density,
            )
            .size
            .width <= scanBudgetPx

    tabsFit && scanFits
  } ?: minFontSize
}

@Composable
private fun RowScope.DockTabSlot(
    destination: DockDestination,
    selected: DockDestination?,
    onDestinationSelected: (DockDestination) -> Unit,
    labelFontSize: TextUnit,
) {
  Box(
      modifier = Modifier.weight(1f).fillMaxHeight().padding(top = 12.dp, bottom = 10.dp),
      contentAlignment = Alignment.TopCenter,
  ) {
    DockTabItem(
        destination = destination,
        selected = selected == destination,
        onDestinationSelected = onDestinationSelected,
        labelFontSize = labelFontSize,
    )
  }
}

@Composable
private fun DockTabItem(
    destination: DockDestination,
    selected: Boolean,
    onDestinationSelected: (DockDestination) -> Unit,
    labelFontSize: TextUnit,
) {
  val color =
      if (selected) MaterialTheme.colorScheme.onSurface
      else MaterialTheme.colorScheme.onSurfaceVariant

  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier =
          Modifier.defaultMinSize(minWidth = 68.dp, minHeight = 56.dp)
              .clip(RoundedCornerShape(12.dp))
              .clickable(enabled = !selected) { onDestinationSelected(destination) }
              .semantics { testTag = destination.tag }
              .padding(horizontal = 4.dp, vertical = 6.dp),
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
        fontSize = labelFontSize,
    )
  }
}

@Composable
private fun AutoResizedTabLabel(
    text: String,
    color: Color,
    fontWeight: FontWeight,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current

  CompositionLocalProvider(
      LocalDensity provides Density(density = density.density, fontScale = 1f)
  ) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
  }
}
