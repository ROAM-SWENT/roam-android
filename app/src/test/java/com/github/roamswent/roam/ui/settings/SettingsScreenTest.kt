/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.credentials.CredentialManager
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun initialState_displaysSettingsButtonsAndPermissionStatuses() {
    val viewModel = mock<SettingsViewModel>()
    whenever(viewModel.uiState)
        .thenReturn(
            MutableStateFlow(
                SettingsUiState(
                    cameraPermissionGranted = true,
                    locationPermissionGranted = false,
                )
            )
        )

    composeTestRule.setContent { SampleAppTheme { SettingsScreen(settingsViewModel = viewModel) } }

    composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    composeTestRule.onNodeWithText("Manage Permissions").assertIsDisplayed()
    composeTestRule.onNodeWithText("Log Out").assertIsDisplayed()
    composeTestRule.onNodeWithText("Camera permission").assertIsDisplayed()
    composeTestRule.onNodeWithText("Localisation permission").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Granted", useUnmergedTree = true).assertCountEquals(1)
    composeTestRule.onAllNodesWithText("Denied", useUnmergedTree = true).assertCountEquals(1)
  }

  @Test
  fun permissionStateChanges_updateCameraAndLocationStatuses() {
    val viewModel = mock<SettingsViewModel>()
    val state = MutableStateFlow(SettingsUiState())
    whenever(viewModel.uiState).thenReturn(state)

    composeTestRule.setContent { SampleAppTheme { SettingsScreen(settingsViewModel = viewModel) } }

    composeTestRule.onAllNodesWithText("Denied", useUnmergedTree = true).assertCountEquals(2)

    state.value = SettingsUiState(cameraPermissionGranted = true, locationPermissionGranted = true)
    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithText("Granted", useUnmergedTree = true).assertCountEquals(2)
    composeTestRule.onAllNodesWithText("Denied", useUnmergedTree = true).assertCountEquals(0)

    state.value = SettingsUiState()
    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithText("Granted", useUnmergedTree = true).assertCountEquals(0)
    composeTestRule.onAllNodesWithText("Denied", useUnmergedTree = true).assertCountEquals(2)
  }

  @Test
  fun buttons_invokeCallbacksAndSignOutSuccessNotifiesCaller() {
    val viewModel = mock<SettingsViewModel>()
    val state = MutableStateFlow(SettingsUiState())
    val credentialManager = mock<CredentialManager>()
    whenever(viewModel.uiState).thenReturn(state)
    var managePermissionsClicks = 0
    var signedOutCallbacks = 0

    composeTestRule.setContent {
      SampleAppTheme {
        SettingsScreen(
            settingsViewModel = viewModel,
            credentialManager = credentialManager,
            onManagePermissionsClick = { managePermissionsClicks++ },
            onSignedOut = { signedOutCallbacks++ },
        )
      }
    }

    composeTestRule.onNodeWithText("Manage Permissions").performClick()
    composeTestRule.onNodeWithText("Log Out").performClick()

    assertEquals(1, managePermissionsClicks)
    verify(viewModel).signOut(credentialManager)
    assertEquals(0, signedOutCallbacks)

    state.value = SettingsUiState(signedOut = true)
    composeTestRule.waitForIdle()

    assertEquals(1, signedOutCallbacks)
  }

  @Test
  fun errorState_displaysToastAndClearsErrorMessage() {
    val viewModel = mock<SettingsViewModel>()
    whenever(viewModel.uiState)
        .thenReturn(MutableStateFlow(SettingsUiState(errorMsg = "Unable to log out")))

    composeTestRule.setContent { SampleAppTheme { SettingsScreen(settingsViewModel = viewModel) } }
    composeTestRule.waitForIdle()

    assertEquals("Unable to log out", ShadowToast.getTextOfLatestToast())
    verify(viewModel).clearErrorMsg()
  }
    @Test
    fun errorAppearsAfterInitialRender() {
        val viewModel = mock<SettingsViewModel>()
        val state = MutableStateFlow(SettingsUiState())

        whenever(viewModel.uiState).thenReturn(state)

        composeTestRule.setContent {
            SampleAppTheme {
                SettingsScreen(settingsViewModel = viewModel)
            }
        }

        state.value = SettingsUiState(
            errorMsg = "Logout failed"
        )

        composeTestRule.waitForIdle()

        assertEquals(
            "Logout failed",
            ShadowToast.getTextOfLatestToast()
        )

        verify(viewModel).clearErrorMsg()
    }
}
