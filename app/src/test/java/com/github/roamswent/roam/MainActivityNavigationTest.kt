/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 * Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
 * Co-authored-by: Mistral Vibe <vibe@mistral.ai>
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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.github.roamswent.roam.model.guide.FakeSavedGuideRepository
import com.github.roamswent.roam.model.guide.Guide
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
    waitAndAssertDisplayed(C.Tag.sign_in_screen_container)
    waitForGone(C.Tag.home_screen_container)
    waitForGone(C.Tag.scan_camera_screen)
  }

  @Test
  fun firstSuccessfulSignInRequestsLocationAndGrantNavigatesToHome() {
    val authState = MutableStateFlow(AuthUIState())
    renderApp(authState)

    waitAndAssertDisplayed(C.Tag.sign_in_screen_container)
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

    composeTestRule.onNodeWithTag(C.Tag.sign_in_google_button).performClick()

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
    composeTestRule.onNodeWithTag(C.Tag.settings_manage_permissions_button).performClick()

    val intent = shadowOf(composeTestRule.activity).nextStartedActivity
    assertNotNull(intent)
    assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
    assertEquals(
        composeTestRule.activity.packageName,
        intent.data?.schemeSpecificPart,
    )
  }

  @Test
  fun dockHomeTabFromSettingsPopsBackToSingleHomeEntry() {
    lateinit var navController: NavHostController
    showAuthenticatedHome(onNavControllerReady = { navController = it })

    navigateToSettings(navController)
    waitAndAssertDisplayed(C.Tag.settings_manage_permissions_button)
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).performClick()

    waitAndAssertDisplayed(C.Tag.home_screen_container)
    assertEquals(listOf(Routes.Home), backStackRoutes(navController))
  }

  @Test
  fun collectionDestinationShowsSavedGuides() {
    lateinit var navController: NavHostController
    val guide =
        Guide(
            id = "eiffel",
            photoBytes = ByteArray(0),
            monumentName = "Eiffel Tower",
            confidence = 0.9,
            history = "History",
            facts = emptyList(),
            culture = "Culture",
        )
    showAuthenticatedHome(
        onNavControllerReady = { navController = it },
        savedGuideRepository = FakeSavedGuideRepository(listOf(guide)),
    )

    composeTestRule.runOnIdle { navController.navigate(Routes.Collection) }

    waitAndAssertDisplayed(C.Tag.collection_grid)
    composeTestRule.onAllNodesWithTag(C.Tag.collection_card).assertCountEquals(1)
  }

  @Test
  fun dockSettingsTabNavigatesToSettingsAndHomeTabReturnsHome() {
    lateinit var navController: NavHostController
    showAuthenticatedHome(onNavControllerReady = { navController = it })

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_settings).performClick()
    waitAndAssertDisplayed(C.Tag.settings_manage_permissions_button)
    composeTestRule.runOnIdle {
      assertEquals(Routes.Settings, navController.currentDestination?.route)
    }

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).performClick()
    waitAndAssertDisplayed(C.Tag.home_screen_container)
    composeTestRule.runOnIdle { assertEquals(Routes.Home, navController.currentDestination?.route) }
  }

  /**
   * Pins the bottom dock navigation contract: the back stack after each step. Sign-in pops SignIn
   * inclusively, tabs stack Settings above the Home root, the Home tab pops back to that root, and
   * no flow ever duplicates entries.
   */
  @Test
  fun signInToHomeLeavesSingleHomeEntryOnBackStack() {
    lateinit var navController: NavHostController
    showAuthenticatedHome(onNavControllerReady = { navController = it })

    assertEquals(listOf(Routes.Home), backStackRoutes(navController))
  }

  @Test
  fun dockSettingsTabFromHomeStacksSettingsAboveHome() {
    lateinit var navController: NavHostController
    showAuthenticatedHome(onNavControllerReady = { navController = it })

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_settings).performClick()
    waitAndAssertDisplayed(C.Tag.settings_manage_permissions_button)

    assertEquals(listOf(Routes.Home, Routes.Settings), backStackRoutes(navController))
  }

  @Test
  fun alternatingDockTabsNeverDuplicateBackStackEntries() {
    lateinit var navController: NavHostController
    showAuthenticatedHome(onNavControllerReady = { navController = it })

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_settings).performClick()
    waitAndAssertDisplayed(C.Tag.settings_manage_permissions_button)
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).performClick()
    waitAndAssertDisplayed(C.Tag.home_screen_container)
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_settings).performClick()
    waitAndAssertDisplayed(C.Tag.settings_manage_permissions_button)

    assertEquals(listOf(Routes.Home, Routes.Settings), backStackRoutes(navController))
  }

  @Test
  fun systemBackFromSettingsReturnsToSingleHomeEntry() {
    lateinit var navController: NavHostController
    showAuthenticatedHome(onNavControllerReady = { navController = it })
    navigateToSettings(navController)
    waitAndAssertDisplayed(C.Tag.settings_manage_permissions_button)

    composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
    waitAndAssertDisplayed(C.Tag.home_screen_container)

    assertEquals(listOf(Routes.Home), backStackRoutes(navController))
  }

  @Test
  fun systemBackFromHomeFinishesActivity() {
    showAuthenticatedHome()

    composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

    composeTestRule.waitUntil { composeTestRule.activity.isFinishing }
  }

  @Test
  fun tappingSelectedDockTabLeavesBackStackUnchanged() {
    lateinit var navController: NavHostController
    showAuthenticatedHome(onNavControllerReady = { navController = it })

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).performClick()
    composeTestRule.waitForIdle()
    assertEquals(listOf(Routes.Home), backStackRoutes(navController))

    navigateToSettings(navController)
    waitAndAssertDisplayed(C.Tag.settings_manage_permissions_button)
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_settings).performClick()
    composeTestRule.waitForIdle()
    assertEquals(listOf(Routes.Home, Routes.Settings), backStackRoutes(navController))
  }

  /**
   * The dock carries five slots on every docked screen: home, explore, the scan action, collection
   * and settings all render, and every tab is enabled except the one marking the current screen as
   * selected.
   */
  @Test
  fun dockShowsAllFiveSlotsEnabledOnEveryDockedScreen() {
    lateinit var navController: NavHostController
    showAuthenticatedHome(
        onNavControllerReady = { navController = it },
        savedGuideRepository = FakeSavedGuideRepository(),
    )
    assertDockShowsAllFiveSlots()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_settings).assertIsEnabled()

    navigateToSettings(navController)
    waitAndAssertDisplayed(C.Tag.settings_manage_permissions_button)
    assertDockShowsAllFiveSlots()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).assertIsEnabled()

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).performClick()
    waitAndAssertDisplayed(C.Tag.collection_screen)
    assertDockShowsAllFiveSlots()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_settings).assertIsEnabled()

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).performClick()
    waitAndAssertDisplayed(C.Tag.explore_screen_container)
    assertDockShowsAllFiveSlots()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_settings).assertIsEnabled()
  }

  @Test
  fun dockIsAbsentOnSignIn() {
    renderApp(MutableStateFlow(AuthUIState()))
    waitAndAssertDisplayed(C.Tag.sign_in_screen_container)

    composeTestRule.onNodeWithTag(C.Tag.dock_container).assertDoesNotExist()
  }

  @Test
  fun dockIsAbsentOnScanAndScanReview() {
    shadowOf(composeTestRule.activity).grantPermissions(Manifest.permission.CAMERA)
    lateinit var navController: NavHostController
    showAuthenticatedHome(onNavControllerReady = { navController = it })

    composeTestRule.onNodeWithTag(C.Tag.dock_scan_button).performClick()
    waitAndAssertDisplayed(C.Tag.scan_camera_screen)
    composeTestRule.onNodeWithTag(C.Tag.dock_container).assertDoesNotExist()

    composeTestRule.runOnIdle {
      navController.navigate("${Routes.ScanReview}?uri=${Uri.encode("content://test/photo.jpg")}")
    }
    waitAndAssertDisplayed(C.Tag.scan_review_screen)
    composeTestRule.onNodeWithTag(C.Tag.dock_container).assertDoesNotExist()
  }

  @Test
  fun offlineScanIsBlockedButProfileRemainsAccessible() {
    lateinit var navController: NavHostController
    showAuthenticatedHome(
        onNavControllerReady = { navController = it },
        isOnlineOverride = false,
    )

    composeTestRule.onNodeWithTag(C.Tag.dock_scan_button).performClick()
    waitAndAssertDisplayed(C.Tag.offline_action_dialog)
    waitAndAssertDisplayed(C.Tag.home_screen_container)
    assertNull(shadowOf(composeTestRule.activity).lastRequestedPermission)

    composeTestRule.onNodeWithTag(C.Tag.offline_action_dialog_dismiss).performClick()
    waitForGone(C.Tag.offline_action_dialog)

    // The dock tabs succeeded the navigation affordances main gated with
    // OPEN_PROFILE (the profile tab) and RETURN_HOME (the settings home
    // button), and the merge dropped those gate calls: offline, navigation
    // between Home and Settings stays ungated in both directions.
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_settings).performClick()
    waitAndAssertDisplayed(C.Tag.settings_manage_permissions_button)
    composeTestRule.onAllNodesWithTag(C.Tag.offline_action_dialog).assertCountEquals(0)
    composeTestRule.runOnIdle {
      assertEquals(Routes.Settings, navController.currentDestination?.route)
    }

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).performClick()
    waitAndAssertDisplayed(C.Tag.home_screen_container)
    composeTestRule.onAllNodesWithTag(C.Tag.offline_action_dialog).assertCountEquals(0)
    composeTestRule.runOnIdle { assertEquals(Routes.Home, navController.currentDestination?.route) }
  }

  @Test
  fun signedOutStateReturnsToSignInWithoutAutomaticallySigningInAgain() {
    locationDecisionRepository.saveDecision(LocationPermissionDecision.DENIED)
    val authState = MutableStateFlow(AuthUIState(user = mock()))
    val settingsViewModel = mock<SettingsViewModel>()
    whenever(settingsViewModel.uiState)
        .thenReturn(MutableStateFlow(SettingsUIState(signedOut = true)))
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
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      composeTestRule
          .onAllNodesWithTag(C.Tag.sign_in_screen_container)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    waitForGone(C.Tag.home_screen_container)
    composeTestRule.waitForIdle()

    assertEquals(AuthUIState(signOutCompleted = true), authState.value)
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
    authState.value = AuthUIState(signOutCompleted = true)
    composeTestRule.runOnIdle {
      navController.navigate(Routes.SignIn) { popUpTo(navController.graph.id) { inclusive = true } }
    }
    waitAndAssertDisplayed(C.Tag.sign_in_screen_container)

    composeTestRule.onNodeWithTag(C.Tag.sign_in_google_button).performClick()

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

    composeTestRule.onNodeWithTag(C.Tag.dock_scan_button).performClick()
    waitAndAssertDisplayed(C.Tag.scan_camera_screen)

    composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

    waitAndAssertDisplayed(C.Tag.home_screen_container)
  }

  @Test
  fun scanEntryNavigatesToScanDestination() {
    showAuthenticatedHome()
    shadowOf(composeTestRule.activity).grantPermissions(Manifest.permission.CAMERA)
    waitFor(C.Tag.dock_scan_button)
    composeTestRule.onNodeWithTag(C.Tag.dock_scan_button).performClick()

    waitAndAssertDisplayed(C.Tag.scan_camera_screen)
    waitForGone(C.Tag.home_screen_container)
  }

  @Test
  fun denyShowsPermissionDialogWithoutNavigating() {
    showAuthenticatedHome()
    waitFor(C.Tag.dock_scan_button)
    composeTestRule.onNodeWithTag(C.Tag.dock_scan_button).performClick()
    dispatchCameraPermissionResult()

    waitAndAssertDisplayed(C.Tag.camera_permission_dialog)
    waitForGone(C.Tag.scan_camera_screen)
    composeTestRule.onNodeWithTag(C.Tag.camera_permission_settings_button).assertIsDisplayed()
  }

  @Test
  fun dismissingPermissionDialogLeavesHomeDisplayed() {
    showAuthenticatedHome()
    waitFor(C.Tag.dock_scan_button)
    composeTestRule.onNodeWithTag(C.Tag.dock_scan_button).performClick()
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
    waitFor(C.Tag.dock_scan_button)
    composeTestRule.onNodeWithTag(C.Tag.dock_scan_button).performClick()
    val firstPermissionRequest = dispatchCameraPermissionResult()
    waitAndAssertDisplayed(C.Tag.camera_permission_dialog)
    waitForGone(C.Tag.scan_camera_screen)

    composeTestRule.onNodeWithTag(C.Tag.camera_permission_dismiss_button).performClick()
    waitForGone(C.Tag.camera_permission_dialog)
    composeTestRule.onAllNodesWithTag(C.Tag.camera_permission_dialog).assertCountEquals(0)
    waitAndAssertDisplayed(C.Tag.home_screen_container)
    waitForGone(C.Tag.scan_camera_screen)

    composeTestRule.onNodeWithTag(C.Tag.dock_scan_button).performClick()
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
    waitFor(C.Tag.dock_scan_button)

    composeTestRule.onNodeWithTag(C.Tag.dock_scan_button).performClick()

    waitForGone(C.Tag.camera_permission_dialog)
    waitForGone(C.Tag.scan_camera_screen)
    assertNull(shadowOf(composeTestRule.activity).lastRequestedPermission)
  }

  @Test
  fun unavailableCameraDisablesScanButton() {
    val application: Application = ApplicationProvider.getApplicationContext()
    shadowOf(application.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, false)
    showAuthenticatedHome()
    waitFor(C.Tag.dock_scan_button)

    composeTestRule.onNodeWithTag(C.Tag.dock_scan_button).assertIsNotEnabled()
  }

  @Test
  fun openingSettingsFromPermissionDialog() {
    showAuthenticatedHome()
    waitFor(C.Tag.dock_scan_button)

    composeTestRule.onNodeWithTag(C.Tag.dock_scan_button).performClick()

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
      savedGuideRepository: FakeSavedGuideRepository? = null,
      isOnlineOverride: Boolean? = true,
  ) {
    locationDecisionRepository.saveDecision(LocationPermissionDecision.DENIED)
    renderApp(
        MutableStateFlow(AuthUIState(user = mock())),
        onNavControllerReady = onNavControllerReady,
        savedGuideRepository = savedGuideRepository,
        isOnlineOverride = isOnlineOverride,
    )
    waitAndAssertDisplayed(C.Tag.home_screen_container)
  }

  private fun navigateToSettings(navController: NavHostController) {
    composeTestRule.runOnIdle { navController.navigate(Routes.Settings) }
  }

  /** Asserts the five dock slots render: home, explore, the scan action, collection, settings. */
  private fun assertDockShowsAllFiveSlots() {
    waitAndAssertDisplayed(C.Tag.dock_tab_home)
    waitAndAssertDisplayed(C.Tag.dock_tab_explore)
    waitAndAssertDisplayed(C.Tag.dock_scan_button)
    waitAndAssertDisplayed(C.Tag.dock_tab_collection)
    waitAndAssertDisplayed(C.Tag.dock_tab_settings)
  }

  /**
   * Returns the screen routes on the navigation back stack, read on the UI thread. The NavGraph
   * root entry has a null route and is not a screen, so it is filtered out.
   */
  private fun backStackRoutes(navController: NavHostController): List<String> =
      composeTestRule.runOnIdle {
        navController.currentBackStack.value.mapNotNull { it.destination.route }
      }

  private fun renderApp(
      authState: MutableStateFlow<AuthUIState>,
      onSignInClickOverride: (() -> Unit)? = null,
      onNavControllerReady: (NavHostController) -> Unit = {},
      settingsViewModel: SettingsViewModel? = null,
      savedGuideRepository: FakeSavedGuideRepository? = null,
      isOnlineOverride: Boolean? = true,
  ) {
    val authViewModel = mock<SignInViewModel>()
    whenever(authViewModel.uiState).thenReturn(authState)
    doAnswer {
          authState.value = AuthUIState(signOutCompleted = true)
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
            savedGuideRepositoryOverride = savedGuideRepository,
            isOnlineOverride = isOnlineOverride,
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
