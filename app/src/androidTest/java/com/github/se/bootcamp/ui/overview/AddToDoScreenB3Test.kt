package com.github.se.bootcamp.ui.overview

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.github.se.bootcamp.BootcampApp
import com.github.se.bootcamp.utils.BootcampMilestone
import com.github.se.bootcamp.utils.FakeHttpClient.FakeLocation
import com.github.se.bootcamp.utils.FirebaseEmulator
import com.github.se.bootcamp.utils.InMemoryBootcampTest
import com.github.se.bootcamp.utils.UI_WAIT_TIMEOUT
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AddToDoScreenB3Test : InMemoryBootcampTest(BootcampMilestone.B3) {
  @get:Rule val composeTestRule = createComposeRule()

  @Before
  override fun setUp() {
    super.setUp()
    runBlocking { FirebaseEmulator.auth.signInAnonymously().await() }
    composeTestRule.setContent { BootcampApp() }
    composeTestRule.navigateToAddToDoScreen()
  }

  @Test
  fun canEnterLocationAndSeeSuggestions() {
    composeTestRule.enterAddTodoLocation(FakeLocation.EPFL)
    composeTestRule.waitUntil(UI_WAIT_TIMEOUT) {
      composeTestRule
          .onAllNodesWithTag(AddToDoScreenTestTags.LOCATION_SUGGESTION)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeTestRule.assertAllLocationSuggestionsAreDisplayed(FakeLocation.EPFL)
  }

  @Test
  fun numberOfLocationSuggestionsIsLimited() {
    {
      composeTestRule.enterAddTodoLocation(FakeLocation.EVERYWHERE)
      composeTestRule.waitUntil {
        composeTestRule
            .onAllNodesWithTag(AddToDoScreenTestTags.LOCATION_SUGGESTION)
            .fetchSemanticsNodes()
            .isNotEmpty()
      }

      val numSuggestions =
          composeTestRule
              .onAllNodesWithTag(AddToDoScreenTestTags.LOCATION_SUGGESTION)
              .fetchSemanticsNodes()
              .size

      assertTrue(numSuggestions > 1)
      assertTrue(numSuggestions <= MAX_LOCATION_SUGGESTIONS_DISPLAYED)
    }
  }

  @Test
  fun noSuggestionsForUnknownLocation() {
    composeTestRule.enterAddTodoLocation(FakeLocation.NOWHERE)
    composeTestRule.waitUntil {
      composeTestRule
          .onAllNodesWithTag(AddToDoScreenTestTags.LOCATION_SUGGESTION)
          .fetchSemanticsNodes()
          .isEmpty()
    }

    composeTestRule
        .onAllNodesWithTag(AddToDoScreenTestTags.LOCATION_SUGGESTION)
        .assertCountEquals(0)
  }
}
