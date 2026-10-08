package com.github.roamswent.roam.ui.scan

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.navigation.Routes
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScanReviewScreenTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun reviewingRendersPhotoAndEnabledActions() {
    setContent(ScanReviewUiState.Reviewing(uri))

    composeTestRule.onNodeWithTag(C.Tag.scan_review_screen).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.scan_review_photo).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_button).assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).assertIsEnabled()
  }

  @Test
  fun confirmRetakeRendersDialogAndInvokesActions() {
    var confirmed = false
    var cancelled = false
    setContent(
        ScanReviewUiState.ConfirmRetake(uri),
        onRetakeConfirm = { confirmed = true },
        onRetakeCancel = { cancelled = true },
    )

    composeTestRule.onNodeWithTag(C.Tag.scan_retake_dialog).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_confirm).performClick()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_cancel).performClick()

    assertEquals(true, confirmed)
    assertEquals(true, cancelled)
  }

  @Test
  fun sendingRendersNonDismissibleDialogAndDisablesActions() {
    setContent(ScanReviewUiState.Sending(uri))

    composeTestRule.onNodeWithTag(C.Tag.scan_sending_dialog).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_button).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).assertIsNotEnabled()
    composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
    composeTestRule.onNodeWithTag(C.Tag.scan_sending_dialog).assertIsDisplayed()
  }

  @Test
  fun deletingRendersLockedReviewWithoutDialog() {
    setContent(ScanReviewUiState.Deleting(uri))

    composeTestRule.onNodeWithTag(C.Tag.scan_review_screen).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.scan_review_photo).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_button).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).assertIsNotEnabled()
    composeTestRule.onAllNodesWithTag(C.Tag.scan_retake_dialog).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(C.Tag.scan_sending_dialog).assertCountEquals(0)
  }

  @Test
  fun deleteFailedRendersDialogAndInvokesActions() {
    var retried = false
    var stayed = false
    setContent(
        ScanReviewUiState.DeleteFailed(uri),
        onRetryDelete = { retried = true },
        onDismissDeleteError = { stayed = true },
    )

    composeTestRule.onNodeWithTag(C.Tag.scan_retake_delete_failed_dialog).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_delete_retry).performClick()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_delete_stay).performClick()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_button).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).assertIsNotEnabled()

    assertEquals(true, retried)
    assertEquals(true, stayed)
  }

  @Test
  fun reviewingActionClicksInvokeLambdas() {
    var retakeClicks = 0
    var sendClicks = 0
    setContent(
        ScanReviewUiState.Reviewing(uri),
        onRetake = { retakeClicks++ },
        onSend = { sendClicks++ },
    )

    composeTestRule.onNodeWithTag(C.Tag.scan_retake_button).performClick()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()

    assertEquals(1, retakeClicks)
    assertEquals(1, sendClicks)
  }

  @Test
  fun reviewRouteRendersScreenAndBackOpensRetakeDialog() {
    composeTestRule.activity.setContent {
      SampleAppTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = Routes.Home) {
          composable(Routes.Home) {
            LaunchedEffect(Unit) {
              navController.navigate("${Routes.ScanReview}?uri=${Uri.encode(uri.toString())}")
            }
          }
          composable("${Routes.ScanReview}?uri={uri}") { entry ->
            ScanReviewRoute(
                navController = navController,
                uri = Uri.parse(checkNotNull(entry.arguments?.getString("uri"))),
            )
          }
        }
      }
    }

    composeTestRule.waitUntil {
      composeTestRule.onAllNodesWithTag(C.Tag.scan_review_screen).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag(C.Tag.scan_review_screen).assertIsDisplayed()
    composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_dialog).assertIsDisplayed()
  }

  private val uri = Uri.parse("content://example/capture.jpg")

  private fun setContent(
      state: ScanReviewUiState,
      onRetake: () -> Unit = {},
      onRetakeConfirm: () -> Unit = {},
      onRetakeCancel: () -> Unit = {},
      onRetryDelete: () -> Unit = {},
      onDismissDeleteError: () -> Unit = {},
      onSend: () -> Unit = {},
  ) {
    composeTestRule.activity.setContent {
      SampleAppTheme {
        ScanReviewScreen(
            state = state,
            onRetake = onRetake,
            onRetakeConfirm = onRetakeConfirm,
            onRetakeCancel = onRetakeCancel,
            onRetryDelete = onRetryDelete,
            onDismissDeleteError = onDismissDeleteError,
            onSend = onSend,
        )
      }
    }
  }
}
