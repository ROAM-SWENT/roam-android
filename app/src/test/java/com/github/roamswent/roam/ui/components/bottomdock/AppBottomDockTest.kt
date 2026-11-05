/*
 * Co-authored-by: Mistral Vibe <vibe@mistral.ai>
 */

package com.github.roamswent.roam.ui.components.bottomdock

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class AppBottomDockTest {
  @get:Rule val composeTestRule = createComposeRule()

  /**
   * Renders the dock with the given behavior hooks. Selection and camera availability defaults
   * mirror the production defaults.
   */
  private fun setDock(
      selected: DockDestination = DockDestination.HOME,
      cameraAvailable: Boolean = true,
      onDestinationSelected: (DockDestination) -> Unit = {},
      onScanClick: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      SampleAppTheme {
        AppBottomDock(
            selected = selected,
            onDestinationSelected = onDestinationSelected,
            onScanClick = onScanClick,
            cameraAvailable = cameraAvailable,
        )
      }
    }
  }

  @Test
  fun dockDisplaysAllEntries() {
    setDock()

    composeTestRule.onNodeWithTag(C.Tag.dock_container).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dock_scan_button).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dock_scan_label).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_settings).assertIsDisplayed()
  }

  @Test
  fun tabAndScanLabelsRender() {
    setDock()

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).assertTextEquals("Home")
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_settings).assertTextEquals("Profile")
    composeTestRule.onNodeWithTag(C.Tag.dock_scan_label).assertTextEquals("Scan a monument")
  }

  /**
   * The dock exposes the selection through the click affordance: only the unselected tab accepts
   * taps. Tab tint and weight are paint-only details that semantics cannot observe, so the enabled
   * state is the real contract.
   */
  @Test
  fun selectedHomeTabIsMarkedByDisablingIt() {
    setDock(selected = DockDestination.HOME)

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_settings).assertIsEnabled()
  }

  @Test
  fun selectedSettingsTabIsMarkedByDisablingIt() {
    setDock(selected = DockDestination.SETTINGS)

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_settings).assertIsNotEnabled()
  }

  @Test
  fun tappingHomeTabInvokesCallbackWithHome() {
    val destinations = mutableListOf<DockDestination>()
    setDock(
        selected = DockDestination.SETTINGS,
        onDestinationSelected = { destinations.add(it) },
    )

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).performClick()

    assertEquals(listOf(DockDestination.HOME), destinations)
  }

  @Test
  fun tappingSettingsTabInvokesCallbackWithSettings() {
    val destinations = mutableListOf<DockDestination>()
    setDock(onDestinationSelected = { destinations.add(it) })

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_settings).performClick()

    assertEquals(listOf(DockDestination.SETTINGS), destinations)
  }

  @Test
  fun tappingSelectedTabDoesNotInvokeCallback() {
    val destinations = mutableListOf<DockDestination>()
    setDock(onDestinationSelected = { destinations.add(it) })

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).performClick()

    assertTrue(destinations.isEmpty())
  }

  @Test
  fun scanButtonInvokesScanCallbackOncePerClick() {
    var scanClicks = 0
    val destinations = mutableListOf<DockDestination>()
    setDock(
        onDestinationSelected = { destinations.add(it) },
        onScanClick = { scanClicks++ },
    )

    val scanButton = composeTestRule.onNodeWithTag(C.Tag.dock_scan_button)
    scanButton.performClick()
    assertEquals(1, scanClicks)
    scanButton.performClick()
    assertEquals(2, scanClicks)

    assertTrue(destinations.isEmpty())
  }

  @Test
  fun unavailableCameraDisablesScanButtonAndShowsUnavailableLabel() {
    var scanClicks = 0
    setDock(cameraAvailable = false, onScanClick = { scanClicks++ })

    composeTestRule.onNodeWithTag(C.Tag.dock_scan_button).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.dock_scan_button).performClick()
    assertEquals(0, scanClicks)

    composeTestRule
        .onNodeWithTag(C.Tag.dock_scan_label)
        .assertTextEquals("No camera detected on this device")
  }

  @Test
  fun tabsRemainClickableWhenCameraUnavailable() {
    val destinations = mutableListOf<DockDestination>()
    setDock(
        cameraAvailable = false,
        onDestinationSelected = { destinations.add(it) },
    )

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_settings).performClick()

    assertEquals(listOf(DockDestination.SETTINGS), destinations)
  }

  /**
   * Forces a real draw pass so the paint pipeline (bar, halo, outline path) executes. Asserts only
   * that the dock draws without crashing and produces content; the frame must have a size because
   * the dock fills the test window's width.
   */
  @Test
  fun dockDrawsItsFrame() {
    setDock()

    val frame = composeTestRule.onNodeWithTag(C.Tag.dock_container).captureToImage()

    assertTrue(frame.width > 0)
    assertTrue(frame.height > 0)
  }
}
