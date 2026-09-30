package com.github.se.bootcamp.ui.map

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.se.bootcamp.BootcampApp
import com.github.se.bootcamp.model.map.Location
import com.github.se.bootcamp.ui.navigation.NavigationTestTags
import com.github.se.bootcamp.utils.BootcampMilestone
import com.github.se.bootcamp.utils.FirebaseEmulator
import com.github.se.bootcamp.utils.InMemoryBootcampTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MapScreenTagsPresenceTest : InMemoryBootcampTest(BootcampMilestone.B3) {

  @get:Rule val composeTestRule = createComposeRule()

  @Before
  override fun setUp() {
    super.setUp()
    runBlocking { FirebaseEmulator.auth.signInAnonymously().await() }
    runBlocking { repository.addTodo(todo1.copy(location = Location(46.520278, 6.565556, "EPFL"))) }
    composeTestRule.setContent { BootcampApp() }
  }

  @Test
  fun mapScreen_allExpectedTestTags_exist() {

    composeTestRule
        .onNodeWithTag(NavigationTestTags.MAP_TAB, useUnmergedTree = true)
        .assertExists()
        .performClick()

    composeTestRule.checkMapScreenIsDisplayed()

    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE, useUnmergedTree = true)
        .assertExists()

    composeTestRule
        .onNodeWithTag(MapScreenTestTags.GOOGLE_MAP_SCREEN, useUnmergedTree = true)
        .assertExists()

    composeTestRule
        .onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU, useUnmergedTree = true)
        .assertExists()
  }
}
