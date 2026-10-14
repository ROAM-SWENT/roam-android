/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.roamswent.roam.data.GuideCaptureSender
import com.github.roamswent.roam.data.NullLocationProvider
import com.github.roamswent.roam.model.ai.GeminiClient
import com.github.roamswent.roam.model.ai.GuideRepositoryImpl
import com.github.roamswent.roam.model.ai.ImagePayload
import com.github.roamswent.roam.model.ai.ImagePreparer
import com.github.roamswent.roam.model.ai.ImageScreen
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.ai.ScreenVerdict
import com.github.roamswent.roam.model.authentication.AuthRepository
import com.github.roamswent.roam.model.permission.LocationPermissionDecision
import com.github.roamswent.roam.model.permission.SharedPreferencesLocationPermissionDecisionRepository
import com.github.roamswent.roam.repository.CaptureSender
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.authentication.SignInViewModel
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserJourneyEndToEndTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val locationPermissionDecisions =
      SharedPreferencesLocationPermissionDecisionRepository(
          ApplicationProvider.getApplicationContext()
      )

  @Before
  fun prepareAppPermissions() {
    locationPermissionDecisions.saveDecision(LocationPermissionDecision.DENIED)
    InstrumentationRegistry.getInstrumentation()
        .uiAutomation
        .grantRuntimePermission(
            ApplicationProvider.getApplicationContext<Context>().packageName,
            Manifest.permission.CAMERA,
        )
  }

  @After
  fun clearLocationPermissionDecision() {
    ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("location_permission", Context.MODE_PRIVATE)
        .edit()
        .clear()
        .commit()
  }

  @Test
  fun backFromSignInClosesApp() {
    renderRoamApp(FakeDiscoveryServices(DiscoveryScenario.Monument))

    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.sign_in_title))
        .assertIsDisplayed()

    pressSystemBack()

    composeTestRule.waitUntil { composeTestRule.activity.isFinishing }
  }

  @Test
  fun signingInThenGoingBackFromHomeClosesApp() {
    val auth = renderRoamApp(FakeDiscoveryServices(DiscoveryScenario.Monument))

    signIn()
    assertEquals(1, auth.signInCalls)
    waitAndAssertDisplayed(C.Tag.home_screen_container)

    pressSystemBack()

    composeTestRule.waitUntil { composeTestRule.activity.isFinishing }
  }

  @Test
  fun userCanRetakeAndSeeNonMonumentErrorWithoutCallingGemini() {
    val services = FakeDiscoveryServices(DiscoveryScenario.NotMonument)
    val auth = renderRoamApp(services)
    signIn()
    assertEquals(1, auth.signInCalls)

    enterCapture()
    pressSystemBack()
    waitAndAssertDisplayed(C.Tag.home_screen_container)

    enterCapture()
    capturePhoto()
    waitAndAssertDisplayed(C.Tag.scan_review_screen)

    composeTestRule.onNodeWithTag(C.Tag.scan_retake_button).performClick()
    waitAndAssertDisplayed(C.Tag.scan_retake_dialog)
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_cancel).performClick()
    waitAndAssertDisplayed(C.Tag.scan_review_screen)

    composeTestRule.onNodeWithTag(C.Tag.scan_retake_button).performClick()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_confirm).performClick()
    waitAndAssertDisplayed(C.Tag.scan_camera_screen)

    capturePhoto()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.scan_send_failed_dialog)
    composeTestRule.onNodeWithText("No monument was recognized in this photo.").assertIsDisplayed()
    assertEquals(0, services.gemini.calls)

    composeTestRule.onNodeWithTag(C.Tag.scan_send_failed_retry).performClick()
    waitAndAssertDisplayed(C.Tag.scan_send_failed_dialog)
    composeTestRule.onNodeWithTag(C.Tag.scan_send_failed_dismiss).performClick()
    waitAndAssertDisplayed(C.Tag.scan_review_screen)
    assertEquals(0, services.gemini.calls)
  }

  @Test
  fun userCanGetGuideAndGoBackFromResult() {
    val services = FakeDiscoveryServices(DiscoveryScenario.Monument)
    renderRoamApp(services)
    signIn()
    enterCapture()
    capturePhoto()

    pressSystemBack()
    waitAndAssertDisplayed(C.Tag.scan_retake_dialog)
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_cancel).performClick()

    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.monument_detail_screen_container)
    composeTestRule.onNodeWithText("Eiffel Tower").assertIsDisplayed()
    composeTestRule.onNodeWithText("Built in 1889.").assertIsDisplayed()
    assertEquals(1, services.gemini.calls)

    pressSystemBack()
    waitAndAssertDisplayed(C.Tag.home_screen_container)
  }

  @Test
  fun systemBackDoesNotCancelAnInFlightGuideRequest() {
    val services = FakeDiscoveryServices(DiscoveryScenario.Monument, holdRequest = true)
    renderRoamApp(services)
    signIn()
    enterCapture()
    capturePhoto()

    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    composeTestRule.waitUntil { services.requestStarted.isCompleted }
    waitAndAssertDisplayed(C.Tag.scan_sending_dialog)

    pressSystemBack()
    waitAndAssertDisplayed(C.Tag.scan_sending_dialog)

    services.releaseRequest()
    waitAndAssertDisplayed(C.Tag.monument_detail_screen_container)
  }

  private fun renderRoamApp(services: FakeDiscoveryServices): FakeFirebaseSignIn {
    val auth = FakeFirebaseSignIn()
    val authViewModel = SignInViewModel(FakeAuthRepository())
    composeTestRule.runOnUiThread {
      composeTestRule.activity.setContent {
        com.github.roamswent.roam.ui.theme.SampleAppTheme {
          RoamApp(
              authViewModel = authViewModel,
              captureSenderOverride = services.captureSender,
              onSignInClickOverride = auth::signIn,
          )
        }
      }
    }
    return auth
  }

  private fun signIn() {
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.sign_in_google_button))
        .performClick()
    waitAndAssertDisplayed(C.Tag.home_screen_container)
  }

  private fun enterCapture() {
    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).performClick()
    waitAndAssertDisplayed(C.Tag.scan_camera_screen)
  }

  private fun capturePhoto() {
    composeTestRule.onNodeWithTag(C.Tag.scan_shutter_button).performClick()
    waitAndAssertDisplayed(C.Tag.scan_review_screen)
  }

  private fun pressSystemBack() {
    composeTestRule.runOnUiThread {
      composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
    }
    composeTestRule.waitForIdle()
  }

  private fun waitAndAssertDisplayed(tag: String) {
    composeTestRule.waitUntil {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
  }
}

