/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
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
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests one user journey with Roam's real screens and navigation. The test uses Android's
 * permission dialogs and fake sign-in and AI services.
 */
@RunWith(AndroidJUnit4::class)
class UserJourneyEndToEndTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  // Roam saves the result of the location permission prompt here.
  private val locationPermissionDecisions =
      SharedPreferencesLocationPermissionDecisionRepository(
          ApplicationProvider.getApplicationContext()
      )

  @After
  fun clearLocationPermissionDecision() {
    // Remove saved permission data so it cannot affect another test.
    ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("location_permission", Context.MODE_PRIVATE)
        .edit()
        .clear()
        .commit()
  }

  @Test
  fun userCanSignInCaptureRetryCancelAndViewGuide() {
    // The app screens and navigation are real. These fakes replace sign-in and remote AI services.
    val services = FakeDiscoveryServices()
    val auth = FakeFirebaseSignIn()
    val authViewModel = SignInViewModel(FakeAuthRepository())

    // Show the real app. The sign-in callback skips the Google account dialog. The real capture
    // sender and guide repository still run, but their outside services are fakes.
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

    // Permissions and the saved choice start empty. Sign-in opens the real Android location
    // prompt. Choose precise location, then check Android's grants and Roam's saved choice.
    acceptPreciseLocationPermission()
    assertEquals(LocationPermissionDecision.GRANTED, locationPermissionDecisions.getDecision())
    assertPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
    assertPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)
    waitAndAssertDisplayed(C.Tag.home_screen_container)

    // Both the camera's close button and Android's Back button should return to Home.
    enterCapture()
    composeTestRule.onNodeWithTag(C.Tag.scan_close_button).performClick()
    waitAndAssertDisplayed(C.Tag.home_screen_container)

    enterCapture()
    pressSystemBack()
    waitAndAssertDisplayed(C.Tag.home_screen_container)

    // Take a photo, choose Retake, and confirm. The app should return to the camera.
    enterCapture()
    capturePhoto()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_button).performClick()
    waitAndAssertDisplayed(C.Tag.scan_retake_dialog)
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_confirm).performClick()
    waitAndAssertDisplayed(C.Tag.scan_camera_screen)

    // The first fake screen says this photo is not a monument. Check the error message and make
    // sure no location or guide request was made. Cancel should return to the camera.
    capturePhoto()
    val rejectedPhoto = readCapturedPhoto()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.loading_screen_progress)
    val notMonumentError = composeTestRule.activity.getString(R.string.loading_error_not_identified)
    waitForText(notMonumentError)
    composeTestRule.onNodeWithText(notMonumentError).assertIsDisplayed()
    assertEquals(1, services.imageScreen.calls)
    assertPhotoWasScreened(services, 0, rejectedPhoto)
    assertEquals(0, services.gemini.calls)
    assertEquals(0, services.locationProvider.calls)
    composeTestRule.onNodeWithTag(C.Tag.loading_screen_cancel_button).performClick()
    waitAndAssertDisplayed(C.Tag.scan_camera_screen)

    // The next photo passes screening. The first fake Gemini call fails with a network error.
    // Check the error message, captured photo, and location in the guide request.
    capturePhoto()
    val networkFailurePhoto = readCapturedPhoto()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.loading_screen_progress)
    val networkError = composeTestRule.activity.getString(R.string.loading_error_network)
    waitForText(networkError)
    composeTestRule.onNodeWithText(networkError).assertIsDisplayed()
    assertEquals(1, services.gemini.calls)
    assertEquals(2, services.imageScreen.calls)
    assertPhotoWasScreened(services, 1, networkFailurePhoto)
    assertEquals(1, services.locationProvider.calls)
    assertGuideRequestIncludedPreciseLocation(services, 0)

    // The second fake Gemini call waits until it is cancelled. Press Cancel on the loading screen
    // and check that the app returns to the camera and stops the request.
    composeTestRule.onNodeWithTag(C.Tag.loading_screen_cancel_button).performClick()
    waitAndAssertDisplayed(C.Tag.scan_camera_screen)
    capturePhoto()
    val cancelledPhoto = readCapturedPhoto()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.loading_screen_progress)
    composeTestRule.waitUntil { services.gemini.secondCallStarted.isCompleted }
    assertEquals(2, services.gemini.calls)
    assertPhotoWasScreened(services, 2, cancelledPhoto)
    assertGuideRequestIncludedPreciseLocation(services, 1)

    composeTestRule.onNodeWithTag(C.Tag.loading_screen_cancel_button).performClick()
    waitAndAssertDisplayed(C.Tag.scan_camera_screen)
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      services.gemini.secondCallCancelled.isCompleted
    }

    // Take a new photo and get a guide. Check the guide, photo, location, and number of service
    // calls so missing or repeated steps fail the test.
    capturePhoto()
    val firstGuidePhoto = readCapturedPhoto()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.monument_detail_screen_container)
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_picture).assertIsDisplayed()
    composeTestRule.onNodeWithText("Eiffel Tower").assertIsDisplayed()
    composeTestRule.onNodeWithText("Built in 1889.").assertIsDisplayed()
    assertEquals(3, services.gemini.calls)
    assertPhotoWasScreened(services, 3, firstGuidePhoto)
    assertEquals(3, services.locationProvider.calls)
    assertGuideRequestIncludedPreciseLocation(services, 2)

    // The guide's Back button should return to Home.
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).performClick()
    waitAndAssertDisplayed(C.Tag.home_screen_container)

    // Get another guide, then use Android's Back button to return to Home.
    enterCapture()
    capturePhoto()
    val secondGuidePhoto = readCapturedPhoto()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.monument_detail_screen_container)
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_picture).assertIsDisplayed()
    composeTestRule.onNodeWithText("Eiffel Tower").assertIsDisplayed()
    assertEquals(4, services.gemini.calls)
    assertPhotoWasScreened(services, 4, secondGuidePhoto)
    assertEquals(4, services.locationProvider.calls)
    assertGuideRequestIncludedPreciseLocation(services, 3)

    pressSystemBack()
    waitAndAssertDisplayed(C.Tag.home_screen_container)

    // Home is the first screen after sign-in. Android's Back button should close the app here.
    pressSystemBack()

    composeTestRule.waitUntil(timeoutMillis = 5_000) { composeTestRule.activity.isFinishing }
  }

  private fun enterCapture() {
    // Open the camera from Home. On the first visit, accept Android's camera permission prompt.
    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).performClick()
    if (!hasPermission(Manifest.permission.CAMERA)) {
      acceptPermissionDialog(
          resourceNames = listOf("permission_allow_button"),
          buttonLabels = listOf("Allow"),
      )
      assertPermissionGranted(Manifest.permission.CAMERA)
    }
    waitAndAssertDisplayed(C.Tag.scan_camera_screen)
  }

  private fun capturePhoto() {
    // Use the camera shutter and wait for the photo review screen.
    composeTestRule.onNodeWithTag(C.Tag.scan_shutter_button).performClick()
    waitAndAssertDisplayed(C.Tag.scan_review_screen)
  }

  private fun pressSystemBack() {
    // Send Android Back through the Activity so the current screen handles it.
    composeTestRule.runOnUiThread {
      composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
    }
    composeTestRule.waitForIdle()
  }

  private fun waitAndAssertDisplayed(tag: String) {
    // Wait for the screen to appear, then check that it is visible.
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
  }

  private fun waitForText(text: String) {
    // Wait for the loading flow to show its error message.
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun acceptPreciseLocationPermission() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val fineAccuracy =
        waitForPermissionObject(
            device,
            By.res(PERMISSION_CONTROLLER_PACKAGE, "permission_location_accuracy_radio_fine"),
        )
    if (!fineAccuracy.isChecked) {
      fineAccuracy.click()
    }
    assertTrue("Precise location must be selected in the system prompt", fineAccuracy.isChecked)
    acceptPermissionDialog(
        resourceNames =
            listOf("permission_allow_foreground_only_button", "permission_allow_button"),
        buttonLabels = listOf("While using the app", "Allow"),
    )
  }

  // Android uses different resource IDs on different versions and devices. Try known IDs and
  // button labels so the test can accept the permission prompt on each supported system image.
  private fun acceptPermissionDialog(resourceNames: List<String>, buttonLabels: List<String>) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val packageNames =
        listOf(
            PERMISSION_CONTROLLER_PACKAGE,
            "com.google.android.permissioncontroller",
            "com.google.android.packageinstaller",
        )
    val resourceSelectors = packageNames.flatMap { packageName ->
      resourceNames.map { By.res(packageName, it) }
    }
    val textSelectors =
        (buttonLabels + "While using the app" + "Only this time").distinct().map { By.text(it) }
    val selectors = textSelectors + resourceSelectors
    val selector =
        selectors.firstOrNull { device.wait(Until.hasObject(it), SELECTOR_TIMEOUT_MS) }
            ?: run {
              val hierarchy = File.createTempFile("permission-dialog", ".xml")
              try {
                device.dumpWindowHierarchy(hierarchy)
                error(
                    "Expected the Android runtime permission dialog to appear. " +
                        hierarchy.readText()
                )
              } finally {
                hierarchy.delete()
              }
            }
    checkNotNull(device.findObject(selector)) { "Permission dialog's Allow button disappeared" }
        .click()
  }

  private fun waitForPermissionObject(device: UiDevice, selector: BySelector): UiObject2 {
    check(device.wait(Until.hasObject(selector), PERMISSION_DIALOG_TIMEOUT_MS)) {
      "Expected Android location accuracy choice to appear"
    }
    return checkNotNull(device.findObject(selector)) { "Location accuracy choice disappeared" }
  }

  // Read permissions from Android's package manager, not from a test-only permission state.
  private fun assertPermissionGranted(permission: String) {
    assertEquals(PackageManager.PERMISSION_GRANTED, permissionResult(permission))
  }

  private fun hasPermission(permission: String): Boolean =
      permissionResult(permission) == PackageManager.PERMISSION_GRANTED

  private fun permissionResult(permission: String): Int =
      ApplicationProvider.getApplicationContext<Context>().checkSelfPermission(permission)

  private fun readCapturedPhoto(): ByteArray {
    val photo = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, CAPTURE_PATH)
    check(photo.isFile) { "Camera did not save a captured photo at ${photo.absolutePath}" }
    return photo.readBytes().also { check(it.isNotEmpty()) { "Captured photo is empty" } }
  }

  // The pipeline prepares the captured image before screening. Compare those bytes with the
  // bytes received by the fake screening service to check that the captured photo was sent.
  private fun assertPhotoWasScreened(
      services: FakeDiscoveryServices,
      imageIndex: Int,
      capturedBytes: ByteArray,
  ) {
    val preparedCapture = ImagePreparer().prepare(capturedBytes, "image/jpeg")
    val screenedPhoto = services.imageScreen.images[imageIndex]
    assertEquals("image/jpeg", screenedPhoto.mimeType)
    assertTrue(preparedCapture.bytes.contentEquals(screenedPhoto.bytes))
  }

  private fun assertGuideRequestIncludedPreciseLocation(
      services: FakeDiscoveryServices,
      promptIndex: Int,
  ) {
    val prompt = services.gemini.prompts[promptIndex]
    assertTrue(prompt.contains("latitude 48.858, longitude 2.294"))
  }

  private companion object {
    const val PERMISSION_CONTROLLER_PACKAGE = "com.android.permissioncontroller"
    const val PERMISSION_DIALOG_TIMEOUT_MS = 10_000L
    private const val SELECTOR_TIMEOUT_MS = 500L
    const val CAPTURE_PATH = "captures/capture.jpg"

    @BeforeClass
    @JvmStatic
    fun resetPermissionsBeforeActivityStarts() {
      // Start with no runtime grants and no saved location choice so Android shows real prompts.
      val context = ApplicationProvider.getApplicationContext<Context>()
      val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
      listOf(
              Manifest.permission.CAMERA,
              Manifest.permission.ACCESS_COARSE_LOCATION,
              Manifest.permission.ACCESS_FINE_LOCATION,
          )
          .forEach { permission ->
            uiAutomation.revokeRuntimePermission(context.packageName, permission)
          }
      SharedPreferencesLocationPermissionDecisionRepository(context)
          .saveDecision(LocationPermissionDecision.NOT_ASKED)
    }
  }
}

