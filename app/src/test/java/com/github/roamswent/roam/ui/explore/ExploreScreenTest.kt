/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 * Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
 */

package com.github.roamswent.roam.ui.explore

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.intercept.Interceptor
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.model.permission.LocationPermissionDecision
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.permission.LocationPermissionController
import com.github.roamswent.roam.ui.permission.LocationPermissionState
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
class ExploreScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  @After
  fun resetImageLoader() {
    Coil.reset()
  }

  /** Verifies that the explore title and map container are shown when location is available. */
  @Test
  fun displaysExploreTitleAndMapContainerWhenLocationIsAvailable() {
    val exploreViewModel = mock<ExploreViewModel>()
    whenever(exploreViewModel.uiState)
        .thenReturn(
            MutableStateFlow(ExploreUiState.LocationAvailable(Coordinates(48.85837, 2.29448)))
        )
    whenever(exploreViewModel.monuments).thenReturn(MutableStateFlow(emptyList()))
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

  /**
   * Verifies that the shown map reads the saved monuments from the view model. Google Maps markers
   * expose no Compose semantics, so the markers built from these monuments are checked in
   * [ExploreMapMarkersTest].
   */
  @Test
  fun readsSavedMonumentsWhenMapIsShown() {
    val exploreViewModel = mock<ExploreViewModel>()
    whenever(exploreViewModel.uiState)
        .thenReturn(
            MutableStateFlow(ExploreUiState.LocationAvailable(Coordinates(48.85837, 2.29448)))
        )
    whenever(exploreViewModel.monuments)
        .thenReturn(
            MutableStateFlow(
                listOf(
                    ExploreMonument("guide-1", "Eiffel Tower", Coordinates(48.85837, 2.29448)),
                    ExploreMonument("guide-2", "Colosseum", Coordinates(41.89021, 12.49223)),
                )
            )
        )
    composeTestRule.setContent {
      SampleAppTheme {
        ExploreScreen(
            onBackClick = {},
            exploreViewModel = exploreViewModel,
            locationPermissionController = permissionController(granted = true),
        )
      }
    }

    composeTestRule.onAllNodesWithTag(C.Tag.explore_map).assertCountEquals(1)
    verify(exploreViewModel, atLeastOnce()).monuments
  }

  /** Verifies that the monument card displays its content and invokes dismissal when closed. */
  @Test
  fun monumentCardDisplaysContentAndDismissesWhenCloseIsClicked() {
    var dismissed = false
    val monument =
        ExploreMonument(
            guideId = "guide-1",
            name = "Eiffel Tower",
            coordinates = Coordinates(48.85837, 2.29448),
            description = "Built for the 1889 exposition.",
        )

    composeTestRule.setContent {
      SampleAppTheme { ExploreMonumentCard(monument = monument, onDismiss = { dismissed = true }) }
    }

    composeTestRule.onNodeWithText("Eiffel Tower").assertIsDisplayed()
    composeTestRule.onNodeWithText("Built for the 1889 exposition.").assertIsDisplayed()
    composeTestRule
        .onNodeWithContentDescription("Close monument information")
        .assertIsDisplayed()
        .performClick()

    assertEquals(true, dismissed)
  }

  /** Verifies that a monument without photo bytes shows the image placeholder. */
  @Test
  fun monumentCardShowsPlaceholderWhenPhotoIsMissing() {
    val monument =
        ExploreMonument(
            guideId = "guide-1",
            name = "Eiffel Tower",
            coordinates = Coordinates(48.85837, 2.29448),
            photoBytes = ByteArray(0),
        )

    composeTestRule.setContent {
      SampleAppTheme { ExploreMonumentCard(monument = monument, onDismiss = {}) }
    }

    composeTestRule
        .onNodeWithTag(C.Tag.explore_monument_photo_placeholder, useUnmergedTree = true)
        .assertIsDisplayed()
  }

  /** Verifies that a photo decoding failure shows the image placeholder. */
  @Test
  fun monumentCardShowsPlaceholderWhenPhotoCannotBeDecoded() {
    Coil.setImageLoader(
        ImageLoader.Builder(ApplicationProvider.getApplicationContext())
            .components { add(Interceptor { error("forced image failure") }) }
            .build()
    )
    val monument =
        ExploreMonument(
            guideId = "guide-1",
            name = "Eiffel Tower",
            coordinates = Coordinates(48.85837, 2.29448),
            photoBytes = byteArrayOf(1, 2, 3),
        )

    composeTestRule.setContent {
      SampleAppTheme { ExploreMonumentCard(monument = monument, onDismiss = {}) }
    }

    composeTestRule.waitUntil(5_000) {
      composeTestRule
          .onAllNodesWithTag(C.Tag.explore_monument_photo_error, useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeTestRule
        .onNodeWithTag(C.Tag.explore_monument_photo_error, useUnmergedTree = true)
        .assertIsDisplayed()
  }

  /** Verifies that saved monuments are not read while the map is not shown. */
  @Test
  fun doesNotReadMonumentsWithoutLocationPermission() {
    val exploreViewModel = mock<ExploreViewModel>()
    whenever(exploreViewModel.uiState)
        .thenReturn(
            MutableStateFlow(ExploreUiState.PermissionRequired(LocationPermissionDecision.DENIED))
        )
    composeTestRule.setContent {
      SampleAppTheme {
        ExploreScreen(
            onBackClick = {},
            exploreViewModel = exploreViewModel,
            locationPermissionController = permissionController(granted = false),
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onAllNodesWithTag(C.Tag.explore_map).assertCountEquals(0)
    verify(exploreViewModel, never()).monuments
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun exploreTitleUsesOnSurfaceColorInDarkTheme() {
    val exploreViewModel = mock<ExploreViewModel>()
    whenever(exploreViewModel.uiState).thenReturn(MutableStateFlow(ExploreUiState.Loading))
    val locationPermissionController = permissionController(granted = true)
    var onSurfaceColor = 0

    composeTestRule.setContent {
      SampleAppTheme(darkTheme = true, dynamicColor = false) {
        onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
        ExploreScreen(
            onBackClick = {},
            exploreViewModel = exploreViewModel,
            locationPermissionController = locationPermissionController,
        )
      }
    }

    val title = composeTestRule.onNodeWithText("Explore")
    title.assertIsDisplayed()
    val titleImage = title.captureToImage().asAndroidBitmap()
    var foundOnSurfaceColor = false
    for (y in 0 until titleImage.height) {
      for (x in 0 until titleImage.width) {
        if (titleImage.getPixel(x, y) == onSurfaceColor) foundOnSurfaceColor = true
      }
    }
    assertTrue(foundOnSurfaceColor)
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
