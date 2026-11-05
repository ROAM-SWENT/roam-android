/*
 * Co-authored-by: Mistral Vibe <vibe@mistral.ai>
 */

package com.github.roamswent.roam.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
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

@RunWith(RobolectricTestRunner::class)
class HomeBottomBarTest {
  @get:Rule val composeTestRule = createComposeRule()

  private fun setBottomBar(
      selectedTab: HomeBottomTab,
      cameraAvailable: Boolean,
      onTabSelected: (HomeBottomTab) -> Unit,
      onScanClick: () -> Unit,
  ) {
    composeTestRule.setContent {
      SampleAppTheme {
        HomeBottomBar(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
            onScanClick = onScanClick,
            cameraAvailable = cameraAvailable,
        )
      }
    }
  }

  @Test
  fun bottomBarDisplaysAllThreeTabs() {
    setBottomBar(
        selectedTab = HomeBottomTab.HOME,
        cameraAvailable = true,
        onTabSelected = {},
        onScanClick = {},
    )

    composeTestRule.onNodeWithTag(C.Tag.home_bottom_bar).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.home_tab_home).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.home_tab_profile).assertIsDisplayed()
  }

  @Test
  fun sideTabLabelsRender() {
    setBottomBar(
        selectedTab = HomeBottomTab.HOME,
        cameraAvailable = true,
        onTabSelected = {},
        onScanClick = {},
    )

    composeTestRule.onNodeWithTag(C.Tag.home_tab_home).assertTextEquals("Home")
    composeTestRule.onNodeWithTag(C.Tag.home_tab_profile).assertTextEquals("Profile")
    composeTestRule
        .onNodeWithTag(C.Tag.home_scan_label, useUnmergedTree = true)
        .assertTextEquals("Scan a monument")
  }

  @Test
  fun eachSideTabClickInvokesCallbackWithCorrectEnum() {
    val selectedTabs = mutableListOf<HomeBottomTab>()
    var scanClicks = 0
    setBottomBar(
        selectedTab = HomeBottomTab.HOME,
        cameraAvailable = true,
        onTabSelected = { selectedTabs.add(it) },
        onScanClick = { scanClicks++ },
    )

    composeTestRule.onNodeWithTag(C.Tag.home_tab_home).performClick()
    assertEquals(listOf(HomeBottomTab.HOME), selectedTabs)

    composeTestRule.onNodeWithTag(C.Tag.home_tab_profile).performClick()
    assertEquals(listOf(HomeBottomTab.HOME, HomeBottomTab.PROFILE), selectedTabs)

    assertEquals(0, scanClicks)
  }

  @Test
  fun scanButtonClickInvokesScanCallbackOnly() {
    val selectedTabs = mutableListOf<HomeBottomTab>()
    var scanClicks = 0
    setBottomBar(
        selectedTab = HomeBottomTab.HOME,
        cameraAvailable = true,
        onTabSelected = { selectedTabs.add(it) },
        onScanClick = { scanClicks++ },
    )

    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).performClick()

    assertEquals(1, scanClicks)
    assertTrue(selectedTabs.isEmpty())
  }

  @Test
  fun scanButtonSupportsRepeatedClicks() {
    var scanClicks = 0
    setBottomBar(
        selectedTab = HomeBottomTab.HOME,
        cameraAvailable = true,
        onTabSelected = {},
        onScanClick = { scanClicks++ },
    )

    val scanButton = composeTestRule.onNodeWithTag(C.Tag.home_scan_button)
    scanButton.performClick()
    scanButton.performClick()
    assertEquals(2, scanClicks)
  }

  @Test
  fun unavailableCameraDisablesScanButton() {
    val selectedTabs = mutableListOf<HomeBottomTab>()
    var scanClicks = 0
    setBottomBar(
        selectedTab = HomeBottomTab.HOME,
        cameraAvailable = false,
        onTabSelected = { selectedTabs.add(it) },
        onScanClick = { scanClicks++ },
    )

    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).performClick()

    assertEquals(0, scanClicks)
  }

  @Test
  fun selectedTabProfileStillRendersAllTabs() {
    setBottomBar(
        selectedTab = HomeBottomTab.PROFILE,
        cameraAvailable = true,
        onTabSelected = {},
        onScanClick = {},
    )

    composeTestRule.onNodeWithTag(C.Tag.home_bottom_bar).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.home_tab_home).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.home_tab_profile).assertIsDisplayed()
  }

  @Test
  fun sideTabsRemainClickableWhenCameraUnavailable() {
    val selectedTabs = mutableListOf<HomeBottomTab>()
    setBottomBar(
        selectedTab = HomeBottomTab.HOME,
        cameraAvailable = false,
        onTabSelected = { selectedTabs.add(it) },
        onScanClick = {},
    )

    composeTestRule.onNodeWithTag(C.Tag.home_tab_home).performClick()
    assertEquals(listOf(HomeBottomTab.HOME), selectedTabs)

    composeTestRule.onNodeWithTag(C.Tag.home_tab_profile).performClick()
    assertEquals(listOf(HomeBottomTab.HOME, HomeBottomTab.PROFILE), selectedTabs)
  }
}
