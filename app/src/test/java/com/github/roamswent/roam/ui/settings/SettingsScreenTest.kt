/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.roamswent.roam.model.permission.LocationPermissionDecision
import com.github.roamswent.roam.ui.permission.CameraPermissionController
import com.github.roamswent.roam.ui.permission.CameraPermissionState
import com.github.roamswent.roam.ui.permission.LocationPermissionController
import com.github.roamswent.roam.ui.permission.LocationPermissionState
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** Verifies the profile, settings sections, and initial permission statuses. */
  @Test
  fun initialState_displaysSettingsButtonsAndPermissionStatuses() {
    val viewModel = mock<SettingsViewModel>()
    whenever(viewModel.uiState)
        .thenReturn(
            MutableStateFlow(
                SettingsUIState(
                    cameraPermissionGranted = true,
                    locationPermissionGranted = false,
                )
            )
        )
    val (cameraController, locationController) = permissionControllers()

    composeTestRule.setContent {
      SampleAppTheme {
        SettingsScreen(
            settingsViewModel = viewModel,
            cameraPermissionController = cameraController,
            locationPermissionController = locationController,
        )
      }
    }

    composeTestRule.onNodeWithText("Victor").assertIsDisplayed()
    composeTestRule.onNodeWithText("App Permissions").assertIsDisplayed()
    composeTestRule.onNodeWithText("Manage Permissions").assertIsDisplayed()
    composeTestRule.onNodeWithText("Sign Out").assertIsDisplayed()
    composeTestRule.onNodeWithText("Camera permission").assertIsDisplayed()
    composeTestRule.onNodeWithText("Location permission").assertIsDisplayed()
    composeTestRule
        .onNodeWithTag("Camera permission", useUnmergedTree = true)
        .assert(hasAnyChild(hasText("Granted")))
    composeTestRule
        .onNodeWithTag("Location permission", useUnmergedTree = true)
        .assert(hasAnyChild(hasText("Denied")))
  }

  /**
   * Verifies that camera and location status labels follow ViewModel state changes independently.
   */
  @Test
  fun permissionStateChanges_updateCameraAndLocationStatuses() {
    val viewModel = mock<SettingsViewModel>()
    val state = MutableStateFlow(SettingsUIState())
    whenever(viewModel.uiState).thenReturn(state)
    val (cameraController, locationController) = permissionControllers()

    composeTestRule.setContent {
      SampleAppTheme {
        SettingsScreen(
            settingsViewModel = viewModel,
            cameraPermissionController = cameraController,
            locationPermissionController = locationController,
        )
      }
    }

    assertPermissionStatuses(cameraGranted = false, locationGranted = false)

    state.value = SettingsUIState(cameraPermissionGranted = true, locationPermissionGranted = true)
    composeTestRule.waitForIdle()
    assertPermissionStatuses(cameraGranted = true, locationGranted = true)

    state.value = SettingsUIState(cameraPermissionGranted = false, locationPermissionGranted = true)
    composeTestRule.waitForIdle()
    assertPermissionStatuses(cameraGranted = false, locationGranted = true)
  }

  /** Verifies that tapping Manage Permissions invokes the provided callback. */
  @Test
  fun managePermissionsButton_invokesCallback() {
    val viewModel = mock<SettingsViewModel>()
    val state = MutableStateFlow(SettingsUIState())
    whenever(viewModel.uiState).thenReturn(state)
    val (cameraController, locationController) = permissionControllers()
    var managePermissionsClicks = 0

    composeTestRule.setContent {
      SampleAppTheme {
        SettingsScreen(
            settingsViewModel = viewModel,
            cameraPermissionController = cameraController,
            locationPermissionController = locationController,
            onManagePermissionsClick = { managePermissionsClicks++ },
        )
      }
    }

    composeTestRule.onNodeWithText("Manage Permissions").performClick()

    assertEquals(1, managePermissionsClicks)
  }

  /** Verifies that tapping Sign Out delegates to the ViewModel and credential manager. */
  @Test
  fun logOutButton_delegatesToViewModel() {
    val viewModel = mock<SettingsViewModel>()
    val credentialManager = mock<CredentialManager>()
    whenever(viewModel.uiState).thenReturn(MutableStateFlow(SettingsUIState()))
    val (cameraController, locationController) = permissionControllers()

    composeTestRule.setContent {
      SampleAppTheme {
        SettingsScreen(
            settingsViewModel = viewModel,
            credentialManager = credentialManager,
            cameraPermissionController = cameraController,
            locationPermissionController = locationController,
        )
      }
    }

    composeTestRule.onNodeWithText("Sign Out").performClick()

    verify(viewModel).signOut(credentialManager)
  }

  /** Verifies that an initially available error is shown as a toast and then cleared. */
  @Test
  fun errorState_displaysToastAndClearsErrorMessage() {
    val viewModel = mock<SettingsViewModel>()
    whenever(viewModel.uiState)
        .thenReturn(MutableStateFlow(SettingsUIState(errorMsg = "Unable to log out")))
    val (cameraController, locationController) = permissionControllers()

    composeTestRule.setContent {
      SampleAppTheme {
        SettingsScreen(
            settingsViewModel = viewModel,
            cameraPermissionController = cameraController,
            locationPermissionController = locationController,
        )
      }
    }
    composeTestRule.waitForIdle()

    assertEquals("Unable to log out", ShadowToast.getTextOfLatestToast())
    verify(viewModel).clearErrorMsg()
  }

  /** Verifies that an error arriving after composition is shown and then cleared. */
  @Test
  fun errorStateAppearingAfterInitialRender_displaysToastAndClearsError() {
    val viewModel = mock<SettingsViewModel>()
    val state = MutableStateFlow(SettingsUIState())

    whenever(viewModel.uiState).thenReturn(state)
    val (cameraController, locationController) = permissionControllers()

    composeTestRule.setContent {
      SampleAppTheme {
        SettingsScreen(
            settingsViewModel = viewModel,
            cameraPermissionController = cameraController,
            locationPermissionController = locationController,
        )
      }
    }

    state.value = SettingsUIState(errorMsg = "Logout failed")

    composeTestRule.waitForIdle()

    assertEquals(
        "Logout failed",
        ShadowToast.getTextOfLatestToast(),
    )

    verify(viewModel).clearErrorMsg()
  }

  /** Verifies that signing out notifies the caller and shows the success toast. */
  @Test
  fun signedOutState_notifiesCallerAndShowsLogoutToast() {
    val viewModel = mock<SettingsViewModel>()
    whenever(viewModel.uiState).thenReturn(MutableStateFlow(SettingsUIState(signedOut = true)))
    val (cameraController, locationController) = permissionControllers()
    var signedOutCallbacks = 0

    composeTestRule.setContent {
      SampleAppTheme {
        SettingsScreen(
            settingsViewModel = viewModel,
            cameraPermissionController = cameraController,
            locationPermissionController = locationController,
            onSignedOut = { signedOutCallbacks++ },
        )
      }
    }
    composeTestRule.waitForIdle()

    assertEquals(1, signedOutCallbacks)
    assertEquals("Logout successful", ShadowToast.getTextOfLatestToast())
  }

  /** Verifies that permissions are refreshed initially and again when the lifecycle resumes. */
  @Test
  fun composition_refreshesPermissionStateInitiallyAndOnResume() {
    val viewModel = mock<SettingsViewModel>()
    whenever(viewModel.uiState).thenReturn(MutableStateFlow(SettingsUIState()))
    val cameraController = mock<CameraPermissionController>()
    val locationController = mock<LocationPermissionController>()
    whenever(cameraController.hasPermission()).thenReturn(false, true)
    whenever(locationController.hasPermission()).thenReturn(true, false)
    val lifecycleOwner = TestLifecycleOwner()

    composeTestRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
        SampleAppTheme {
          SettingsScreen(
              settingsViewModel = viewModel,
              cameraPermissionController = cameraController,
              locationPermissionController = locationController,
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    verify(viewModel).setCameraPermissionGranted(false)
    verify(viewModel).setLocationPermissionGranted(true)

    lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)

    lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

    verify(viewModel, times(2)).setCameraPermissionGranted(true)
    verify(viewModel, times(2)).setLocationPermissionGranted(false)
  }

  /** Verifies that disposing the screen removes its lifecycle permission observer. */
  @Test
  fun composition_removesPermissionObserverWhenDisposed() {
    val viewModel = mock<SettingsViewModel>()
    whenever(viewModel.uiState).thenReturn(MutableStateFlow(SettingsUIState()))
    val cameraController = mock<CameraPermissionController>()
    val locationController = mock<LocationPermissionController>()
    whenever(cameraController.hasPermission()).thenReturn(false)
    whenever(locationController.hasPermission()).thenReturn(false)
    val lifecycleOwner = TestLifecycleOwner()
    var showScreen by mutableStateOf(true)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
        SampleAppTheme {
          if (showScreen) {
            SettingsScreen(
                settingsViewModel = viewModel,
                cameraPermissionController = cameraController,
                locationPermissionController = locationController,
            )
          }
        }
      }
    }
    composeTestRule.waitForIdle()
    showScreen = false
    composeTestRule.waitForIdle()
    lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

    verify(viewModel).setCameraPermissionGranted(false)
    verify(viewModel).setLocationPermissionGranted(false)
    verify(cameraController).hasPermission()
    verify(locationController).hasPermission()
  }

  private fun permissionControllers():
      Pair<CameraPermissionController, LocationPermissionController> {
    val cameraController = mock<CameraPermissionController>()
    whenever(cameraController.state)
        .thenReturn(MutableStateFlow(CameraPermissionState.NotDetermined))

    val locationController = mock<LocationPermissionController>()
    whenever(locationController.state)
        .thenReturn(
            MutableStateFlow(
                LocationPermissionState(
                    isGranted = false,
                    decision = LocationPermissionDecision.NOT_ASKED,
                )
            )
        )

    return cameraController to locationController
  }

  private fun assertPermissionStatuses(cameraGranted: Boolean, locationGranted: Boolean) {
    val cameraStatus = if (cameraGranted) "Granted" else "Denied"
    val locationStatus = if (locationGranted) "Granted" else "Denied"
    composeTestRule
        .onNodeWithTag("Camera permission", useUnmergedTree = true)
        .assert(hasAnyChild(hasText(cameraStatus)))
    composeTestRule
        .onNodeWithTag("Location permission", useUnmergedTree = true)
        .assert(hasAnyChild(hasText(locationStatus)))
  }

  private class TestLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
      get() = registry
  }
}
