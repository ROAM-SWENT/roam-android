/*
 * Co-authored-by: Mistral Vibe <vibe@mistral.ai>
 */

package com.github.roamswent.roam.ui.components.bottomdock

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
      modifier: Modifier = Modifier,
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
            modifier = modifier,
        )
      }
    }
  }

  @Test
  fun dockDisplaysAllEntries() {
    setDock()

    composeTestRule.onNodeWithTag(C.Tag.dock_container).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dock_scan_button).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dock_scan_label).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_settings).assertIsDisplayed()
  }

  @Test
  fun tabAndScanLabelsRender() {
    setDock()

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).assertTextEquals("Home")
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).assertTextEquals("Explore")
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_settings).assertTextEquals("Profile")
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).assertTextEquals("Collection")
    composeTestRule.onNodeWithTag(C.Tag.dock_scan_label).assertTextEquals("Scan a monument")
  }

  /**
   * AutoResizedTabLabel shrinks overlong labels instead of wrapping them. The dock is squeezed far
   * below the width its labels need, so "Explore" and "Collection" overflow their slots at the
   * initial font size and must scale down. The full text stays exposed through semantics, and
   * because every tab column settles at the same height while its label is a single line, a wrapped
   * label would make its tab strictly taller than the home tab. Equal heights pin the one-line
   * contract without pixel assertions.
   */
  @Test
  fun narrowedDockKeepsOverlongTabLabelsOnOneLine() {
    setDock(modifier = Modifier.width(200.dp))

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).assertTextEquals("Explore")
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).assertTextEquals("Collection")

    val homeTabHeight =
        composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).fetchSemanticsNode().size.height
    val collectionTabHeight =
        composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).fetchSemanticsNode().size.height
    assertTrue(collectionTabHeight <= homeTabHeight)
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

  /**
   * The selection marking is a live-tab contract. Passing an unwired destination as selected marks
   * no tab: both live tabs stay enabled and the unwired ones stay disabled, so the enum entries
   * without a route can never pose as the highlighted tab.
   */
  @Test
  fun selectingUnwiredDestinationMarksNoLiveTab() {
    setDock(selected = DockDestination.EXPLORE)

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_settings).assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).assertIsNotEnabled()
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

  /**
   * EXPLORE and COLLECTION have no route yet, so their tabs must stay inert: disabled, and taps
   * never reach onDestinationSelected. Once their routes are wired this contract converts into live
   * navigation tests. The settings tab stays enabled in the same render, pinning that the unwired
   * slots do not leak their state onto the live ones.
   */
  @Test
  fun unwiredTabsAreDisabledAndIgnoreTaps() {
    val destinations = mutableListOf<DockDestination>()
    setDock(onDestinationSelected = { destinations.add(it) })

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_settings).assertIsEnabled()

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).performClick()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).performClick()

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
