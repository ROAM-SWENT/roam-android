package com.github.roamswent.roam.ui.scan

import android.view.View
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScanScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun streamingRendersCameraViewfinderAndEnabledShutter() {
    setContent(ScanUiState.Streaming)

    composeTestRule.onNodeWithTag(C.Tag.scan_camera_screen).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.scan_camera_viewfinder).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.scan_shutter_button).assertIsEnabled()
  }

  @Test
  fun capturingDisablesShutter() {
    setContent(ScanUiState.Capturing)

    composeTestRule.onNodeWithTag(C.Tag.scan_shutter_button).assertIsNotEnabled()
  }

  @Test
  fun capturedDisablesShutter() {
    setContent(ScanUiState.Captured)

    composeTestRule.onNodeWithTag(C.Tag.scan_shutter_button).assertIsNotEnabled()
  }

  @Test
  fun streamingShutterInvokesCapture() {
    var captures = 0
    setContent(ScanUiState.Streaming, onCapture = { captures++ })

    composeTestRule.onNodeWithTag(C.Tag.scan_shutter_button).performClick()

    assertEquals(1, captures)
  }

  @Test
  fun revokedRendersBackControlAndInvokesGoBack() {
    var goBacks = 0
    setContent(ScanUiState.Revoked, onGoBack = { goBacks++ })

    composeTestRule.onNodeWithTag(C.Tag.scan_camera_revoked_container).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.scan_camera_revoked_back).assertIsDisplayed().performClick()

    assertEquals(1, goBacks)
  }

  @Test
  fun streamingShowsFramingReticle() {
    setContent(ScanUiState.Streaming)

    composeTestRule.onNodeWithTag(C.Tag.scan_framing_reticle).assertIsDisplayed()
  }

  @Test
  fun capturingShowsFramingReticle() {
    setContent(ScanUiState.Capturing)

    composeTestRule.onNodeWithTag(C.Tag.scan_framing_reticle).assertIsDisplayed()
  }

  @Test
  fun capturedShowsFramingReticle() {
    setContent(ScanUiState.Captured)

    composeTestRule.onNodeWithTag(C.Tag.scan_framing_reticle).assertIsDisplayed()
  }

  @Test
  fun revokedDoesNotShowFramingReticle() {
    setContent(ScanUiState.Revoked)

    composeTestRule.onNodeWithTag(C.Tag.scan_framing_reticle).assertDoesNotExist()
  }

  @Test
  fun streamingShowsCloseButtonAndPromptPill() {
    setContent(ScanUiState.Streaming)

    composeTestRule.onNodeWithTag(C.Tag.scan_close_button).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.scan_prompt_pill).assertIsDisplayed()
  }

  @Test
  fun closeButtonInvokesGoBack() {
    var goBacks = 0
    setContent(ScanUiState.Streaming, onGoBack = { goBacks++ })

    composeTestRule.onNodeWithTag(C.Tag.scan_close_button).performClick()

    assertEquals(1, goBacks)
  }

  @Test
  fun capturingKeepsCloseButtonDisplayed() {
    setContent(ScanUiState.Capturing)

    composeTestRule.onNodeWithTag(C.Tag.scan_close_button).assertIsDisplayed()
  }

  @Test
  fun capturedKeepsCloseButtonDisplayed() {
    setContent(ScanUiState.Captured)

    composeTestRule.onNodeWithTag(C.Tag.scan_close_button).assertIsDisplayed()
  }

  @Test
  fun revokedDoesNotShowCloseButtonOrPromptPill() {
    setContent(ScanUiState.Revoked)

    composeTestRule.onNodeWithTag(C.Tag.scan_close_button).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.scan_prompt_pill).assertDoesNotExist()
  }

  @Test
  fun revokedDoesNotShowShutterButton() {
    setContent(ScanUiState.Revoked)

    composeTestRule.onNodeWithTag(C.Tag.scan_shutter_button).assertDoesNotExist()
  }

  @Test
  fun capturingShowsPromptPill() {
    setContent(ScanUiState.Capturing)

    composeTestRule.onNodeWithTag(C.Tag.scan_prompt_pill).assertIsDisplayed()
  }

  @Test
  fun capturedShowsPromptPill() {
    setContent(ScanUiState.Captured)

    composeTestRule.onNodeWithTag(C.Tag.scan_prompt_pill).assertIsDisplayed()
  }

  @Test
  fun closeClickInvokesGoBackWithoutCapturing() {
    var goBacks = 0
    var captures = 0
    setContent(ScanUiState.Streaming, onCapture = { captures++ }, onGoBack = { goBacks++ })

    composeTestRule.onNodeWithTag(C.Tag.scan_close_button).performClick()

    assertEquals(1, goBacks)
    assertEquals(0, captures)
  }

  @Test
  fun closeClickWhileCapturingInvokesGoBack() {
    var goBacks = 0
    setContent(ScanUiState.Capturing, onGoBack = { goBacks++ })

    composeTestRule.onNodeWithTag(C.Tag.scan_close_button).performClick()

    assertEquals(1, goBacks)
  }

  @Test
  fun closeClickWhenCapturedInvokesGoBack() {
    var goBacks = 0
    setContent(ScanUiState.Captured, onGoBack = { goBacks++ })

    composeTestRule.onNodeWithTag(C.Tag.scan_close_button).performClick()

    assertEquals(1, goBacks)
  }

  private fun setContent(
      state: ScanUiState,
      onCapture: () -> Unit = {},
      onGoBack: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      SampleAppTheme {
        ScanScreen(
            state = state,
            onCapture = onCapture,
            onGoBack = onGoBack,
            previewView = View(ApplicationProvider.getApplicationContext()),
        )
      }
    }
  }
}
