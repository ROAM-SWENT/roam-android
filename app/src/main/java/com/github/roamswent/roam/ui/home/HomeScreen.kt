/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.components.bottomdock.AppBottomDock
import com.github.roamswent.roam.ui.components.bottomdock.DockDestination
import com.github.roamswent.roam.ui.components.bottomdock.dockContentClearance
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import com.github.roamswent.roam.ui.theme.ScreenBackground

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onScanClicked: () -> Unit,
    cameraAvailable: Boolean = true,
    userName: String = HomeViewModel.DEFAULT_USER_NAME,
    onDockDestinationSelected: (DockDestination) -> Unit = {},
    isConnectedToInternet: Boolean? = null,
) {
  val isOnline = isConnectedToInternet ?: rememberInternetConnectivity()

  Box(
      modifier =
          modifier.fillMaxSize().background(ScreenBackground).semantics {
            testTag = C.Tag.home_screen_container
          }
  ) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = dockContentClearance())
    ) {
      HomeGreetingHeader(userName = userName)
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

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
  SampleAppTheme { HomeScreen(onScanClicked = {}) }
}
