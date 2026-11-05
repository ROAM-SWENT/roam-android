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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import com.github.roamswent.roam.ui.theme.ScreenBackground

@Composable
fun HomeScreen(
    onScanClicked: () -> Unit,
    cameraAvailable: Boolean = true,
    userName: String = HomeViewModel.DEFAULT_USER_NAME,
    onProfileClicked: () -> Unit = {},
    isConnectedToInternet: Boolean? = null,
    isDarkTheme: Boolean,
) {
  val isOnline = isConnectedToInternet ?: rememberInternetConnectivity()

  Box(
      modifier =
          Modifier.fillMaxSize()
              .background(
                  if (isDarkTheme) MaterialTheme.colorScheme.background else ScreenBackground
              )
              .semantics { testTag = C.Tag.home_screen_container }
  ) {
    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp)) {
      HomeGreetingHeader(userName = userName, isDarkTheme = isDarkTheme)
      if (!isOnline) {
        OfflineNoticeCard(
            modifier = Modifier.padding(bottom = 12.dp),
            isDarkTheme = isDarkTheme,
        )
      }
      ScanPromptCard(onPromptClick = {})
    }

    Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
      HomeBottomBar(
          selectedTab = HomeBottomTab.HOME,
          onTabSelected = { tab -> if (tab == HomeBottomTab.PROFILE) onProfileClicked() },
          onScanClick = onScanClicked,
          cameraAvailable = cameraAvailable,
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
  SampleAppTheme { HomeScreen(onScanClicked = {}, isDarkTheme = false) }
}
