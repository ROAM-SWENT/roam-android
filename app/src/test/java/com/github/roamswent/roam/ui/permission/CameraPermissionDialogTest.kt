package com.github.roamswent.roam.ui.permission

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
  fun dialogShowsSettingsAndDismissButtons() {
    composeTestRule.setContent { CameraPermissionDialog(onOpenSettings = {}, onDismiss = {}) }

    composeTestRule.onNodeWithTag(C.Tag.camera_permission_dialog).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.camera_permission_settings_button).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.camera_permission_dismiss_button).assertIsDisplayed()
  }

  @Test
  fun settingsButtonInvokesCallback() {
    var settingsClicks = 0
    composeTestRule.setContent {
      CameraPermissionDialog(
          onOpenSettings = { settingsClicks++ },
          onDismiss = {},
      )
    }

    composeTestRule.onNodeWithTag(C.Tag.camera_permission_settings_button).performClick()
    assertEquals(1, settingsClicks)
  }

  @Test
  fun dismissButtonInvokesCallback() {
    var clicks = 0
    composeTestRule.setContent {
      CameraPermissionDialog(
          onOpenSettings = {},
          onDismiss = { clicks++ },
      )
    }

    composeTestRule.onNodeWithTag(C.Tag.camera_permission_dismiss_button).performClick()
    assertEquals(1, clicks)
  }
}
