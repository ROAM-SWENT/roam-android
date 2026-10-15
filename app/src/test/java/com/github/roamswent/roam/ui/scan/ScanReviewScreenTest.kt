package com.github.roamswent.roam.ui.scan

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.repository.CaptureSender
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.navigation.Routes
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScanReviewScreenTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var navController: NavHostController
  private lateinit var photoUri: Uri
  private var photoFile: File? = null

  @After
  fun deleteTemporaryPhoto() {
    photoFile?.delete()
  }

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

  @Test
  fun reviewRouteCancelDuringLoadingReturnsToCaptureAndCancelsRequest() {
    val requestStarted = AtomicBoolean(false)
    val requestCancelled = AtomicBoolean(false)
    val sender =
        object : CaptureSender {
          override suspend fun send(uri: Uri): Result<MonumentGuide> {
            requestStarted.set(true)
            return try {
              awaitCancellation()
            } finally {
              requestCancelled.set(true)
            }
          }
        }
    showReviewRoute(sender)

    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.loading_screen_progress)
    composeTestRule.waitUntil { requestStarted.get() }
    composeTestRule.onNodeWithTag(C.Tag.loading_screen_cancel_button).performClick()

    waitAndAssertDisplayed(C.Tag.scan_camera_screen)
    waitForDestination(Routes.Scan)
    composeTestRule.waitUntil { requestCancelled.get() }
  }

  @Test
  fun reviewRouteSuccessShowsGuideAndGuideButtonReturnsHome() {
    val sentUri = AtomicReference<Uri>()
    val sender =
        object : CaptureSender {
          override suspend fun send(uri: Uri): Result<MonumentGuide> {
            sentUri.set(uri)
            return Result.success(guide)
          }
        }
    showReviewRoute(sender)

    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()

    waitAndAssertDisplayed(C.Tag.monument_detail_screen_container)
    composeTestRule.onNodeWithText(guide.monumentName).assertIsDisplayed()
    assertEquals(photoUri, sentUri.get())

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).performClick()

    waitAndAssertDisplayed(C.Tag.home_screen_container)
    waitForDestination(Routes.Home)
  }

  @Test
  fun reviewRouteSystemBackFromGuideReturnsHome() {
    val sender =
        object : CaptureSender {
          override suspend fun send(uri: Uri): Result<MonumentGuide> = Result.success(guide)
        }
    showReviewRoute(sender)

    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.monument_detail_screen_container)

    composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

    waitAndAssertDisplayed(C.Tag.home_screen_container)
    waitForDestination(Routes.Home)
  }

  @Test
  fun reviewingShowsPromptPillWithEnabledActions() {
    setContent(ScanReviewUiState.Reviewing(uri))

    composeTestRule.onNodeWithTag(C.Tag.scan_review_prompt_pill).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_button).assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).assertIsDisplayed().assertIsEnabled()
  }

  @Test
  fun sendingShowsPromptPill() {
    setContent(ScanReviewUiState.Sending(uri))

    composeTestRule.onNodeWithTag(C.Tag.scan_review_prompt_pill).assertIsDisplayed()
  }

  @Test
  fun confirmRetakeShowsPromptPill() {
    setContent(ScanReviewUiState.ConfirmRetake(uri))

    composeTestRule.onNodeWithTag(C.Tag.scan_review_prompt_pill).assertIsDisplayed()
  }

  @Test
  fun deletingShowsPromptPill() {
    setContent(ScanReviewUiState.Deleting(uri))

    composeTestRule.onNodeWithTag(C.Tag.scan_review_prompt_pill).assertIsDisplayed()
  }

  @Test
  fun deleteFailedShowsPromptPill() {
    setContent(ScanReviewUiState.DeleteFailed(uri))

    composeTestRule.onNodeWithTag(C.Tag.scan_review_prompt_pill).assertIsDisplayed()
  }

  private val uri = Uri.parse("content://example/capture.jpg")
  private val guide =
      MonumentGuide(
          monumentName = "Eiffel Tower",
          confidence = 0.95,
          history = "Built in 1889.",
          facts = listOf("330 meters tall"),
          culture = "Symbol of Paris.",
          language = "en",
      )

  private fun showReviewRoute(captureSender: CaptureSender) {
    val photo =
        File.createTempFile(
            "scan-review-route",
            ".jpg",
            ApplicationProvider.getApplicationContext<Context>().cacheDir,
        )
    photo.writeBytes(byteArrayOf(1, 2, 3, 4))
    photoFile = photo
    photoUri = Uri.fromFile(photo)
    composeTestRule.activity.setContent {
      SampleAppTheme {
        val controller = rememberNavController()
        SideEffect { navController = controller }
        NavHost(navController = controller, startDestination = Routes.Home) {
          composable(Routes.Home) {
            Box(Modifier.fillMaxSize().testTag(C.Tag.home_screen_container))
          }
          composable(Routes.Scan) { Box(Modifier.fillMaxSize().testTag(C.Tag.scan_camera_screen)) }
          composable("${Routes.ScanReview}?uri={uri}") { entry ->
            ScanReviewRoute(
                navController = controller,
                uri = Uri.parse(checkNotNull(entry.arguments?.getString("uri"))),
                captureSenderOverride = captureSender,
            )
          }
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.runOnIdle {
      navController.navigate(Routes.Scan)
      navController.navigate("${Routes.ScanReview}?uri=${Uri.encode(photoUri.toString())}")
    }
    waitAndAssertDisplayed(C.Tag.scan_review_screen)
  }

  private fun waitForDestination(route: String) {
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      navController.currentDestination?.route == route
    }
  }

  private fun waitAndAssertDisplayed(tag: String) {
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
  }

  @Test
  fun sentRendersMonumentDetailScreen() {
    var homeClicked = false
    setContent(ScanReviewUiState.Sent(uri, guide), onHomeClick = { homeClicked = true })

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_screen_container).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.scan_review_screen).assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).performClick()

    assertEquals(true, homeClicked)
  }

  @Test
  fun sendFailedRendersDialogAndInvokesActions() {
    var retried = false
    var dismissed = false
    setContent(
        ScanReviewUiState.SendFailed(uri, "No monument was recognized in this photo."),
        onRetrySend = { retried = true },
        onDismissSendError = { dismissed = true },
    )

    composeTestRule.onNodeWithTag(C.Tag.scan_send_failed_dialog).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_button).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.scan_send_failed_retry).performClick()
    composeTestRule.onNodeWithTag(C.Tag.scan_send_failed_dismiss).performClick()

    assertEquals(true, retried)
    assertEquals(true, dismissed)
  }

  private fun setContent(
      state: ScanReviewUiState,
      onRetake: () -> Unit = {},
      onRetakeConfirm: () -> Unit = {},
      onRetakeCancel: () -> Unit = {},
      onRetryDelete: () -> Unit = {},
      onDismissDeleteError: () -> Unit = {},
      onSend: () -> Unit = {},
      onRetrySend: () -> Unit = {},
      onDismissSendError: () -> Unit = {},
      onHomeClick: () -> Unit = {},
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
            onRetrySend = onRetrySend,
            onDismissSendError = onDismissSendError,
            onHomeClick = onHomeClick,
        )
      }
    }
  }
}
