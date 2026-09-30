package com.github.se.bootcamp.ui

// ***************************************************************************** //
// ***                                                                       *** //
// *** THIS FILE WILL BE OVERWRITTEN DURING GRADING. IT SHOULD BE LOCATED IN *** //
// *** `app/src/androidTest/java/com/github/se/bootcamp/ui`.                   *** //
// *** DO **NOT** IMPLEMENT YOUR OWN TESTS IN THIS FILE                      *** //
// ***                                                                       *** //
// ***************************************************************************** //

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GreetingScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun testTagsAreCorrectlySet() {
    composeTestRule.setContent { GreetingScreen() }

    composeTestRule.onNodeWithTag(GreetingScreenTestTags.NAME_INPUT).assertIsDisplayed()
    composeTestRule.onNodeWithTag(GreetingScreenTestTags.BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(GreetingScreenTestTags.GREETING_MESSAGE).assertIsDisplayed()
  }

  @Test
  fun displayHasCorrectDefaultValue() {
    composeTestRule.setContent { GreetingScreen() }

    composeTestRule
        .onNodeWithTag(GreetingScreenTestTags.GREETING_MESSAGE)
        .assertTextEquals("What's your name ?")
  }

  @Test
  fun displayCorrectlyUpdates() {
    composeTestRule.setContent { GreetingScreen() }

    composeTestRule.onNodeWithTag(GreetingScreenTestTags.NAME_INPUT).performTextInput("John Doe")
    composeTestRule.onNodeWithTag(GreetingScreenTestTags.BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(GreetingScreenTestTags.GREETING_MESSAGE)
        .assertTextContains("Hi John Doe")
  }

  @Test
  fun clickIsRequiredToUpdateDisplay() {
    composeTestRule.setContent { GreetingScreen() }

    composeTestRule.onNodeWithTag(GreetingScreenTestTags.NAME_INPUT).performTextInput("John Doe")
    composeTestRule.onNodeWithTag(GreetingScreenTestTags.BUTTON).performClick()

    composeTestRule.onNodeWithTag(GreetingScreenTestTags.NAME_INPUT).performTextInput("John Smith")
    composeTestRule
        .onNodeWithTag(GreetingScreenTestTags.GREETING_MESSAGE)
        .assertTextContains("Hi John Doe")
  }

  @Test
  fun doNotGreetEmptyName() {
    composeTestRule.setContent { GreetingScreen() }

    composeTestRule.onNodeWithTag(GreetingScreenTestTags.NAME_INPUT).performTextInput("")
    composeTestRule.onNodeWithTag(GreetingScreenTestTags.BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(GreetingScreenTestTags.GREETING_MESSAGE)
        .assertTextEquals("What's your name ?")
  }
}
