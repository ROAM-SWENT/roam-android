package com.github.se.bootcamp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.se.bootcamp.BootcampApp
import com.github.se.bootcamp.ui.overview.OverviewScreenTestTags
import com.github.se.bootcamp.utils.BootcampMilestone
import com.github.se.bootcamp.utils.InMemoryBootcampTest
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// ***************************************************************************** //
// ***                                                                       *** //
// *** THIS FILE WILL BE OVERWRITTEN DURING GRADING. IT SHOULD BE LOCATED IN *** //
// *** `app/src/androidTest/java/com/github/se/bootcamp/ui/navigation`.        *** //
// *** DO **NOT** IMPLEMENT YOUR OWN TESTS IN THIS FILE                      *** //
// ***                                                                       *** //
// ***************************************************************************** //

class NavigationB1Test : InMemoryBootcampTest(BootcampMilestone.B1) {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  override fun setUp() {
    super.setUp()
    composeTestRule.setContent { BootcampApp() }
  }

  @Test
  fun testTagsAreCorrectlySet() {
    composeTestRule.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.OVERVIEW_TAB).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.MAP_TAB).assertIsDisplayed()
  }

  @Test
  fun bottomNavigationIsDisplayedForOverview() {
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
  }

  @Test
  fun bottomNavigationIsDisplayedForMap() {
    composeTestRule.onNodeWithTag(NavigationTestTags.MAP_TAB).performClick()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
  }

  @Test
  fun tabsAreClickable() {
    composeTestRule
        .onNodeWithTag(NavigationTestTags.OVERVIEW_TAB)
        .assertIsDisplayed()
        .performClick()
    composeTestRule.onNodeWithTag(NavigationTestTags.MAP_TAB).assertIsDisplayed().performClick()
  }

  @Test
  fun topBarTitleIsCorrectForOverview() {
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextContains(value = "Overview")
  }

  @Test
  fun topBarTitleIsCorrectForMap() {
    composeTestRule.onNodeWithTag(NavigationTestTags.MAP_TAB).performClick()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextContains(value = "Map")
  }

  @Test
  fun topBarTitleIsCorrectForAddToDo() {
    composeTestRule.onNodeWithTag(OverviewScreenTestTags.CREATE_TODO_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextContains(value = "Create a new task", substring = false, ignoreCase = true)
  }

  @Test
  fun bottomNavigationNotDisplayedForAddToDo() {
    composeTestRule.onNodeWithTag(OverviewScreenTestTags.CREATE_TODO_BUTTON).performClick()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertDoesNotExist()
  }

  @Test
  fun navigationStartsOnOverviewTab() {
    composeTestRule.checkOverviewScreenIsDisplayed()
  }

  @Test
  fun canNavigateToMap() {
    composeTestRule.onNodeWithTag(NavigationTestTags.MAP_TAB).performClick()
    composeTestRule.checkMapScreenIsDisplayed()
    composeTestRule.checkOverviewScreenIsNotDisplayed()
  }

  @Test
  fun canNavigateToMapAndBackToOverview() {
    composeTestRule.onNodeWithTag(NavigationTestTags.MAP_TAB).performClick()
    composeTestRule.onNodeWithTag(NavigationTestTags.OVERVIEW_TAB).performClick()
    composeTestRule.checkOverviewScreenIsDisplayed()
  }

  //  @Test
  fun canNavigateBackToMapAndBackToOverviewUsingSystemBack() {
    composeTestRule.onNodeWithTag(NavigationTestTags.MAP_TAB).performClick()
    composeTestRule.checkOverviewScreenIsNotDisplayed()
    composeTestRule.checkMapScreenIsDisplayed()
    pressBack(shouldFinish = false)
    composeTestRule.checkOverviewScreenIsDisplayed()
  }

  @Test
  fun canNavigateToAddToDo() {
    composeTestRule.onNodeWithTag(OverviewScreenTestTags.CREATE_TODO_BUTTON).performClick()
    composeTestRule.checkAddToDoScreenIsDisplayed()
    composeTestRule.checkOverviewScreenIsNotDisplayed()
  }

  @Test
  fun canNavigateBackToOverviewFromAddToDo() {
    composeTestRule.onNodeWithTag(OverviewScreenTestTags.CREATE_TODO_BUTTON).performClick()
    composeTestRule.checkAddToDoScreenIsDisplayed()
    composeTestRule.checkOverviewScreenIsNotDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    composeTestRule.checkOverviewScreenIsDisplayed()
  }

  //  @Test
  fun canNavigateBackToOverviewFromAddToDoUsingSystemBack() {
    composeTestRule.onNodeWithTag(OverviewScreenTestTags.CREATE_TODO_BUTTON).performClick()
    composeTestRule.checkAddToDoScreenIsDisplayed()
    composeTestRule.checkOverviewScreenIsNotDisplayed()
    pressBack(shouldFinish = false)
    composeTestRule.checkOverviewScreenIsDisplayed()
  }

  @Test
  fun canNavigateBetweenTabs() {
    composeTestRule.onNodeWithTag(NavigationTestTags.OVERVIEW_TAB).performClick()
    composeTestRule.checkOverviewScreenIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.MAP_TAB).performClick()
    composeTestRule.checkMapScreenIsDisplayed()
    composeTestRule.checkOverviewScreenIsNotDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.OVERVIEW_TAB).performClick()
    composeTestRule.checkOverviewScreenIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.MAP_TAB).performClick()
    composeTestRule.checkMapScreenIsDisplayed()
    composeTestRule.checkOverviewScreenIsNotDisplayed()
  }

  private fun pressBack(shouldFinish: Boolean) {
    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.onBackPressedDispatcher.onBackPressed()
    }
    composeTestRule.waitUntil { composeTestRule.activity.isFinishing == shouldFinish }
    assertEquals(shouldFinish, composeTestRule.activity.isFinishing)
  }
}
