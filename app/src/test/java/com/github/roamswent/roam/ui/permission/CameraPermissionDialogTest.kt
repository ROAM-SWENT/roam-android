package com.github.roamswent.roam.ui.permission

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.roamswent.roam.resources.C
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CameraPermissionDialogTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun rationaleShowsRetryAndDismissButtons() {
    composeTestRule.setContent { dialog(CameraPermissionState.Denied(showRationale = true)) }

    composeTestRule.onNodeWithTag(C.Tag.camera_permission_dialog).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.camera_permission_retry_button).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.camera_permission_settings_button).assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.camera_permission_dismiss_button).assertIsDisplayed()
  }

  @Test
  fun noRationaleShowsSettingsAndDismissButtons() {
    composeTestRule.setContent { dialog(CameraPermissionState.Denied(showRationale = false)) }

    composeTestRule.onNodeWithTag(C.Tag.camera_permission_dialog).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.camera_permission_retry_button).assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.camera_permission_settings_button).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.camera_permission_dismiss_button).assertIsDisplayed()
  }

  @Test
  fun retryButtonInvokesCallbackOncePerClick() {
    var clicks = 0
    composeTestRule.setContent {
      CameraPermissionDialog(
          state = CameraPermissionState.Denied(showRationale = true),
          onRetry = { clicks++ },
          onOpenSettings = {},
          onDismiss = {},
      )
    }

    val button = composeTestRule.onNodeWithTag(C.Tag.camera_permission_retry_button)
    button.performClick()
    button.performClick()
    assertEquals(2, clicks)
  }

  @Test
  fun settingsButtonInvokesCallback() {
    var clicks = 0
    composeTestRule.setContent {
      CameraPermissionDialog(
          state = CameraPermissionState.Denied(showRationale = false),
          onRetry = {},
          onOpenSettings = { clicks++ },
          onDismiss = {},
      )
    }

    composeTestRule.onNodeWithTag(C.Tag.camera_permission_settings_button).performClick()
    assertEquals(1, clicks)
  }

  @Test
  fun dismissButtonInvokesCallback() {
    var clicks = 0
    composeTestRule.setContent {
      CameraPermissionDialog(
          state = CameraPermissionState.Denied(showRationale = true),
          onRetry = {},
          onOpenSettings = {},
          onDismiss = { clicks++ },
      )
    }

    composeTestRule.onNodeWithTag(C.Tag.camera_permission_dismiss_button).performClick()
    assertEquals(1, clicks)
  }

  @Composable
  private fun dialog(state: CameraPermissionState.Denied) {
    CameraPermissionDialog(state = state, onRetry = {}, onOpenSettings = {}, onDismiss = {})
  }
}
