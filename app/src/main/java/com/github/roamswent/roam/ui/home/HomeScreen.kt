// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.components.ProfilePicture
import com.github.roamswent.roam.ui.components.bottomdock.AppBottomDock
import com.github.roamswent.roam.ui.components.bottomdock.DockDestination
import com.github.roamswent.roam.ui.components.bottomdock.dockContentClearance
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import com.github.roamswent.roam.ui.theme.ScreenBackground
import com.github.roamswent.roam.ui.theme.isDark

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onScanClicked: () -> Unit,
    cameraAvailable: Boolean = true,
    userName: String = HomeViewModel.DEFAULT_USER_NAME,
    avatarBytes: ByteArray? = null,
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
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = dockContentClearance())
    ) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        HomeGreetingHeader(
            userName = userName,
            modifier = Modifier.weight(1f),
        )
        ProfileBubble(
            name = userName,
            avatarBytes = avatarBytes,
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
private fun ProfileBubble(
    name: String,
    avatarBytes: ByteArray? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val description = stringResource(R.string.home_profile_button_description)
  ProfilePicture(
      name = name,
      avatarBytes = avatarBytes,
      modifier =
          modifier.size(48.dp).semantics {
            contentDescription = description
            testTag = C.Tag.home_profile_button
          },
      textStyle = MaterialTheme.typography.titleSmall,
      onClick = onClick,
  )
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
  SampleAppTheme { HomeScreen(onScanClicked = {}) }
}