private enum class DiscoveryScenario {
  Monument,
  NotMonument,
}

private class FakeDiscoveryServices(
    scenario: DiscoveryScenario,
    holdRequest: Boolean = false,
) {
  private val imageBytes = sampleJpeg()
  private val requestGate = CompletableDeferred<Unit>()
  val requestStarted = CompletableDeferred<Unit>()
  val gemini = RecordingGeminiClient(GUIDE_REPLY)
  private val repository =
      GuideRepositoryImpl(
          imagePreparer = ImagePreparer(),
          imageScreen =
              FixedImageScreen(
                  ScreenVerdict(
                      isMonument = scenario == DiscoveryScenario.Monument,
                      injectionSuspected = false,
                      reason = "instrumentation test",
                  )
              ),
          geminiClient = gemini,
          locationProvider = NullLocationProvider(),
          pause = {},
      )
  private val guideCaptureSender =
      GuideCaptureSender(
          context = ApplicationProvider.getApplicationContext(),
          guideRepository = repository,
          readBytes = { imageBytes },
      )
  val captureSender =
      object : CaptureSender {
        override suspend fun send(uri: Uri): Result<MonumentGuide> {
          if (holdRequest) {
            requestStarted.complete(Unit)
            requestGate.await()
          }
          return guideCaptureSender.send(uri)
        }
      }

  fun releaseRequest() {
    requestGate.complete(Unit)
  }

  companion object {
    private const val GUIDE_REPLY =
        """{"monumentName":"Eiffel Tower","confidence":0.92,"history":"Built in 1889.","facts":["It grew taller in summer."],"culture":"A symbol of Paris.","language":"en"}"""

    private fun sampleJpeg(): ByteArray {
      val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
      val output = java.io.ByteArrayOutputStream()
      bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
      bitmap.recycle()
      return output.toByteArray()
    }
  }
}

private class FixedImageScreen(private val verdict: ScreenVerdict) : ImageScreen {
  override suspend fun screen(image: ImagePayload): ScreenVerdict = verdict
}

private class RecordingGeminiClient(private val reply: String) : GeminiClient {
  var calls = 0
    private set

  override suspend fun generate(prompt: String, image: ImagePayload): String {
    calls++
    return reply
  }
}

private class FakeFirebaseSignIn {
  var signInCalls = 0
    private set

  fun signIn() {
    signInCalls++
  }
}

private class FakeAuthRepository : AuthRepository {
  override suspend fun signInWithGoogle(
      credential: androidx.credentials.Credential
  ): Result<com.google.firebase.auth.FirebaseUser> =
      Result.failure(AssertionError("The fake sign-in override should handle this interaction"))

  override fun signOut(): Result<Unit> = Result.success(Unit)
}
