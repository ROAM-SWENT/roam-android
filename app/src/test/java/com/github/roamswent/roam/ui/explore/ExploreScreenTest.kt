/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.explore

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.model.permission.LocationPermissionDecision
import com.github.roamswent.roam.resources.C
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

  /** Verifies that the explore title and map container are shown when location is available. */
  @Test
  fun displaysExploreTitleAndMapContainerWhenLocationIsAvailable() {
    val exploreViewModel = mock<ExploreViewModel>()
    whenever(exploreViewModel.uiState)
        .thenReturn(
            MutableStateFlow(ExploreUiState.LocationAvailable(Coordinates(48.85837, 2.29448)))
        )
    val locationPermissionController = permissionController(granted = true)
    composeTestRule.setContent {
      SampleAppTheme {
        ExploreScreen(
            onBackClick = {},
            exploreViewModel = exploreViewModel,
            locationPermissionController = locationPermissionController,
        )
      }
    }

    composeTestRule.onNodeWithText("Explore").assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.explore_map).assertCountEquals(1)
  }

  /** Verifies that clicking the back button invokes the provided callback. */
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

  /** Verifies that a loading indicator is shown while the location is being loaded. */
  @Test
  fun displaysLoadingState() {
    val exploreViewModel = mock<ExploreViewModel>()
    whenever(exploreViewModel.uiState).thenReturn(MutableStateFlow(ExploreUiState.Loading))
    val locationPermissionController = permissionController(granted = true)
    composeTestRule.setContent {
      SampleAppTheme {
        ExploreScreen(
            onBackClick = {},
            exploreViewModel = exploreViewModel,
            locationPermissionController = locationPermissionController,
        )
      }
    }

    composeTestRule.onNodeWithText("Loading your location…").assertIsDisplayed()
  }

  /** Verifies that denied permission displays guidance without an additional request action. */
  @Test
  fun displaysPermissionGuidanceWithoutRequestAction() {
    val exploreViewModel = mock<ExploreViewModel>()
    whenever(exploreViewModel.uiState)
        .thenReturn(
            MutableStateFlow(ExploreUiState.PermissionRequired(LocationPermissionDecision.DENIED))
        )
    val locationPermissionController = permissionController(granted = false)
    composeTestRule.setContent {
      SampleAppTheme {
        ExploreScreen(
            onBackClick = {},
            exploreViewModel = exploreViewModel,
            locationPermissionController = locationPermissionController,
        )
      }
    }

    composeTestRule
        .onNodeWithText("Please activate location permission to explore nearby places.")
        .assertIsDisplayed()
    composeTestRule.onAllNodes(hasClickAction()).assertCountEquals(1)
  }

  /** Verifies that the unavailable-location message and retry action are shown. */
  @Test
  fun displaysUnavailableState() {
    val exploreViewModel = mock<ExploreViewModel>()
    whenever(exploreViewModel.uiState)
        .thenReturn(MutableStateFlow(ExploreUiState.LocationUnavailable))
    val locationPermissionController = permissionController(granted = true)
    composeTestRule.setContent {
      SampleAppTheme {
        ExploreScreen(
            onBackClick = {},
            exploreViewModel = exploreViewModel,
            locationPermissionController = locationPermissionController,
        )
      }
    }

    composeTestRule.onNodeWithText("We could not determine your location.").assertIsDisplayed()
    composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
  }

  /** Verifies that clicking retry asks the view model to retry locating the user. */
  @Test
  fun retryButtonRetriesLocation() {
    val exploreViewModel = mock<ExploreViewModel>()
    whenever(exploreViewModel.uiState)
        .thenReturn(MutableStateFlow(ExploreUiState.LocationUnavailable))
    val locationPermissionController = permissionController(granted = true)
    composeTestRule.setContent {
      SampleAppTheme {
        ExploreScreen(
            onBackClick = {},
            exploreViewModel = exploreViewModel,
            locationPermissionController = locationPermissionController,
        )
      }
    }

    composeTestRule.onNodeWithText("Retry").performClick()

    verify(exploreViewModel).retryLocation()
  }

  /** Verifies that permission state changes are forwarded to the view model. */
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
