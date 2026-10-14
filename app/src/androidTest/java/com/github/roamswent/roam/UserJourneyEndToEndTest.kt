/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam

import android.Manifest
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.roamswent.roam.data.GuideCaptureSender
import com.github.roamswent.roam.model.ai.GeminiClient
import com.github.roamswent.roam.model.ai.GuideRepositoryImpl
import com.github.roamswent.roam.model.ai.ImagePayload
import com.github.roamswent.roam.model.ai.ImagePreparer
import com.github.roamswent.roam.model.ai.ImageScreen
import com.github.roamswent.roam.model.ai.NetworkUnavailableException
import com.github.roamswent.roam.model.ai.ScreenVerdict
import com.github.roamswent.roam.model.authentication.AuthRepository
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.model.location.LocationProvider
import com.github.roamswent.roam.model.permission.LocationPermissionDecision
import com.github.roamswent.roam.model.permission.SharedPreferencesLocationPermissionDecisionRepository
import com.github.roamswent.roam.repository.CaptureSender
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.authentication.SignInViewModel
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
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
  fun userCanSignInCaptureRetryCancelAndViewGuide() {
    // These fakes replace external services only. RoamApp, its navigation graph, and the
    // production screens and discovery pipeline are still used by this instrumentation journey.
    val services = FakeDiscoveryServices()
    val auth = FakeFirebaseSignIn()
    val authViewModel = SignInViewModel(FakeAuthRepository())

    // Render the real app graph with fake authentication completion and a fake-backed capture
    // sender, avoiding both Credential Manager UI and all real Firebase/Gemini requests.
    composeTestRule.runOnUiThread {
      composeTestRule.activity.setContent {
        SampleAppTheme {
          RoamApp(
              authViewModel = authViewModel,
              captureSenderOverride = services.captureSender,
              onSignInClickOverride = auth::signIn,
          )
        }
      }
    }

    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.sign_in_title))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.sign_in_google_button))
        .performClick()
    assertEquals(1, auth.signInCalls)
    waitAndAssertDisplayed(C.Tag.home_screen_container)

    // Exercise both ways to leave the camera without taking a photo: its close button and the
    // Android system back action. Both should return to the home destination.
    enterCapture()
    composeTestRule.onNodeWithTag(C.Tag.scan_close_button).performClick()
    waitAndAssertDisplayed(C.Tag.home_screen_container)

    enterCapture()
    pressSystemBack()
    waitAndAssertDisplayed(C.Tag.home_screen_container)

    // Take a photo, choose to retake it, and confirm deletion to return to camera capture.
    enterCapture()
    capturePhoto()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_button).performClick()
    waitAndAssertDisplayed(C.Tag.scan_retake_dialog)
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_confirm).performClick()
    waitAndAssertDisplayed(C.Tag.scan_camera_screen)

    // The fake image screener rejects this photo as not a monument. Verify its dedicated loading
    // error is visible and that the guide-generation Gemini client and location provider were
    // never reached. Cancel returns the user to the camera so the journey can continue.
    capturePhoto()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.loading_screen_progress)
    val notMonumentError = composeTestRule.activity.getString(R.string.loading_error_not_identified)
    waitForText(notMonumentError)
    composeTestRule.onNodeWithText(notMonumentError).assertIsDisplayed()
    assertEquals(1, services.imageScreen.calls)
    assertEquals(0, services.gemini.calls)
    assertEquals(0, services.locationProvider.calls)
    composeTestRule.onNodeWithTag(C.Tag.loading_screen_cancel_button).performClick()
    waitAndAssertDisplayed(C.Tag.scan_camera_screen)

    // The next valid monument photo reaches the fake Gemini client, whose first response is a
    // controlled network failure. The real loading screen displays the corresponding error.
    capturePhoto()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.loading_screen_progress)
    val networkError = composeTestRule.activity.getString(R.string.loading_error_network)
    waitForText(networkError)
    composeTestRule.onNodeWithText(networkError).assertIsDisplayed()
    assertEquals(1, services.gemini.calls)
    assertEquals(2, services.imageScreen.calls)
    assertEquals(1, services.locationProvider.calls)

    // Start another guide request and cancel it from the real loading screen. The fake Gemini
    // suspends this request until cancelled, allowing this assertion to verify cancellation.
    composeTestRule.onNodeWithTag(C.Tag.loading_screen_cancel_button).performClick()
    waitAndAssertDisplayed(C.Tag.scan_camera_screen)
    capturePhoto()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.loading_screen_progress)
    composeTestRule.waitUntil { services.gemini.secondCallStarted.isCompleted }
    assertEquals(2, services.gemini.calls)

    composeTestRule.onNodeWithTag(C.Tag.loading_screen_cancel_button).performClick()
    waitAndAssertDisplayed(C.Tag.scan_camera_screen)
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      services.gemini.secondCallCancelled.isCompleted
    }

    // A fresh photo and request now succeeds. Assert both guide content and the number of fake
    // service calls, so unexpected retries or skipped stages fail the test.
    capturePhoto()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.monument_detail_screen_container)
    composeTestRule.onNodeWithText("Eiffel Tower").assertIsDisplayed()
    composeTestRule.onNodeWithText("Built in 1889.").assertIsDisplayed()
    assertEquals(3, services.gemini.calls)
    assertEquals(3, services.locationProvider.calls)

    // The guide's UI back button returns to home.
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).performClick()
    waitAndAssertDisplayed(C.Tag.home_screen_container)

    // Repeat a successful scan and leave the guide with system back to verify both return paths.
    enterCapture()
    capturePhoto()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.monument_detail_screen_container)
    composeTestRule.onNodeWithText("Eiffel Tower").assertIsDisplayed()
    assertEquals(4, services.gemini.calls)
    assertEquals(4, services.locationProvider.calls)

    pressSystemBack()
    waitAndAssertDisplayed(C.Tag.home_screen_container)
  }

  private fun enterCapture() {
    // Open the real camera route through the real home screen's scan control.
    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).performClick()
    waitAndAssertDisplayed(C.Tag.scan_camera_screen)
  }

  private fun capturePhoto() {
    // Press the real shutter and wait for navigation to the photo-review screen.
    composeTestRule.onNodeWithTag(C.Tag.scan_shutter_button).performClick()
    waitAndAssertDisplayed(C.Tag.scan_review_screen)
  }

  private fun pressSystemBack() {
    // Dispatch Back through the Activity so the active Compose BackHandler handles it.
    composeTestRule.runOnUiThread {
      composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
    }
    composeTestRule.waitForIdle()
  }

  private fun waitAndAssertDisplayed(tag: String) {
    // Wait for asynchronous navigation/recomposition before checking the expected screen.
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
  }

  private fun waitForText(text: String) {
    // Loading screen errors are produced asynchronously by the discovery coroutine.
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
  }
}