private class FakeDiscoveryServices {
  val gemini = ScriptedGeminiClient()
  // Fixed coordinates make the guide request predictable and protect the user's real location.
  val locationProvider = FakeLocationProvider()
  // Keep the real repository so the test covers image preparation and the full guide pipeline.
  // Its remote image screening, Gemini, and location services use the fakes below.
  val imageScreen = ScriptedImageScreen()
  private val repository =
      GuideRepositoryImpl(
          imagePreparer = ImagePreparer(),
          imageScreen = imageScreen,
          geminiClient = gemini,
          locationProvider = locationProvider,
          pause = {},
      )
  // The production sender reads the captured photo and passes it to the real repository.
  val captureSender: CaptureSender =
      GuideCaptureSender(
          context = ApplicationProvider.getApplicationContext(),
          guideRepository = repository,
      )
}

/**
 * Fake image-screening service.
 *
 * It rejects the first photo and accepts every later photo. This lets the journey test both the
 * not-a-monument message and guide generation.
 */
private class ScriptedImageScreen : ImageScreen {
  private val callCount = AtomicInteger()
  val images = java.util.concurrent.CopyOnWriteArrayList<ImagePayload>()
  val calls: Int
    get() = callCount.get()

  override suspend fun screen(image: ImagePayload): ScreenVerdict {
    // Save the image so the test can compare it with the photo captured by the camera.
    images += image
    val isMonument = callCount.incrementAndGet() > 1
    return ScreenVerdict(
        isMonument = isMonument,
        injectionSuspected = false,
        reason = if (isMonument) "test monument" else "no monument in test image",
    )
  }
}

