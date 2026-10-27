package com.github.roamswent.roam.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.HomeActiveTab
import com.github.roamswent.roam.ui.theme.HomeInactiveTab
import com.github.roamswent.roam.ui.theme.ScanTeal

@Composable
fun HomeBottomBar(
    selectedTab: HomeBottomTab = HomeBottomTab.HOME,
    onTabSelected: (HomeBottomTab) -> Unit = {},
    onScanClick: () -> Unit = {},
    cameraAvailable: Boolean = true,
) {
  val haloSize = 72.dp

  Box(modifier = Modifier.fillMaxWidth().semantics { testTag = C.Tag.home_bottom_bar }) {
    Surface(
        modifier =
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(top = haloSize / 2),
        color = Color.White,
    ) {
      Row(
          modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(80.dp),
          verticalAlignment = Alignment.Bottom,
      ) {
        Box(
            modifier = Modifier.weight(1f).padding(bottom = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
          HomeBottomTabItem(
              tab = HomeBottomTab.HOME,
              selected = selectedTab == HomeBottomTab.HOME,
              onTabSelected = onTabSelected,
          )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
          Text(
              text =
                  if (cameraAvailable) "Scan a monument" else "No camera detected on this device",
              color = if (cameraAvailable) ScanTeal else MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              modifier =
                  Modifier.padding(bottom = 16.dp).semantics { testTag = C.Tag.home_scan_label },
          )
        }
        Box(
            modifier = Modifier.weight(1f).padding(bottom = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
          HomeBottomTabItem(
              tab = HomeBottomTab.PROFILE,
              selected = selectedTab == HomeBottomTab.PROFILE,
              onTabSelected = onTabSelected,
          )
        }
      }
    }

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
              Modifier.size(56.dp)
                  .background(
                      color =
                          if (cameraAvailable) ScanTeal
                          else MaterialTheme.colorScheme.surfaceVariant,
                      shape = CircleShape,
                  )
                  .semantics { testTag = C.Tag.home_scan_button },
      ) {
        Icon(
            imageVector =
                if (cameraAvailable) Icons.Default.CenterFocusStrong
                else Icons.Default.NoPhotography,
            contentDescription =
                if (cameraAvailable) "Scan a monument" else "No camera detected on this device",
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
      }
    }
  }
}

@Composable
private fun HomeBottomTabItem(
    tab: HomeBottomTab,
    selected: Boolean,
    onTabSelected: (HomeBottomTab) -> Unit,
) {
  val color = if (selected) HomeActiveTab else HomeInactiveTab

  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier =
          Modifier.defaultMinSize(minWidth = 68.dp, minHeight = 56.dp)
              .clip(RoundedCornerShape(12.dp))
              .clickable { onTabSelected(tab) }
              .semantics { testTag = tab.tag }
              .padding(horizontal = 12.dp, vertical = 6.dp),
  ) {
    Icon(
        imageVector = tab.icon,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(24.dp),
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(tab.labelRes),
        color = color,
        fontSize = 11.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
    )
  }
}