private class FakeDiscoveryServices {
  val gemini = ScriptedGeminiClient()
  // Location is faked too, so the guide pipeline never depends on real device permissions, GPS,
  // or the user's physical location.
  val locationProvider = FakeLocationProvider()
  // This real repository orchestrates image preparation, screening, location, and guide parsing.
  // Only its external screening, Gemini, and location dependencies are faked below.
  val imageScreen = ScriptedImageScreen()
  private val repository =
      GuideRepositoryImpl(
          imagePreparer = ImagePreparer(),
          imageScreen = imageScreen,
          geminiClient = gemini,
          locationProvider = locationProvider,
          pause = {},
      )
  // Reuse the production sender so the flow also exercises photo-byte reading and request setup.
  val captureSender: CaptureSender =
      GuideCaptureSender(
          context = ApplicationProvider.getApplicationContext(),
          guideRepository = repository,
      )
}

/**
 * Fake remote image-screening service.
 *
 * Its first invocation simulates a photo with no monument; later invocations represent monument
 * photos so this one journey can cover the rejection and guide-generation paths.
 */
private class ScriptedImageScreen : ImageScreen {
  private val callCount = AtomicInteger()
  val calls: Int
    get() = callCount.get()

  override suspend fun screen(image: ImagePayload): ScreenVerdict {
    val isMonument = callCount.incrementAndGet() > 1
    return ScreenVerdict(
        isMonument = isMonument,
        injectionSuspected = false,
        reason = if (isMonument) "test monument" else "no monument in test image",
    )
  }
}

/** Returns fixed coordinates for deterministic guide prompts and tracks each lookup. */
private class FakeLocationProvider : LocationProvider {
  private val callCount = AtomicInteger()
  val calls: Int
    get() = callCount.get()

  override suspend fun currentCoordinates(): Coordinates? {
    callCount.incrementAndGet()
    return Coordinates(48.85837, 2.29448)
  }
}

/**
 * Fake Gemini transport with a deterministic sequence:
 * - first call fails to test the loading error UI;
 * - second call suspends so cancellation can be exercised;
 * - remaining calls return a valid guide response.
 */
private class ScriptedGeminiClient : GeminiClient {
  private val callCount = AtomicInteger()
  private val cancellationGate = CompletableDeferred<Unit>()
  val secondCallStarted = CompletableDeferred<Unit>()
  val secondCallCancelled = CompletableDeferred<Unit>()
  val calls: Int
    get() = callCount.get()

  override suspend fun generate(
      prompt: String,
      image: ImagePayload,
  ): String =
      when (callCount.incrementAndGet()) {
        1 -> throw NetworkUnavailableException()
        2 -> {
          secondCallStarted.complete(Unit)

          try {
            cancellationGate.await()
            GUIDE_REPLY
          } catch (e: CancellationException) {
            secondCallCancelled.complete(Unit)
            throw e
          }
        }
        else -> GUIDE_REPLY
      }

  companion object {
    private const val GUIDE_REPLY =
        """{"monumentName":"Eiffel Tower","confidence":0.92,"history":"Built in 1889.","facts":["It grew taller in summer."],"culture":"A symbol of Paris.","language":"en"}"""
  }
}

/** Represents the user's Google sign-in action without invoking Credential Manager or Firebase. */
private class FakeFirebaseSignIn {
  var signInCalls = 0
    private set

  fun signIn() {
    signInCalls++
  }
}

/** Fails loudly if the test accidentally bypasses its fake sign-in action. */
private class FakeAuthRepository : AuthRepository {
  override suspend fun signInWithGoogle(
      credential: androidx.credentials.Credential
  ): Result<com.google.firebase.auth.FirebaseUser> =
      Result.failure(AssertionError("The fake sign-in override should handle this interaction"))

  override fun signOut(): Result<Unit> = Result.success(Unit)
}