/** Returns fixed coordinates and counts how often the guide pipeline requests them. */
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
 * Fake Gemini service with a fixed sequence of replies:
 * - the first call fails, to test the network error;
 * - the second call waits, to test cancellation;
 * - later calls return a guide for the Eiffel Tower.
 */
private class ScriptedGeminiClient : GeminiClient {
  private val callCount = AtomicInteger()
  val prompts = java.util.concurrent.CopyOnWriteArrayList<String>()
  private val cancellationGate = CompletableDeferred<Unit>()
  val secondCallStarted = CompletableDeferred<Unit>()
  val secondCallCancelled = CompletableDeferred<Unit>()
  val calls: Int
    get() = callCount.get()

  override suspend fun generate(
      prompt: String,
      image: ImagePayload,
  ): String =
      // Save each prompt so the test can check that it includes the fake precise location.
      when (callCount.incrementAndGet().also { prompts += prompt }) {
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

/** Counts taps on the sign-in button without opening Google's sign-in screen. */
private class FakeFirebaseSignIn {
  var signInCalls = 0
    private set

  fun signIn() {
    signInCalls++
  }
}

/** Makes the test fail if sign-in unexpectedly calls the authentication repository. */
private class FakeAuthRepository : AuthRepository {
  override suspend fun signInWithGoogle(
      credential: androidx.credentials.Credential
  ): Result<com.google.firebase.auth.FirebaseUser> =
      Result.failure(AssertionError("The fake sign-in override should handle this interaction"))

  override fun signOut(): Result<Unit> = Result.success(Unit)
}
