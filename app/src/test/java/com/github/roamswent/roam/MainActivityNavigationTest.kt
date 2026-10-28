/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.github.roamswent.roam.model.permission.LocationPermissionDecision
import com.github.roamswent.roam.model.permission.SharedPreferencesLocationPermissionDecisionRepository
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.authentication.AuthUIState
import com.github.roamswent.roam.ui.authentication.SignInViewModel
import com.github.roamswent.roam.ui.navigation.Routes
import com.github.roamswent.roam.ui.settings.SettingsUIState
import com.github.roamswent.roam.ui.settings.SettingsViewModel
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MainActivityNavigationTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val locationDecisionRepository =
      SharedPreferencesLocationPermissionDecisionRepository(
          ApplicationProvider.getApplicationContext()
      )
  private val locationPermissions =
      arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

  @Before
  fun configureCameraAvailability() {
    val application: Application = ApplicationProvider.getApplicationContext()
    shadowOf(application.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, true)
    shadowOf(application).denyPermissions(Manifest.permission.CAMERA, *locationPermissions)
    application
        .getSharedPreferences("location_permission", Context.MODE_PRIVATE)
        .edit()
        .clear()
        .commit()
  }

  @After
  fun clearLocationDecision() {
    (ApplicationProvider.getApplicationContext() as Application)
        .getSharedPreferences("location_permission", Context.MODE_PRIVATE)
        .edit()
        .clear()
        .commit()
  }

  @Test
  fun startsAtSignInWithoutAccessToHomeOrScan() {
    renderApp(MutableStateFlow(AuthUIState()))
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.sign_in_title))
        .assertIsDisplayed()
    waitForGone(C.Tag.home_screen_container)
    waitForGone(C.Tag.scan_camera_screen)
  }

  @Test
  fun firstSuccessfulSignInRequestsLocationAndGrantNavigatesToHome() {
    val authState = MutableStateFlow(AuthUIState())
    renderApp(authState)

    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.sign_in_title))
        .assertIsDisplayed()
    waitForGone(C.Tag.home_screen_container)

    authState.value = AuthUIState(user = mock())

    val request = waitForLocationPermissionRequest()
    assertEquals(locationPermissions.toSet(), request.requestedPermissions.toSet())
    waitForGone(C.Tag.home_screen_container)
    dispatchLocationPermissionResult(request, granted = true)

    waitAndAssertDisplayed(C.Tag.home_screen_container)
    waitForGone(C.Tag.scan_camera_screen)
    assertEquals(LocationPermissionDecision.GRANTED, locationDecisionRepository.getDecision())
  }

  @Test
  fun signInClickOverrideRunsAndNavigatesToHome() {
    locationDecisionRepository.saveDecision(LocationPermissionDecision.DENIED)
    val authViewModel = mock<SignInViewModel>()
    whenever(authViewModel.uiState).thenReturn(MutableStateFlow(AuthUIState()))
    var signInClickCount = 0

    composeTestRule.activity.setContent {
      SampleAppTheme {
        RoamApp(
            authViewModel = authViewModel,
            onSignInClickOverride = { signInClickCount++ },
        )
      }
    }

    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.sign_in_google_button))
        .performClick()

    assertEquals(1, signInClickCount)
    waitAndAssertDisplayed(C.Tag.home_screen_container)
    assertNull(shadowOf(composeTestRule.activity).lastRequestedPermission)
  }

  @Test
  fun firstSuccessfulSignInWithPreGrantedLocationNavigatesAndPersistsGrant() {
    shadowOf(composeTestRule.activity).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    val authState = MutableStateFlow(AuthUIState())
    renderApp(authState)

    authState.value = AuthUIState(user = mock())

    waitAndAssertDisplayed(C.Tag.home_screen_container)
    assertNull(shadowOf(composeTestRule.activity).lastRequestedPermission)
    assertEquals(LocationPermissionDecision.GRANTED, locationDecisionRepository.getDecision())
  }

  @Test
  fun firstSuccessfulSignInNavigatesToHomeAndPersistsLocationDenial() {
    val authState = MutableStateFlow(AuthUIState())
    renderApp(authState)

    authState.value = AuthUIState(user = mock())

    val request = waitForLocationPermissionRequest()
    dispatchLocationPermissionResult(request, granted = false)

    waitAndAssertDisplayed(C.Tag.home_screen_container)
    assertEquals(LocationPermissionDecision.DENIED, locationDecisionRepository.getDecision())
  }

  @Test
  fun persistedGrantNavigatesDirectlyToHomeWithoutRequestingLocation() {
    signInWithPersistedLocationDecision(LocationPermissionDecision.GRANTED)
  }

  @Test
  fun persistedDenialNavigatesDirectlyToHomeWithoutRequestingLocation() {
    signInWithPersistedLocationDecision(LocationPermissionDecision.DENIED)
  }

  @Test
  fun managePermissionsOpensApplicationSettings() {
    lateinit var navController: NavHostController
    showAuthenticatedHome(onNavControllerReady = { navController = it })

    navigateToSettings(navController)
    composeTestRule.onNodeWithText("Manage Permissions").performClick()

    val intent = shadowOf(composeTestRule.activity).nextStartedActivity
    assertNotNull(intent)
    assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
    assertEquals(
        composeTestRule.activity.packageName,
        intent.data?.schemeSpecificPart,
    )
  }

  @Test
  fun signedOutStateReturnsToSignInWithoutAutomaticallySigningInAgain() {
    locationDecisionRepository.saveDecision(LocationPermissionDecision.DENIED)
    val authState = MutableStateFlow(AuthUIState(user = mock()))
    val settingsState = MutableStateFlow(SettingsUIState())
    val settingsViewModel = mock<SettingsViewModel>()
    whenever(settingsViewModel.uiState).thenReturn(settingsState)
    doAnswer {
          settingsState.value = settingsState.value.copy(signedOut = true)
          Unit
        }
        .whenever(settingsViewModel)
        .signOut(any<CredentialManager>())
    var signInClickCount = 0
    lateinit var navController: NavHostController
    renderApp(
        authState,
        onSignInClickOverride = { signInClickCount++ },
        onNavControllerReady = { navController = it },
        settingsViewModel = settingsViewModel,
    )
    waitAndAssertDisplayed(C.Tag.home_screen_container)

    navigateToSettings(navController)
    composeTestRule.waitUntil {
      composeTestRule.onAllNodesWithText("Log Out").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("Log Out").performClick()
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.sign_in_title))
        .assertIsDisplayed()
    waitForGone(C.Tag.home_screen_container)
    composeTestRule.waitForIdle()

    assertEquals(AuthUIState(signedOut = true), authState.value)
    assertEquals(0, signInClickCount)
  }

  @Test
  fun loginCanBeStartedAgainFromSignedOutState() {
    locationDecisionRepository.saveDecision(LocationPermissionDecision.DENIED)
    val authState = MutableStateFlow(AuthUIState(user = mock()))
    var signInClickCount = 0
    lateinit var navController: NavHostController
    renderApp(
        authState,
        onSignInClickOverride = {
          signInClickCount++
          authState.value = AuthUIState(user = mock())
        },
        onNavControllerReady = { navController = it },
    )
    waitAndAssertDisplayed(C.Tag.home_screen_container)

    navigateToSettings(navController)
    authState.value = AuthUIState(signedOut = true)
    composeTestRule.runOnIdle {
      navController.navigate(Routes.SignIn) { popUpTo(navController.graph.id) { inclusive = true } }
    }
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.sign_in_title))
        .assertIsDisplayed()

    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.sign_in_google_button))
        .performClick()

    assertEquals(1, signInClickCount)
    waitAndAssertDisplayed(C.Tag.home_screen_container)
  }

  @Test
  fun revokedPersistedGrantStillNavigatesWithoutRequestingLocation() {
    locationDecisionRepository.saveDecision(LocationPermissionDecision.GRANTED)
    val authState = MutableStateFlow(AuthUIState())
    renderApp(authState)

    authState.value = AuthUIState(user = mock())

    waitAndAssertDisplayed(C.Tag.home_screen_container)
    assertNull(shadowOf(composeTestRule.activity).lastRequestedPermission)
    assertEquals(LocationPermissionDecision.GRANTED, locationDecisionRepository.getDecision())
  }

  @Test
  fun backFromScanReturnsToAuthenticatedHome() {
    shadowOf(composeTestRule.activity).grantPermissions(Manifest.permission.CAMERA)
    showAuthenticatedHome()

    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).performClick()
    waitAndAssertDisplayed(C.Tag.scan_camera_screen)

    composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

    waitAndAssertDisplayed(C.Tag.home_screen_container)
  }

  @Test
  fun scanEntryNavigatesToScanDestination() {
    showAuthenticatedHome()
    shadowOf(composeTestRule.activity).grantPermissions(Manifest.permission.CAMERA)
    waitFor(C.Tag.home_scan_button)
    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).performClick()

    waitAndAssertDisplayed(C.Tag.scan_camera_screen)
    waitForGone(C.Tag.home_screen_container)
  }

  @Test
  fun denyShowsPermissionDialogWithoutNavigating() {
    showAuthenticatedHome()
    waitFor(C.Tag.home_scan_button)
    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).performClick()
    dispatchCameraPermissionResult()

    waitAndAssertDisplayed(C.Tag.camera_permission_dialog)
    waitForGone(C.Tag.scan_camera_screen)
    composeTestRule.onNodeWithTag(C.Tag.camera_permission_settings_button).assertIsDisplayed()
  }

  @Test
  fun dismissingPermissionDialogLeavesHomeDisplayed() {
    showAuthenticatedHome()
    waitFor(C.Tag.home_scan_button)
    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).performClick()
    dispatchCameraPermissionResult()
    waitAndAssertDisplayed(C.Tag.camera_permission_dialog)

    composeTestRule.onNodeWithTag(C.Tag.camera_permission_dismiss_button).performClick()

    waitForGone(C.Tag.camera_permission_dialog)
    waitForGone(C.Tag.scan_camera_screen)
    waitAndAssertDisplayed(C.Tag.home_screen_container)
  }

  @Test
  fun denialAfterDismissShowsDialogAgain() {
    showAuthenticatedHome()
    waitFor(C.Tag.home_scan_button)
    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).performClick()
    val firstPermissionRequest = dispatchCameraPermissionResult()
    waitAndAssertDisplayed(C.Tag.camera_permission_dialog)
    waitForGone(C.Tag.scan_camera_screen)

    composeTestRule.onNodeWithTag(C.Tag.camera_permission_dismiss_button).performClick()
    waitForGone(C.Tag.camera_permission_dialog)
    composeTestRule.onAllNodesWithTag(C.Tag.camera_permission_dialog).assertCountEquals(0)
    waitAndAssertDisplayed(C.Tag.home_screen_container)
    waitForGone(C.Tag.scan_camera_screen)

    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).performClick()
    val secondPermissionRequest = permissionRequest()
    assertNotSame(firstPermissionRequest, secondPermissionRequest)
    dispatchCameraPermissionResult(secondPermissionRequest)

    waitAndAssertDisplayed(C.Tag.camera_permission_dialog)
    waitForGone(C.Tag.scan_camera_screen)
  }

  @Test
  fun unavailableCameraDoesNotRequestPermissionOrNavigate() {
    val application: Application = ApplicationProvider.getApplicationContext()
    shadowOf(application.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, false)
    showAuthenticatedHome()
    waitFor(C.Tag.home_scan_button)

    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).performClick()

    waitForGone(C.Tag.camera_permission_dialog)
    waitForGone(C.Tag.scan_camera_screen)
    assertNull(shadowOf(composeTestRule.activity).lastRequestedPermission)
  }

  @Test
  fun unavailableCameraDisablesScanButton() {
    val application: Application = ApplicationProvider.getApplicationContext()
    shadowOf(application.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, false)
    showAuthenticatedHome()
    waitFor(C.Tag.home_scan_button)

    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).assertIsNotEnabled()
  }

  @Test
  fun openingSettingsFromPermissionDialog() {
    showAuthenticatedHome()
    waitFor(C.Tag.home_scan_button)

    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).performClick()

    dispatchCameraPermissionResult()
    waitAndAssertDisplayed(C.Tag.camera_permission_dialog)

    composeTestRule.onNodeWithTag(C.Tag.camera_permission_settings_button).performClick()

    waitForGone(C.Tag.camera_permission_dialog)
    waitAndAssertDisplayed(C.Tag.home_screen_container)

    val intent = shadowOf(composeTestRule.activity).nextStartedActivity

    assertNotNull(intent)
    assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
    assertEquals(
        composeTestRule.activity.packageName,
        intent.data?.schemeSpecificPart,
    )
  }

  @Test
  fun scanReviewWithUriDisplaysReviewScreen() {
    locationDecisionRepository.saveDecision(LocationPermissionDecision.DENIED)
    val authViewModel = mock<SignInViewModel>()
    whenever(authViewModel.uiState).thenReturn(MutableStateFlow(AuthUIState(user = mock())))

    lateinit var navController: NavHostController

    composeTestRule.activity.setContent {
      val controller = rememberNavController()
      SideEffect { navController = controller }

      SampleAppTheme { RoamApp(authViewModel, controller) }
    }

    waitAndAssertDisplayed(C.Tag.home_screen_container)

    composeTestRule.runOnIdle {
      navController.navigate("${Routes.ScanReview}?uri=${Uri.encode("content://test/photo.jpg")}")
    }

    waitAndAssertDisplayed(C.Tag.scan_review_screen)

    composeTestRule.runOnIdle { navController.navigate(Routes.ScanReview) }

    waitForGone(C.Tag.scan_review_screen)

    composeTestRule.runOnIdle {
      assertNull(navController.currentBackStackEntry?.arguments?.getString("uri"))
    }
  }

  @Test
  fun mainActivityCreatesComposeContent() {
    val authViewModel = mock<SignInViewModel>()
    whenever(authViewModel.uiState).thenReturn(MutableStateFlow(AuthUIState()))

    val controller = Robolectric.buildActivity(MainActivity::class.java).create()

    try {
      ViewModelProvider(
          controller.get(),
          object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
              return authViewModel as T
            }
          },
      )[SignInViewModel::class.java]

      controller.start().resume().visible()

      waitAndAssertDisplayed(C.Tag.main_screen_container)
    } finally {
      controller.pause().stop().destroy()
    }
  }

  private fun permissionRequest(): org.robolectric.shadows.ShadowActivity.PermissionsRequest {
    return checkNotNull(shadowOf(composeTestRule.activity).lastRequestedPermission)
  }

  private fun waitForLocationPermissionRequest():
      org.robolectric.shadows.ShadowActivity.PermissionsRequest {
    composeTestRule.waitUntil {
      shadowOf(composeTestRule.activity)
          .lastRequestedPermission
          ?.requestedPermissions
          ?.contains(Manifest.permission.ACCESS_FINE_LOCATION) == true
    }
    return permissionRequest()
  }

  private fun dispatchLocationPermissionResult(
      request: org.robolectric.shadows.ShadowActivity.PermissionsRequest,
      granted: Boolean,
  ) {
    if (granted) {
      shadowOf(composeTestRule.activity)
          .grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    } else {
      shadowOf(composeTestRule.activity).denyPermissions(*locationPermissions)
    }
    val result =
        if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
    composeTestRule.activity.onRequestPermissionsResult(
        request.requestCode,
        locationPermissions,
        intArrayOf(result, result),
    )
  }

  private fun signInWithPersistedLocationDecision(decision: LocationPermissionDecision) {
    locationDecisionRepository.saveDecision(decision)
    val authState = MutableStateFlow(AuthUIState())
    renderApp(authState)

    authState.value = AuthUIState(user = mock())

    waitAndAssertDisplayed(C.Tag.home_screen_container)
    assertNull(shadowOf(composeTestRule.activity).lastRequestedPermission)
    assertEquals(decision, locationDecisionRepository.getDecision())
  }

  private fun showAuthenticatedHome(
      onNavControllerReady: (NavHostController) -> Unit = {},
  ) {
    locationDecisionRepository.saveDecision(LocationPermissionDecision.DENIED)
    renderApp(
        MutableStateFlow(AuthUIState(user = mock())),
        onNavControllerReady = onNavControllerReady,
    )
    waitAndAssertDisplayed(C.Tag.home_screen_container)
  }

  private fun navigateToSettings(navController: NavHostController) {
    composeTestRule.runOnIdle { navController.navigate(Routes.Settings) }
  }

  private fun renderApp(
      authState: MutableStateFlow<AuthUIState>,
      onSignInClickOverride: (() -> Unit)? = null,
      onNavControllerReady: (NavHostController) -> Unit = {},
      settingsViewModel: SettingsViewModel? = null,
  ) {
    val authViewModel = mock<SignInViewModel>()
    whenever(authViewModel.uiState).thenReturn(authState)
    doAnswer {
          authState.value = AuthUIState(signedOut = true)
          Unit
        }
        .whenever(authViewModel)
        .clearSignedInUser()
    composeTestRule.activity.setContent {
      val navController = rememberNavController()
      SideEffect { onNavControllerReady(navController) }
      SampleAppTheme {
        RoamApp(
            authViewModel,
            navController = navController,
            onSignInClickOverride = onSignInClickOverride,
            settingsViewModel = settingsViewModel,
        )
      }
    }
  }

  private fun dispatchCameraPermissionResult() = dispatchCameraPermissionResult(permissionRequest())

  private fun dispatchCameraPermissionResult(
      permissionRequest: org.robolectric.shadows.ShadowActivity.PermissionsRequest
  ): org.robolectric.shadows.ShadowActivity.PermissionsRequest {
    composeTestRule.activity.onRequestPermissionsResult(
        permissionRequest.requestCode,
        arrayOf(Manifest.permission.CAMERA),
        intArrayOf(PackageManager.PERMISSION_DENIED),
    )
    return permissionRequest
  }

  private fun waitAndAssertDisplayed(tag: String) {
    waitFor(tag)
    composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
  }

  private fun waitFor(tag: String) {
    composeTestRule.waitUntil {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun waitForGone(tag: String) {
    composeTestRule.waitUntil {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
    }
  }
}
