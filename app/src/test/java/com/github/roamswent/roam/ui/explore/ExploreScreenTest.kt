/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.explore

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.github.roamswent.roam.model.permission.LocationPermissionDecision
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

@RunWith(RobolectricTestRunner::class)
class ExploreScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** Verifies that tapping the back button invokes the navigation callback. */
  @Test
  fun backButtonInvokesCallback() {
    var backClicks = 0
    val exploreViewModel = mock<ExploreViewModel>()
    whenever(exploreViewModel.uiState)
        .thenReturn(
            MutableStateFlow(ExploreUiState.PermissionRequired(LocationPermissionDecision.DENIED))
        )
    val locationPermissionController = permissionController(granted = false)
    composeTestRule.setContent {
      SampleAppTheme {
        ExploreScreen(
            onBackClick = { backClicks++ },
            exploreViewModel = exploreViewModel,
            locationPermissionController = locationPermissionController,
        )
      }
    }

    val backButton = composeTestRule.onNodeWithContentDescription("Back")
    backButton.assertIsDisplayed().performClick()

    assertEquals(1, backClicks)
  }

  /** Verifies that permission state changes are forwarded to the ExploreViewModel. */
  @Test
  fun permissionStateChangesAreForwardedToViewModel() {
    val exploreViewModel = mock<ExploreViewModel>()
    whenever(exploreViewModel.uiState)
        .thenReturn(
            MutableStateFlow(ExploreUiState.PermissionRequired(LocationPermissionDecision.DENIED))
        )
    val permissionState =
        MutableStateFlow(
            LocationPermissionState(
                isGranted = false,
                decision = LocationPermissionDecision.DENIED,
            )
        )
    val locationPermissionController = mock<LocationPermissionController>()
    whenever(locationPermissionController.state).thenReturn(permissionState)

    composeTestRule.setContent {
      SampleAppTheme {
        ExploreScreen(
            onBackClick = {},
            exploreViewModel = exploreViewModel,
            locationPermissionController = locationPermissionController,
        )
      }
    }
    composeTestRule.waitForIdle()

    verify(exploreViewModel).onPermissionStateChanged(false, LocationPermissionDecision.DENIED)
    permissionState.value =
        LocationPermissionState(
            isGranted = true,
            decision = LocationPermissionDecision.GRANTED,
        )
    composeTestRule.waitForIdle()

    verify(exploreViewModel, times(1))
        .onPermissionStateChanged(true, LocationPermissionDecision.GRANTED)
  }

  private fun permissionController(granted: Boolean): LocationPermissionController {
    val controller = mock<LocationPermissionController>()
    whenever(controller.state)
        .thenReturn(
            MutableStateFlow(
                LocationPermissionState(
                    isGranted = granted,
                    decision =
                        if (granted) LocationPermissionDecision.GRANTED
                        else LocationPermissionDecision.DENIED,
                )
            )
        )
    return controller
  }
}
