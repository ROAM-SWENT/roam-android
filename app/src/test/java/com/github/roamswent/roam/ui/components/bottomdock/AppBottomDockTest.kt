/*
 * Co-authored-by: Mistral Vibe <vibe@mistral.ai>
 */

package com.github.roamswent.roam.ui.components.bottomdock

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
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
import org.junit.Assert.assertNotEquals
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
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_friends).assertIsDisplayed()
  }

  @Test
  fun dockSurfaceFollowsLightAndDarkTheme() {
    var darkTheme by mutableStateOf(false)
    var lightSurface = 0
    var darkSurface = 0

    composeTestRule.setContent {
      SampleAppTheme(darkTheme = darkTheme, dynamicColor = false) {
        if (darkTheme) darkSurface = MaterialTheme.colorScheme.surface.toArgb()
        else lightSurface = MaterialTheme.colorScheme.surface.toArgb()
        AppBottomDock(
            selected = DockDestination.HOME,
            onDestinationSelected = {},
            onScanClick = {},
        )
      }
    }

    val dock = composeTestRule.onNodeWithTag(C.Tag.dock_container)
    val lightDock = dock.captureToImage().asAndroidBitmap()
    assertEquals(lightSurface, lightDock.getPixel(0, lightDock.height - 1))

    composeTestRule.runOnIdle { darkTheme = true }

    val darkDock = dock.captureToImage().asAndroidBitmap()
    assertEquals(darkSurface, darkDock.getPixel(0, darkDock.height - 1))
    assertNotEquals(lightSurface, darkSurface)
  }

  @Test
  fun tabAndScanLabelsRender() {
    setDock()

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).assertTextEquals("Home")
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).assertTextEquals("Explore")
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_friends).assertTextEquals("Friends")
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).assertTextEquals("Collection")
    composeTestRule.onNodeWithTag(C.Tag.dock_scan_label).assertTextEquals("Scan a monument")
  }

  /**
   * AutoResizedTabLabel shrinks overlong labels instead of wrapping them. The dock is squeezed just
   * enough that "Home" still fits at the initial font size while "Explore" and "Collection"
   * overflow their slots and must scale down: home is the untouched single-line baseline and the
   * overlong labels take the autosize path in the same render. The full text stays exposed through
   * semantics, and because a wrapped label would grow its tab column past the single-line height
   * the home tab pins, an overlong tab taller than home betrays a wrap. No pixel assertions.
   */
  @Test
  fun narrowedDockKeepsOverlongTabLabelsOnOneLine() {
    setDock(modifier = Modifier.width(340.dp))

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
   * state is the real contract: the selected tab is disabled and every other tab stays enabled.
   */
  private fun assertTabSelectionMarked(selected: DockDestination) {
    setDock(selected = selected)

    composeTestRule.onNodeWithTag(selected.tag).assertIsNotEnabled()
    DockDestination.entries
        .filterNot { it == selected }
        .forEach { destination -> composeTestRule.onNodeWithTag(destination.tag).assertIsEnabled() }
  }

  @Test
  fun selectedHomeTabIsMarkedByDisablingIt() {
    assertTabSelectionMarked(DockDestination.HOME)
  }

  @Test
  fun selectedSettingsTabIsMarkedByDisablingIt() {
    assertTabSelectionMarked(DockDestination.FRIENDS)
  }

  @Test
  fun selectedExploreTabIsMarkedByDisablingIt() {
    assertTabSelectionMarked(DockDestination.EXPLORE)
  }

  @Test
  fun selectedCollectionTabIsMarkedByDisablingIt() {
    assertTabSelectionMarked(DockDestination.COLLECTION)
  }

  @Test
  fun tappingHomeTabInvokesCallbackWithHome() {
    val destinations = mutableListOf<DockDestination>()
    setDock(
        selected = DockDestination.FRIENDS,
        onDestinationSelected = { destinations.add(it) },
    )

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).performClick()

    assertEquals(listOf(DockDestination.HOME), destinations)
  }

  @Test
  fun tappingSettingsTabInvokesCallbackWithSettings() {
    val destinations = mutableListOf<DockDestination>()
    setDock(onDestinationSelected = { destinations.add(it) })

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_friends).performClick()

    assertEquals(listOf(DockDestination.FRIENDS), destinations)
  }

  @Test
  fun tappingSelectedTabDoesNotInvokeCallback() {
    val destinations = mutableListOf<DockDestination>()
    setDock(onDestinationSelected = { destinations.add(it) })

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_home).performClick()

    assertTrue(destinations.isEmpty())
  }

  @Test
  fun tappingExploreAndCollectionTabsInvokesCallbackPerTap() {
    val destinations = mutableListOf<DockDestination>()
    setDock(onDestinationSelected = { destinations.add(it) })

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).assertIsEnabled()

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).performClick()
    assertEquals(listOf(DockDestination.EXPLORE), destinations)

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).performClick()
    assertEquals(listOf(DockDestination.EXPLORE, DockDestination.COLLECTION), destinations)

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).performClick()
    assertEquals(
        listOf(DockDestination.EXPLORE, DockDestination.COLLECTION, DockDestination.EXPLORE),
        destinations,
    )
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

    composeTestRule.onNodeWithTag(C.Tag.dock_scan_button).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).assertIsEnabled()

    composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore).performClick()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_collection).performClick()
    composeTestRule.onNodeWithTag(C.Tag.dock_tab_friends).performClick()

    assertEquals(
        listOf(DockDestination.EXPLORE, DockDestination.COLLECTION, DockDestination.FRIENDS),
        destinations,
    )
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
