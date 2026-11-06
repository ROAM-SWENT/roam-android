/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.components.bottomdock.AppBottomDock
import com.github.roamswent.roam.ui.components.bottomdock.DockDestination
import com.github.roamswent.roam.ui.components.bottomdock.dockContentClearance
import com.github.roamswent.roam.ui.theme.HomeAccentGreen
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import com.github.roamswent.roam.ui.theme.ScreenBackground
import com.github.roamswent.roam.ui.theme.isDark

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onScanClicked: () -> Unit,
    cameraAvailable: Boolean = true,
    userName: String = HomeViewModel.DEFAULT_USER_NAME,
    onDockDestinationSelected: (DockDestination) -> Unit = {},
    onProfileClicked: () -> Unit = {},
    isConnectedToInternet: Boolean? = null,
) {
  val isOnline = isConnectedToInternet ?: rememberInternetConnectivity()

  Box(
      modifier =
          modifier
              .fillMaxSize()
              .background(
                  if (MaterialTheme.isDark) MaterialTheme.colorScheme.background
                  else ScreenBackground
              )
              .semantics { testTag = C.Tag.home_screen_container }
  ) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = dockContentClearance())
    ) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        HomeGreetingHeader(
            userName = userName,
            modifier = Modifier.weight(1f),
        )
        ProfileBubble(
            userName = userName,
            onClick = onProfileClicked,
            modifier = Modifier.padding(top = 16.dp),
        )
      }
      if (!isOnline) {
        OfflineNoticeCard(modifier = Modifier.padding(bottom = 12.dp))
      }
      ScanPromptCard(onPromptClick = {})
    }

    Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
      AppBottomDock(
          selected = DockDestination.HOME,
          onDestinationSelected = onDockDestinationSelected,
          onScanClick = onScanClicked,
          cameraAvailable = cameraAvailable,
      )
    }
  }
}

@Composable
private fun ProfileBubble(userName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val description = stringResource(R.string.home_profile_button_description)
  Surface(
      onClick = onClick,
      modifier =
          modifier.size(48.dp).semantics {
            contentDescription = description
            testTag = C.Tag.home_profile_button
          },
      shape = CircleShape,
      color = HomeAccentGreen,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
          text = userName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
          color = Color.White,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
  SampleAppTheme { HomeScreen(onScanClicked = {}) }
}
