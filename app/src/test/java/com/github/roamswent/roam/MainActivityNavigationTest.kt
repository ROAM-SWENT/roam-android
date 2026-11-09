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
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.github.roamswent.roam.model.guide.FakeSavedGuideRepository
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.permission.LocationPermissionDecision
import com.github.roamswent.roam.model.permission.NotificationPermissionDecision
import com.github.roamswent.roam.model.permission.SharedPreferencesLocationPermissionDecisionRepository
import com.github.roamswent.roam.model.permission.SharedPreferencesNotificationPermissionDecisionRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
  private val notificationDecisionRepository =
      SharedPreferencesNotificationPermissionDecisionRepository(
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
    notificationDecisionRepository.saveDecision(NotificationPermissionDecision.DENIED)
  }

  @After
  fun clearLocationDecision() {
    (ApplicationProvider.getApplicationContext() as Application)
        .getSharedPreferences("location_permission", Context.MODE_PRIVATE)
        .edit()
        .clear()
        .commit()
    (ApplicationProvider.getApplicationContext() as Application)
        .getSharedPreferences("notification_permission", Context.MODE_PRIVATE)
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
  fun firstSignInRequestsNotificationWhenLocationDecisionAlreadyExists() {
    locationDecisionRepository.saveDecision(LocationPermissionDecision.DENIED)
    notificationDecisionRepository.saveDecision(NotificationPermissionDecision.NOT_ASKED)
    shadowOf(composeTestRule.activity).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    val authState = MutableStateFlow(AuthUIState())
    renderApp(authState)

    authState.value = AuthUIState(user = mock())

    val request = waitForNotificationPermissionRequest()
    assertEquals(
        setOf(Manifest.permission.POST_NOTIFICATIONS),
        request.requestedPermissions.toSet(),
    )
    dispatchNotificationPermissionResult(request, granted = false)

    waitAndAssertDisplayed(C.Tag.home_screen_container)
    assertEquals(
        NotificationPermissionDecision.DENIED,
        notificationDecisionRepository.getDecision(),
    )
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
  fun dockHomeTabFromFriendsPopsBackToSingleHomeEntry() {
    assertHomeTabPopsToSingleHomeEntry(
        C.Tag.dock_tab_friends,
        C.Tag.friends_screen_container,
    )
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
  fun dockedScreenContentReachesIntoDockBumpOnEveryDockedScreen() {
    lateinit var navController: NavHostController
    showAuthenticatedHome(
        onNavControllerReady = { navController = it },
        savedGuideRepository = FakeSavedGuideRepository(),
    )

    assertContentReachesDockBump(C.Tag.home_screen_container)

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_friends).performClick()
    waitAndAssertDisplayed(C.Tag.friends_screen_container)
    assertContentReachesDockBump(C.Tag.friends_screen_container)

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).performClick()
    waitAndAssertDisplayed(C.Tag.collection_screen)
    assertContentReachesDockBump(C.Tag.collection_screen)

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).performClick()
    waitAndAssertDisplayed(C.Tag.explore_screen_container)
    assertContentReachesDockBump(C.Tag.explore_screen_container)

    composeTestRule.runOnIdle {
      assertEquals(Routes.Explore, navController.currentDestination?.route)
    }
  }

  @Test
  fun friendsDockTabShowsFriendsAndHomeTabReturnsHome() {
    lateinit var navController: NavHostController
    showAuthenticatedHome(onNavControllerReady = { navController = it })

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_friends).performClick()
    waitAndAssertDisplayed(C.Tag.friends_title)
    composeTestRule.onNodeWithTag(C.Tag.friends_title).assertTextEquals("Friends")
    composeTestRule.runOnIdle {
      assertEquals(Routes.Friends, navController.currentDestination?.route)
    }

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).performClick()
    waitAndAssertDisplayed(C.Tag.home_screen_container)
    composeTestRule.runOnIdle { assertEquals(Routes.Home, navController.currentDestination?.route) }
  }

  /**
   * Pins the bottom dock navigation contract: the back stack after each step. Sign-in pops SignIn
   * inclusively, tabs stack destinations above the Home root, the Home tab pops back to that root,
   * and no flow ever duplicates entries.
   */
  @Test
  fun signInToHomeLeavesSingleHomeEntryOnBackStack() {
    lateinit var navController: NavHostController
    showAuthenticatedHome(onNavControllerReady = { navController = it })

    assertEquals(listOf(Routes.Home), backStackRoutes(navController))
  }

  @Test
  fun dockFriendsTabFromHomeStacksFriendsAboveHome() {
    assertTabStacksAboveHome(
        C.Tag.dock_tab_friends,
        C.Tag.friends_screen_container,
        Routes.Friends,
    )
  }

  @Test
  fun alternatingDockTabsNeverDuplicateBackStackEntries() {
    lateinit var navController: NavHostController
    showAuthenticatedHome(onNavControllerReady = { navController = it })

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_friends).performClick()
    waitAndAssertDisplayed(C.Tag.friends_screen_container)
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).performClick()
    waitAndAssertDisplayed(C.Tag.home_screen_container)
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_friends).performClick()
    waitAndAssertDisplayed(C.Tag.friends_screen_container)

    assertEquals(listOf(Routes.Home, Routes.Friends), backStackRoutes(navController))
  }

  @Test
  fun systemBackFromFriendsReturnsToSingleHomeEntry() {
    assertSystemBackPopsToSingleHomeEntry(
        C.Tag.dock_tab_friends,
        C.Tag.friends_screen_container,
    )
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

    composeTestRule.runOnIdle { navController.navigate(Routes.Friends) }
    waitAndAssertDisplayed(C.Tag.friends_screen_container)
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_friends).performClick()
    composeTestRule.waitForIdle()
    assertEquals(listOf(Routes.Home, Routes.Friends), backStackRoutes(navController))
  }

  @Test
  fun dockCollectionTabFromHomeStacksCollectionAboveHome() {
    assertTabStacksAboveHome(
        C.Tag.dock_tab_collection,
        C.Tag.collection_screen,
        Routes.Collection,
    )
  }

  @Test
  fun dockExploreTabFromHomeStacksExploreAboveHome() {
    assertTabStacksAboveHome(
        C.Tag.dock_tab_explore,
        C.Tag.explore_screen_container,
        Routes.Explore,
    )
  }

  @Test
  fun crossDockTabTapReplacesDestinationWithoutStacking() {
    lateinit var navController: NavHostController
    showAuthenticatedHome(
        onNavControllerReady = { navController = it },
        savedGuideRepository = FakeSavedGuideRepository(),
    )

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).performClick()
    waitAndAssertDisplayed(C.Tag.collection_screen)
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).performClick()
    waitAndAssertDisplayed(C.Tag.explore_screen_container)

    assertEquals(listOf(Routes.Home, Routes.Explore), backStackRoutes(navController))
  }

  @Test
  fun dockHomeTabFromCollectionPopsBackToSingleHomeEntry() {
    assertHomeTabPopsToSingleHomeEntry(C.Tag.dock_tab_collection, C.Tag.collection_screen)
  }

  @Test
  fun dockHomeTabFromExplorePopsBackToSingleHomeEntry() {
    assertHomeTabPopsToSingleHomeEntry(
        C.Tag.dock_tab_explore,
        C.Tag.explore_screen_container,
    )
  }

  @Test
  fun systemBackFromCollectionReturnsToSingleHomeEntry() {
    assertSystemBackPopsToSingleHomeEntry(C.Tag.dock_tab_collection, C.Tag.collection_screen)
  }

  @Test
  fun systemBackFromExploreReturnsToSingleHomeEntry() {
    assertSystemBackPopsToSingleHomeEntry(C.Tag.dock_tab_explore, C.Tag.explore_screen_container)
  }

  @Test
  fun tappingSelectedDockTabOnCollectionAndExploreLeavesStackUnchanged() {
    lateinit var navController: NavHostController
    showAuthenticatedHome(
        onNavControllerReady = { navController = it },
        savedGuideRepository = FakeSavedGuideRepository(),
    )

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).performClick()
    waitAndAssertDisplayed(C.Tag.collection_screen)
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).performClick()
    composeTestRule.waitForIdle()
    assertEquals(listOf(Routes.Home, Routes.Collection), backStackRoutes(navController))

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).performClick()
    waitAndAssertDisplayed(C.Tag.explore_screen_container)
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).performClick()
    composeTestRule.waitForIdle()
    assertEquals(listOf(Routes.Home, Routes.Explore), backStackRoutes(navController))
  }

  @Test
  fun alternatingAllDockTabsNeverDuplicatesBackStackEntries() {
    lateinit var navController: NavHostController
    showAuthenticatedHome(
        onNavControllerReady = { navController = it },
        savedGuideRepository = FakeSavedGuideRepository(),
    )

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_friends).performClick()
    waitAndAssertDisplayed(C.Tag.friends_screen_container)
    assertEquals(listOf(Routes.Home, Routes.Friends), backStackRoutes(navController))

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).performClick()
    waitAndAssertDisplayed(C.Tag.collection_screen)
    assertEquals(listOf(Routes.Home, Routes.Collection), backStackRoutes(navController))

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).performClick()
    waitAndAssertDisplayed(C.Tag.explore_screen_container)
    assertEquals(listOf(Routes.Home, Routes.Explore), backStackRoutes(navController))

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_friends).performClick()
    waitAndAssertDisplayed(C.Tag.friends_screen_container)
    assertEquals(listOf(Routes.Home, Routes.Friends), backStackRoutes(navController))
  }

  @Test
  fun exploreBackArrowReturnsToSingleHomeEntry() {
    lateinit var navController: NavHostController
    showAuthenticatedHome(onNavControllerReady = { navController = it })

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).performClick()
    waitAndAssertDisplayed(C.Tag.explore_screen_container)
    composeTestRule.onNodeWithContentDescription("Back").performClick()

    waitAndAssertDisplayed(C.Tag.home_screen_container)
    assertEquals(listOf(Routes.Home), backStackRoutes(navController))
  }

  /**
   * Like the settings and home tabs, the collection and explore tab navigations are not gated on
   * connectivity: offline, tapping them navigates without the offline dialog.
   */
  @Test
  fun offlineCollectionAndExploreTabsRemainAccessible() {
    lateinit var navController: NavHostController
    showAuthenticatedHome(
        onNavControllerReady = { navController = it },
        savedGuideRepository = FakeSavedGuideRepository(),
        isOnlineOverride = false,
    )

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).performClick()
    waitAndAssertDisplayed(C.Tag.collection_screen)
    composeTestRule.onAllNodesWithTag(C.Tag.offline_action_dialog).assertCountEquals(0)
    composeTestRule.runOnIdle {
      assertEquals(Routes.Collection, navController.currentDestination?.route)
    }

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).performClick()
    waitAndAssertDisplayed(C.Tag.explore_screen_container)
    composeTestRule.onAllNodesWithTag(C.Tag.offline_action_dialog).assertCountEquals(0)
    composeTestRule.runOnIdle {
      assertEquals(Routes.Explore, navController.currentDestination?.route)
    }

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).performClick()
    waitAndAssertDisplayed(C.Tag.home_screen_container)
    composeTestRule.onAllNodesWithTag(C.Tag.offline_action_dialog).assertCountEquals(0)
    composeTestRule.runOnIdle { assertEquals(Routes.Home, navController.currentDestination?.route) }
  }

  /**
   * The dock carries five slots on every docked screen: home, explore, the scan action, collection
   * and friends all render, and every tab is enabled except the one marking the current screen as
   * selected.
   */
  @Test
  fun dockShowsAllFiveSlotsEnabledOnEveryDockedScreen() {
    lateinit var navController: NavHostController
    showAuthenticatedHome(
        onNavControllerReady = { navController = it },
        savedGuideRepository = FakeSavedGuideRepository(),
    )
    assertDockSlotsEnabledOn(
        C.Tag.home_screen_container,
        C.Tag.dock_tab_explore,
        C.Tag.dock_tab_collection,
        C.Tag.dock_tab_friends,
    )

    navigateToSettings(navController)
    assertDockSlotsEnabledOn(
        C.Tag.settings_manage_permissions_button,
        C.Tag.dock_tab_home,
        C.Tag.dock_tab_explore,
        C.Tag.dock_tab_collection,
        C.Tag.dock_tab_friends,
    )

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).performClick()
    assertDockSlotsEnabledOn(
        C.Tag.collection_screen,
        C.Tag.dock_tab_home,
        C.Tag.dock_tab_explore,
        C.Tag.dock_tab_friends,
    )

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).performClick()
    assertDockSlotsEnabledOn(
        C.Tag.explore_screen_container,
        C.Tag.dock_tab_home,
        C.Tag.dock_tab_collection,
        C.Tag.dock_tab_friends,
    )
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

    composeTestRule.onNodeWithTag(C.Tag.home_profile_button).performClick()
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
  fun profileButtonFromFriendsOpensSettings() {
    lateinit var navController: NavHostController
    showAuthenticatedHome(
        onNavControllerReady = { navController = it },
        savedGuideRepository = FakeSavedGuideRepository(),
    )

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_friends).performClick()
    waitAndAssertDisplayed(C.Tag.friends_screen_container)
    composeTestRule.onNodeWithTag(C.Tag.home_profile_button).performClick()

    waitAndAssertDisplayed(C.Tag.settings_manage_permissions_button)
    composeTestRule.runOnIdle {
      assertEquals(Routes.Settings, navController.currentDestination?.route)
    }
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

    // Switching tabs saves Collection's back stack, which sign-out must discard.
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).performClick()
    waitAndAssertDisplayed(C.Tag.collection_screen)
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).performClick()
    waitAndAssertDisplayed(C.Tag.explore_screen_container)
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).performClick()
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
    composeTestRule.runOnIdle { assertFalse(navController.clearBackStack(Routes.Collection)) }
  }

  @Test
  fun signedOutUserCannotRestorePreviousNavigationState() {
    locationDecisionRepository.saveDecision(LocationPermissionDecision.DENIED)
    val authState = MutableStateFlow(AuthUIState(user = mock()))
    val settingsState = MutableStateFlow(SettingsUIState())
    val settingsViewModel = mock<SettingsViewModel>()
    whenever(settingsViewModel.uiState).thenReturn(settingsState)
    lateinit var navController: NavHostController
    renderApp(
        authState,
        onSignInClickOverride = { authState.value = AuthUIState(user = mock()) },
        onNavControllerReady = { navController = it },
        settingsViewModel = settingsViewModel,
    )
    waitAndAssertDisplayed(C.Tag.home_screen_container)

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).performClick()
    waitAndAssertDisplayed(C.Tag.collection_screen)
    val userACollectionEntryId = composeTestRule.runOnIdle {
      requireNotNull(navController.currentBackStackEntry).id
    }

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).performClick()
    waitAndAssertDisplayed(C.Tag.explore_screen_container)
    val userAExploreEntryId = composeTestRule.runOnIdle {
      requireNotNull(navController.currentBackStackEntry).id
    }

    composeTestRule.runOnIdle {
      navController.navigate(Routes.Settings) {
        popUpTo(Routes.Home) {
          inclusive = false
          saveState = true
        }
        launchSingleTop = true
        restoreState = true
      }
    }
    waitAndAssertDisplayed(C.Tag.settings_manage_permissions_button)
    val userASettingsEntryId = composeTestRule.runOnIdle {
      requireNotNull(navController.currentBackStackEntry).id
    }

    composeTestRule.runOnIdle { settingsState.value = SettingsUIState(signedOut = true) }
    waitAndAssertDisplayed(C.Tag.sign_in_screen_container)
    composeTestRule.runOnIdle {
      listOf(Routes.Settings, Routes.Friends, Routes.Collection, Routes.Explore).forEach {
        assertFalse(navController.clearBackStack(it))
      }
      settingsState.value = SettingsUIState()
    }

    composeTestRule.onNodeWithTag(C.Tag.sign_in_google_button).performClick()
    waitAndAssertDisplayed(C.Tag.home_screen_container)
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).performClick()
    waitAndAssertDisplayed(C.Tag.collection_screen)
    val userBCollectionEntryId = composeTestRule.runOnIdle {
      requireNotNull(navController.currentBackStackEntry).id
    }
    assertNotEquals(userACollectionEntryId, userBCollectionEntryId)

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).performClick()
    waitAndAssertDisplayed(C.Tag.explore_screen_container)
    val userBExploreEntryId = composeTestRule.runOnIdle {
      requireNotNull(navController.currentBackStackEntry).id
    }
    assertNotEquals(userAExploreEntryId, userBExploreEntryId)

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).performClick()
    waitAndAssertDisplayed(C.Tag.home_screen_container)
    composeTestRule.onNodeWithTag(C.Tag.home_profile_button).performClick()
    waitAndAssertDisplayed(C.Tag.settings_manage_permissions_button)
    val userBSettingsEntryId = composeTestRule.runOnIdle {
      requireNotNull(navController.currentBackStackEntry).id
    }
    assertNotEquals(userASettingsEntryId, userBSettingsEntryId)
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

  private fun waitForNotificationPermissionRequest():
      org.robolectric.shadows.ShadowActivity.PermissionsRequest {
    composeTestRule.waitUntil {
      shadowOf(composeTestRule.activity)
          .lastRequestedPermission
          ?.requestedPermissions
          ?.contains(Manifest.permission.POST_NOTIFICATIONS) == true
    }
    return permissionRequest()
  }

  private fun dispatchNotificationPermissionResult(
      request: org.robolectric.shadows.ShadowActivity.PermissionsRequest,
      granted: Boolean,
  ) {
    if (granted) {
      shadowOf(composeTestRule.activity).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      shadowOf(composeTestRule.activity).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }
    val result =
        if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
    composeTestRule.activity.onRequestPermissionsResult(
        request.requestCode,
        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
        intArrayOf(result),
    )
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

  private fun assertContentReachesDockBump(contentTag: String) {
    val contentBottom =
        composeTestRule
            .onAllNodesWithTag(contentTag)
            .fetchSemanticsNodes(atLeastOneRootRequired = true)
            .single()
            .boundsInRoot
            .bottom
    val dockTop =
        composeTestRule
            .onAllNodesWithTag(C.Tag.dock_container)
            .fetchSemanticsNodes(atLeastOneRootRequired = true)
            .single()
            .boundsInRoot
            .top
    val bumpTop = dockTop + with(composeTestRule.density) { 36.dp.toPx() }

    assertTrue(
        "$contentTag bottom $contentBottom must reach dock bar top $bumpTop",
        contentBottom >= bumpTop,
    )
  }

  /** Asserts the five dock slots render: home, explore, the scan action, collection, friends. */
  /** Renders Home with a fake saved-guide repository and returns the app's nav controller. */
  private fun showAuthenticatedHomeWithNavController(): NavHostController {
    lateinit var navController: NavHostController
    showAuthenticatedHome(
        onNavControllerReady = { navController = it },
        savedGuideRepository = FakeSavedGuideRepository(),
    )
    return navController
  }

  /** Taps [tabTag] from Home and asserts its screen stacked [route] above the single Home root. */
  private fun assertTabStacksAboveHome(tabTag: String, screenTag: String, route: String) {
    val navController = showAuthenticatedHomeWithNavController()

    composeTestRule.onNodeWithTag(tabTag).performClick()
    waitAndAssertDisplayed(screenTag)

    assertEquals(listOf(Routes.Home, route), backStackRoutes(navController))
  }

  /** Enters [tabTag]'s screen, taps the Home tab, and asserts only the Home root remains. */
  private fun assertHomeTabPopsToSingleHomeEntry(tabTag: String, screenTag: String) {
    val navController = showAuthenticatedHomeWithNavController()

    composeTestRule.onNodeWithTag(tabTag).performClick()
    waitAndAssertDisplayed(screenTag)
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).performClick()

    waitAndAssertDisplayed(C.Tag.home_screen_container)
    assertEquals(listOf(Routes.Home), backStackRoutes(navController))
  }

  /** Enters [tabTag]'s screen, presses system back, and asserts only the Home root remains. */
  private fun assertSystemBackPopsToSingleHomeEntry(tabTag: String, screenTag: String) {
    val navController = showAuthenticatedHomeWithNavController()

    composeTestRule.onNodeWithTag(tabTag).performClick()
    waitAndAssertDisplayed(screenTag)

    composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
    waitAndAssertDisplayed(C.Tag.home_screen_container)

    assertEquals(listOf(Routes.Home), backStackRoutes(navController))
  }

  /**
   * Waits for [screenTag], asserts the five dock slots render, and asserts every [enabledTabs]
   * entry enabled: the screen's own tab is the disabled-because-selected one.
   */
  private fun assertDockSlotsEnabledOn(screenTag: String, vararg enabledTabs: String) {
    waitAndAssertDisplayed(screenTag)
    assertDockShowsAllFiveSlots()
    enabledTabs.forEach { composeTestRule.onNodeWithTag(it).assertIsEnabled() }
  }

  private fun assertDockShowsAllFiveSlots() {
    waitAndAssertDisplayed(C.Tag.dock_tab_home)
    waitAndAssertDisplayed(C.Tag.dock_tab_explore)
    waitAndAssertDisplayed(C.Tag.dock_scan_button)
    waitAndAssertDisplayed(C.Tag.dock_tab_collection)
    waitAndAssertDisplayed(C.Tag.dock_tab_friends)
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
