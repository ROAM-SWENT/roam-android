/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 * Co-authored-by: Mistral Vibe <vibe@mistral.ai>
 * Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
 */

package com.github.roamswent.roam

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
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
import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.friend.FriendRequest as RepositoryFriendRequest
import com.github.roamswent.roam.model.friend.Friendship
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.guide.SavedGuideRepository
import com.github.roamswent.roam.model.interests.SharedPreferencesUserInterestsRepository
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.model.location.LocationProvider
import com.github.roamswent.roam.model.permission.LocationPermissionDecision
import com.github.roamswent.roam.model.permission.SharedPreferencesLocationPermissionDecisionRepository
import com.github.roamswent.roam.model.profile.ProfileRepository
import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import com.github.roamswent.roam.model.profile.UserProfile
import com.github.roamswent.roam.model.theme.ThemeMode
import com.github.roamswent.roam.repository.CaptureSender
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.authentication.SignInViewModel
import com.github.roamswent.roam.ui.navigation.Routes
import com.github.roamswent.roam.ui.permission.LocationPermissionController
import com.github.roamswent.roam.ui.permission.LocationPermissionState
import com.github.roamswent.roam.ui.settings.SettingsViewModel
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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
  private var latestCapturedPhoto: File? = null
  private var galleryImageFile: File? = null
  // Set once the interests prompt is answered or loading is known to have started, so later
  // validate taps skip the prompt poll.
  private var interestsPromptHandled = false

  private fun captureFiles(): List<File> {
    val directory = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "captures")

    return directory.listFiles()?.filter {
      it.isFile && it.name.startsWith("capture_") && it.extension == "jpg"
    } ?: emptyList()
  }

  @After
  fun clearLocationPermissionDecision() {
    // Remove saved permission data so it cannot affect another test.
    ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("location_permission", Context.MODE_PRIVATE)
        .edit()
        .clear()
        .commit()
    galleryImageFile?.delete()
    galleryImageFile = null
  }

  @Test
  fun userCanExploreAllScreensAndManageGuidesInM2Journey() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val (galleryImageUri, galleryImageBytes) = createGalleryImage(context)
    val interestsRepository = SharedPreferencesUserInterestsRepository(context)
    interestsRepository.updateInterests(emptyList())
    val services =
        FakeDiscoveryServices(
            gemini =
                ScriptedGeminiClient(
                    failFirstCall = false,
                    cancelSecondCall = false,
                    guideReplies =
                        listOf(
                            """{"monumentName":"Eiffel Tower","confidence":0.92,"history":"Built in 1889.","facts":["It grew taller in summer."],"culture":"A symbol of Paris.","language":"en"}""",
                            """{"monumentName":"Louvre Museum","confidence":0.91,"history":"Opened as a museum in 1793.","facts":["It is the world's largest art museum."],"culture":"A landmark of Paris.","language":"en"}""",
                            """{"monumentName":"Arc de Triomphe","confidence":0.9,"history":"Completed in 1836.","facts":["It honors those who fought for France."],"culture":"A Parisian landmark.","language":"en"}""",
                        ),
                ),
            imageScreen = ScriptedImageScreen(rejectFirst = false),
        )
    val savedGuides = InMemorySavedGuideRepository()
    val fakeProfiles = FakePublicProfileRepository()
    val fakeFriends = FakeFriendRepository()
    val auth = FakeFirebaseSignIn()
    val authViewModel = SignInViewModel(FakeAuthRepository())
    val settingsViewModel =
        SettingsViewModel(
            authRepository = FakeAuthRepository(),
            profileRepository = FakeProfileRepository(),
            userInterestsRepository = interestsRepository,
        )

    composeTestRule.runOnUiThread {
      composeTestRule.activity.setContent {
        var selectedThemeMode by remember { mutableStateOf(ThemeMode.LIGHT) }
        SampleAppTheme(darkTheme = selectedThemeMode == ThemeMode.DARK) {
          val navController = androidx.navigation.compose.rememberNavController()
          RoamApp(
              authViewModel = authViewModel,
              navController = navController,
              captureSenderOverride = services.captureSender,
              galleryImageUriOverride = galleryImageUri,
              savedGuideRepositoryOverride = savedGuides,
              publicProfileRepositoryOverride = fakeProfiles,
              friendRepositoryOverride = fakeFriends,
              exploreLocationProviderOverride = services.locationProvider,
              exploreLocationPermissionControllerOverride = GrantedLocationPermissionController(),
              settingsViewModel = settingsViewModel,
              themeMode = selectedThemeMode,
              onThemeModeSelected = { selectedThemeMode = it },
              isOnlineOverride = true,
              // The M2 journey skips Firebase authentication and enters the real Home graph.
              onSignInClickOverride = { _ ->
                auth.signIn()
                navController.navigate(Routes.Home) { popUpTo(Routes.SignIn) { inclusive = true } }
              },
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

    // Save the first fake guide, show its scan location on Explore, and favourite it.
    enterCapture()
    capturePhoto()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    skipInterestsPromptIfShown()
    waitAndAssertDisplayed(C.Tag.monument_detail_screen_container)
    composeTestRule.onNodeWithText("Eiffel Tower").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_show_in_explore).performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      savedGuides.guides.singleOrNull()?.location == Coordinates(48.85837, 2.29448)
    }
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).performClick()
    waitAndAssertDisplayed(C.Tag.home_screen_container)

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).performClick()
    waitAndAssertDisplayed(C.Tag.collection_filter_all)
    composeTestRule.onNodeWithText("Eiffel Tower").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.collection_card_favorite).performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      savedGuides.guides.singleOrNull()?.isFavorite == true
    }
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).performClick()
    waitAndAssertDisplayed(C.Tag.home_screen_container)

    // Update an interest, switch to dark mode and back to light mode in Profile.
    composeTestRule.onNodeWithTag(C.Tag.home_profile_button).performClick()
    waitAndAssertDisplayed(C.Tag.settings_screen_container)
    composeTestRule.onNodeWithTag(C.Tag.settings_interests_card).performScrollTo()
    composeTestRule.onNodeWithTag(C.Tag.settings_add_interest_button).performClick()
    composeTestRule.onNodeWithTag(C.Tag.settings_interest_option_prefix + "History").performClick()
    composeTestRule.onNodeWithText("History").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.settings_theme_card).performScrollTo()
    composeTestRule.onNodeWithTag(C.Tag.settings_theme_dark).performClick()
    composeTestRule.onNodeWithTag(C.Tag.settings_theme_dark).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.settings_theme_light).performClick()
    composeTestRule.onNodeWithTag(C.Tag.settings_theme_light).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).performClick()
    waitAndAssertDisplayed(C.Tag.home_screen_container)

    // Discover a second fake guide and add its scan location to Explore as well.
    enterCapture()
    capturePhoto()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    skipInterestsPromptIfShown()
    waitAndAssertDisplayed(C.Tag.monument_detail_screen_container)
    composeTestRule.onNodeWithText("Louvre Museum").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_show_in_explore).performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      savedGuides.guides.size == 2 && savedGuides.guides.all { it.location != null }
    }
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).performClick()
    waitAndAssertDisplayed(C.Tag.home_screen_container)

    // Send a synthetic gallery photo through the bottom-left picker button.
    enterCapture()
    composeTestRule.onNodeWithTag(C.Tag.scan_gallery_button).performClick()
    waitAndAssertDisplayed(C.Tag.scan_review_screen)
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    skipInterestsPromptIfShown()
    waitAndAssertDisplayed(C.Tag.monument_detail_screen_container)
    composeTestRule.onNodeWithText("Arc de Triomphe").assertIsDisplayed()
    assertPhotoWasScreened(services, 2, galleryImageBytes)
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_show_in_explore).performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      savedGuides.guides.size == 3 && savedGuides.guides.all { it.location != null }
    }
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).performClick()
    waitAndAssertDisplayed(C.Tag.home_screen_container)

    // All shows three guides; Favourites shows only the first guide.
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).performClick()
    waitAndAssertDisplayed(C.Tag.collection_filter_all)
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("Eiffel Tower").fetchSemanticsNodes().isNotEmpty() &&
          composeTestRule.onAllNodesWithText("Louvre Museum").fetchSemanticsNodes().isNotEmpty() &&
          composeTestRule.onAllNodesWithText("Arc de Triomphe").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag(C.Tag.collection_filter_favorites).performClick()
    composeTestRule.onNodeWithText("Eiffel Tower").assertIsDisplayed()
    assertTrue(composeTestRule.onAllNodesWithText("Louvre Museum").fetchSemanticsNodes().isEmpty())
    assertTrue(
        composeTestRule.onAllNodesWithText("Arc de Triomphe").fetchSemanticsNodes().isEmpty()
    )
    composeTestRule.onNodeWithTag(C.Tag.collection_filter_all).performClick()
    composeTestRule.onNodeWithText("Eiffel Tower").assertIsDisplayed()
    composeTestRule.onNodeWithText("Louvre Museum").assertIsDisplayed()
    composeTestRule.onNodeWithText("Arc de Triomphe").assertIsDisplayed()

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).performClick()
    waitAndAssertDisplayed(C.Tag.home_screen_container)
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).performClick()
    waitAndAssertDisplayed(C.Tag.explore_map)
    assertEquals(3, savedGuides.guides.count { it.location == Coordinates(48.85837, 2.29448) })

    // Search for Micheal Jordan without opening his profile or sending a request.
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).performClick()
    waitAndAssertDisplayed(C.Tag.home_screen_container)
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_friends).performClick()
    waitAndAssertDisplayed(C.Tag.friends_screen_container)
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("Taylor Reed").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("Taylor Reed").assertIsDisplayed()

    // The existing friendship is loaded independently of profile search.
    composeTestRule.onNodeWithTag(C.Tag.friends_add_button).performClick()
    composeTestRule.onNodeWithTag(C.Tag.friends_user_search).performTextInput("Mich")
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("Micheal Jordan").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("Micheal Jordan").assertIsDisplayed()

    // Send to a synthetic profile; the fake repository records the request without contacting
    // anyone.
    composeTestRule.onNodeWithTag(C.Tag.friends_user_search).performTextReplacement("Journey")
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("Journey Test User").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag(C.Tag.friends_add_user_button).performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule
          .onAllNodesWithTag(C.Tag.friends_cancel_request)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    assertEquals("journey-request-target", fakeFriends.sentRequests.single().recipientUid)

    composeTestRule.onNodeWithTag(C.Tag.friends_add_dismiss).performClick()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).performClick()
    waitAndAssertDisplayed(C.Tag.home_screen_container)
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
              savedGuideRepositoryOverride = FakeSavedGuideRepository(),
              // Simulate successful sign-in without Firebase, then request permission through
              // the app's real Android permission controller.
              onSignInClickOverride = { onSignedIn ->
                auth.signIn()
                onSignedIn()
              },
              onSignedInOverride = { it.requestPermission() },
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
    acceptPermissionDialogIfVisible()
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      locationPermissionDecisions.getDecision() == LocationPermissionDecision.GRANTED
    }
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
    skipInterestsPromptIfShown()
    val notMonumentError = composeTestRule.activity.getString(R.string.loading_error_not_identified)
    waitForLoadingTerminalState(notMonumentError)
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
    skipInterestsPromptIfShown()
    val networkError = composeTestRule.activity.getString(R.string.loading_error_network)
    waitForLoadingTerminalState(networkError)
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
    skipInterestsPromptIfShown()
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      services.gemini.secondCallStarted.isCompleted
    }
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
    skipInterestsPromptIfShown()
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
    skipInterestsPromptIfShown()
    waitAndAssertDisplayed(C.Tag.monument_detail_screen_container)
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_picture).assertIsDisplayed()
    composeTestRule.onNodeWithText("Eiffel Tower").assertIsDisplayed()
    assertEquals(4, services.gemini.calls)
    assertPhotoWasScreened(services, 4, secondGuidePhoto)
    assertEquals(4, services.locationProvider.calls)
    assertGuideRequestIncludedPreciseLocation(services, 3)

    pressSystemBack()
    waitAndAssertDisplayed(C.Tag.home_screen_container)

    // The dock reaches the collection and explore screens, and the Home tab returns.
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).performClick()
    waitAndAssertDisplayed(C.Tag.collection_screen)
    composeTestRule.onNodeWithTag(C.Tag.dock_container).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).performClick()
    waitAndAssertDisplayed(C.Tag.explore_screen_container)
    composeTestRule.onNodeWithTag(C.Tag.dock_container).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).performClick()
    waitAndAssertDisplayed(C.Tag.home_screen_container)

    // Home is the first screen after sign-in. Android's Back button should close the app here.
    pressSystemBack()

    composeTestRule.waitUntil(timeoutMillis = 5_000) { composeTestRule.activity.isFinishing }
  }

  private fun enterCapture() {
    // Open the camera from Home. On the first visit, accept Android's camera permission prompt.
    composeTestRule.onNodeWithTag(C.Tag.dock_scan_button).performClick()
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
    val existingFiles = captureFiles().toSet()

    composeTestRule.onNodeWithTag(C.Tag.scan_shutter_button).performClick()
    waitAndAssertDisplayed(C.Tag.scan_review_screen)

    val newFiles = captureFiles().filterNot { it in existingFiles }

    check(newFiles.size == 1) { "Expected exactly one new capture, found ${newFiles.size}" }

    latestCapturedPhoto = newFiles.single()
  }

  private fun createGalleryImage(context: Context): Pair<Uri, ByteArray> {
    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    try {
      bitmap.eraseColor(0xFF3F7CAC.toInt())
      val file = File.createTempFile("journey-gallery-", ".jpg", context.cacheDir)
      file.outputStream().use { output ->
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
          "Could not encode the fake gallery image"
        }
      }
      galleryImageFile = file
      return Uri.fromFile(file) to file.readBytes()
    } finally {
      bitmap.recycle()
    }
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

  // A fresh install answers the real first-run interests prompt on the first validate tap,
  // before the loading screen may start. Poll briefly and tap the prompt's Skip action when it
  // appears; when the prompt is absent the journey continues immediately without failing. Once
  // the prompt is answered, later validate taps return immediately instead of polling again.
  private fun skipInterestsPromptIfShown() {
    if (interestsPromptHandled) {
      return
    }
    val deadline = System.currentTimeMillis() + INTERESTS_PROMPT_POLL_MS
    while (System.currentTimeMillis() < deadline) {
      if (
          composeTestRule
              .onAllNodesWithTag(C.Tag.scan_interests_prompt)
              .fetchSemanticsNodes()
              .isNotEmpty()
      ) {
        composeTestRule.onNodeWithTag(C.Tag.scan_interests_skip).performClick()
        interestsPromptHandled = true
        return
      }
      Thread.sleep(INTERESTS_PROMPT_POLL_INTERVAL_MS)
    }
  }

  // Under the fake, the screening stage lasts 150-470 ms, so the progress view is a transient
  // that is never asserted. Wait for the loading flow's terminal state instead: the expected
  // error text or the monument detail screen. A terminal state also proves that loading
  // started, so the interests prompt cannot appear anymore.
  private fun waitForLoadingTerminalState(vararg terminalTexts: String) {
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      val errorShown = terminalTexts.any { text ->
        composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
      }
      val monumentShown =
          composeTestRule
              .onAllNodesWithTag(C.Tag.monument_detail_screen_container)
              .fetchSemanticsNodes()
              .isNotEmpty()
      errorShown || monumentShown
    }
    interestsPromptHandled = true
  }

  private fun acceptPreciseLocationPermission() {
    // Android may already have granted precise access, in which case it skips the prompt
    // altogether. A granted fine-location permission proves the selected precision.
    if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
      assertPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
      return
    }

    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val packageNames =
        listOf(
            PERMISSION_CONTROLLER_PACKAGE,
            "com.google.android.permissioncontroller",
        )
    // Permission-controller resource IDs vary by Android image; labels provide a fallback.
    val precisionSelectors =
        packageNames.map { By.res(it, "permission_location_accuracy_radio_fine") } +
            listOf(
                    "Precise",
                    "Precise location",
                    "Use precise location",
                    "Precise accuracy",
                )
                .map(By::text)
    val precisionSelector =
        waitForPermissionSelector(device, precisionSelectors)
            ?: run {
              val hierarchy = File.createTempFile("location-permission-dialog", ".xml")
              try {
                device.dumpWindowHierarchy(hierarchy)
                error(
                    "Expected the Android precise-location choice to appear. " +
                        "Location permission state: " +
                        "coarse=${hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)}, " +
                        "fine=${hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)}. " +
                        hierarchy.readText()
                )
              } finally {
                hierarchy.delete()
              }
            }
    val fineAccuracy =
        checkNotNull(device.findObject(precisionSelector)) {
          "Location accuracy choice disappeared"
        }
    if (!fineAccuracy.isChecked) {
      fineAccuracy.click()
    }
    assertTrue(
        "Precise location must be selected in the system prompt",
        checkNotNull(device.findObject(precisionSelector)).isChecked,
    )
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
        waitForPermissionSelector(device, selectors)
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

  private fun acceptPermissionDialogIfVisible() {
    // Granting location can immediately trigger Android's notification prompt, which also
    // blocks Compose UI inspection until it is dismissed.
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val selectors =
        listOf(
            By.res(PERMISSION_CONTROLLER_PACKAGE, "permission_allow_button"),
            By.res("com.google.android.permissioncontroller", "permission_allow_button"),
            By.text("Allow"),
        )
    waitForPermissionSelector(device, selectors, timeoutMillis = 1_000L)?.let { selector ->
      device.findObject(selector)?.click()
    }
  }

  // The system dialog can appear later than one pass over the selectors allows, so retry the
  // whole pass until the permission dialog timeout instead of a single 3 s pass.
  private fun waitForPermissionSelector(
      device: UiDevice,
      selectors: List<BySelector>,
      timeoutMillis: Long = PERMISSION_DIALOG_TIMEOUT_MS,
  ): BySelector? {
    val deadline = System.currentTimeMillis() + timeoutMillis
    do {
      val found = selectors.firstOrNull { device.wait(Until.hasObject(it), SELECTOR_TIMEOUT_MS) }
      if (found != null) {
        return found
      }
    } while (System.currentTimeMillis() < deadline)
    return null
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
    val photo = checkNotNull(latestCapturedPhoto) { "No captured photo was recorded" }

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
    private const val INTERESTS_PROMPT_POLL_MS = 2_000L
    private const val INTERESTS_PROMPT_POLL_INTERVAL_MS = 50L

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

private class GrantedLocationPermissionController : LocationPermissionController {
  override val state =
      MutableStateFlow(
          LocationPermissionState(
              isGranted = true,
              decision = LocationPermissionDecision.GRANTED,
          )
      )

  override fun hasPermission(): Boolean = true

  override fun refreshPermissionState() = Unit

  override fun requestPermission() = Unit
}

private class FakeDiscoveryServices(
    val gemini: ScriptedGeminiClient = ScriptedGeminiClient(),
    val imageScreen: ScriptedImageScreen = ScriptedImageScreen(),
) {
  // Fixed coordinates make the guide request predictable and protect the user's real location.
  val locationProvider = FakeLocationProvider()
  // Keep the real repository so the test covers image preparation and the full guide pipeline.
  // Its remote image screening, Gemini, and location services use the fakes below.
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
          userInterestsRepository =
              SharedPreferencesUserInterestsRepository(ApplicationProvider.getApplicationContext()),
      )
}

