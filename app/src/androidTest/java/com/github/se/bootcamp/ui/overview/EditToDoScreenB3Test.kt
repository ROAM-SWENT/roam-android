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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EditToDoScreenB3Test : InMemoryBootcampTest(BootcampMilestone.B3) {
  @get:Rule val composeTestRule = createComposeRule()

  @Before
  override fun setUp() {
    super.setUp()
    runBlocking { FirebaseEmulator.auth.signInAnonymously().await() }
    runBlocking { repository.addTodo(todo1) }
    composeTestRule.setContent { BootcampApp() }
    composeTestRule.navigateToEditToDoScreen(todo1)
  }

  @Test
  fun canEnterLocationAndSeeSuggestions() {
    composeTestRule.enterEditTodoLocation(FakeLocation.EPFL)
    composeTestRule.waitUntil(UI_WAIT_TIMEOUT) {
      composeTestRule
          .onAllNodesWithTag(EditToDoScreenTestTags.LOCATION_SUGGESTION)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeTestRule.assertAllLocationSuggestionsAreDisplayed(FakeLocation.EPFL)
  }

  @Test
  fun noSuggestionsForUnknownLocation() {
    composeTestRule.enterEditTodoLocation(FakeLocation.NOWHERE)
    composeTestRule.waitUntil {
      composeTestRule
          .onAllNodesWithTag(EditToDoScreenTestTags.LOCATION_SUGGESTION)
          .fetchSemanticsNodes()
          .isEmpty()
    }

    composeTestRule
        .onAllNodesWithTag(EditToDoScreenTestTags.LOCATION_SUGGESTION)
        .assertCountEquals(0)
  }
}