/**
 * Fake image-screening service.
 *
 * It rejects the first photo and accepts every later photo. This lets the journey test both the
 * not-a-monument message and guide generation.
 */
private class ScriptedImageScreen(private val rejectFirst: Boolean = true) : ImageScreen {
  private val callCount = AtomicInteger()
  val images = java.util.concurrent.CopyOnWriteArrayList<ImagePayload>()
  val calls: Int
    get() = callCount.get()

  override suspend fun screen(image: ImagePayload): ScreenVerdict {
    // Save the image so the test can compare it with the photo captured by the camera.
    images += image
    val call = callCount.incrementAndGet()
    val isMonument = !rejectFirst || call > 1
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
private class ScriptedGeminiClient(
    private val failFirstCall: Boolean = true,
    private val cancelSecondCall: Boolean = true,
    private val guideReplies: List<String> = listOf(GUIDE_REPLY),
) : GeminiClient {
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
  ): String {
    // Save each prompt so the test can check that it includes the fake precise location.
    val call = callCount.incrementAndGet().also { prompts += prompt }
    if (failFirstCall && call == 1) {
      throw NetworkUnavailableException()
    }
    if (cancelSecondCall && call == 2) {
      secondCallStarted.complete(Unit)

      try {
        cancellationGate.await()
      } catch (e: CancellationException) {
        secondCallCancelled.complete(Unit)
        throw e
      }
    }
    val successfulCall = call - if (failFirstCall) 1 else 0
    return guideReplies.getOrElse(successfulCall - 1) { guideReplies.last() }
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

  override fun currentUser(): com.google.firebase.auth.FirebaseUser? = null

  override fun currentUserFlow():
      kotlinx.coroutines.flow.Flow<com.google.firebase.auth.FirebaseUser?> =
      kotlinx.coroutines.flow.flowOf(null)

  override fun signOut(): Result<Unit> = Result.success(Unit)
}

private class FakeProfileRepository : ProfileRepository {
  override suspend fun loadProfile(): Result<UserProfile> =
      Result.success(UserProfile("Roam User", "roam@example.com", "Google"))
}

private class FakePublicProfileRepository : PublicProfileRepository {
  private val currentUser = PublicProfile("journey-user", "RoamUser")
  private val existingFriend = PublicProfile("existing-friend", "Taylor Reed")
  private val searchUser = PublicProfile("micheal-jordan", "Micheal Jordan")
  private val requestTarget = PublicProfile("journey-request-target", "Journey Test User")

  override suspend fun getCurrentProfile(): PublicProfile = currentUser

  override suspend fun getProfile(uid: String): PublicProfile? =
      listOf(currentUser, existingFriend, searchUser, requestTarget).firstOrNull { it.uid == uid }

  override suspend fun registerUsername(username: String): PublicProfile =
      currentUser.copy(username = username)

  override suspend fun renameUsername(username: String): PublicProfile =
      currentUser.copy(username = username)

  override suspend fun searchProfiles(prefix: String): List<PublicProfile> =
      listOf(searchUser, requestTarget).filter { it.username.startsWith(prefix, ignoreCase = true) }
}

private class FakeFriendRepository : FriendRepository {
  private val outgoingRequests = mutableListOf<RepositoryFriendRequest>()
  val sentRequests: List<RepositoryFriendRequest>
    get() = outgoingRequests.toList()

  override suspend fun sendFriendRequest(toUid: String): RepositoryFriendRequest =
      RepositoryFriendRequest("journey-request-$toUid", "journey-user", toUid).also {
        outgoingRequests.add(it)
      }

  override suspend fun acceptFriendRequest(requestId: String) = Unit

  override suspend fun rejectFriendRequest(requestId: String) = Unit

  override suspend fun cancelFriendRequest(requestId: String) {
    outgoingRequests.removeAll { it.id == requestId }
  }

  override suspend fun getIncomingRequests(): List<RepositoryFriendRequest> = emptyList()

  override suspend fun getOutgoingRequests(): List<RepositoryFriendRequest> =
      outgoingRequests.toList()

  override suspend fun getFriends(): List<Friendship> =
      listOf(Friendship("existing-friendship", "existing-friend"))

  override suspend fun removeFriend(friendUid: String) = Unit
}

/** In-memory collection used by the M2 journey instead of Firestore. */
private class InMemorySavedGuideRepository : SavedGuideRepository {
  private val _guides = MutableStateFlow<List<Guide>>(emptyList())
  val guides: List<Guide>
    get() = _guides.value

  override suspend fun addGuide(guide: Guide): Guide {
    _guides.update { current -> listOf(guide) + current.filterNot { it.id == guide.id } }
    return guide
  }

  override suspend fun removeGuide(guideId: String) {
    _guides.update { guides -> guides.filterNot { it.id == guideId } }
  }

  override suspend fun setFavorite(guideId: String, isFavorite: Boolean) {
    _guides.update { guides ->
      guides.map { guide ->
        if (guide.id == guideId) guide.copy(isFavorite = isFavorite) else guide
      }
    }
  }

  override suspend fun setLocation(guideId: String, location: Coordinates?) {
    _guides.update { guides ->
      guides.map { guide -> if (guide.id == guideId) guide.copy(location = location) else guide }
    }
  }

  override suspend fun getSavedGuides(): List<Guide> = _guides.value

  override fun observeSavedGuides() = _guides

  override suspend fun getGuide(guideId: String): Guide? =
      _guides.value.firstOrNull { it.id == guideId }
}

/** Empty in-memory collection for the journey that only smoke-tests collection navigation. */
private class FakeSavedGuideRepository : SavedGuideRepository {
  override suspend fun addGuide(guide: Guide): Guide = guide

  override suspend fun removeGuide(guideId: String) = Unit

  override suspend fun setFavorite(guideId: String, isFavorite: Boolean) = Unit

  override suspend fun setLocation(guideId: String, location: Coordinates?) = Unit

  override suspend fun getSavedGuides(): List<Guide> = emptyList()

  override fun observeSavedGuides() = MutableStateFlow<List<Guide>>(emptyList())

  override suspend fun getGuide(guideId: String): Guide? = null
}
