package com.github.se.bootcamp.ui.overview

// ***************************************************************************** //
// ***                                                                       *** //
// *** THIS FILE WILL BE OVERWRITTEN DURING GRADING. IT SHOULD BE LOCATED IN *** //
// *** `app/src/androidTest/java/com/github/se/bootcamp/ui/overview/         *** //
// *** DO **NOT** IMPLEMENT YOUR OWN TESTS IN THIS FILE                      *** //
// ***                                                                       *** //
// ***************************************************************************** //

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.se.bootcamp.utils.BootcampMilestone
import com.github.se.bootcamp.utils.InMemoryBootcampTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AddToDoScreenB2Test : InMemoryBootcampTest(BootcampMilestone.B2) {
  @get:Rule val composeTestRule = createComposeRule()

  @Before
  override fun setUp() {
    super.setUp()
    composeTestRule.setContent { AddTodoScreen() }
  }

  @Test
  fun displayAllComponents() {
    composeTestRule
        .onNodeWithTag(AddToDoScreenTestTags.TODO_SAVE)
        .assertTextContains("Save", substring = true, ignoreCase = true)
    composeTestRule.onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_DESCRIPTION).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_ASSIGNEE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_LOCATION).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_DATE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AddToDoScreenTestTags.LOCATION_SUGGESTION).assertIsNotDisplayed()
    composeTestRule
        .onNodeWithTag(AddToDoScreenTestTags.ERROR_MESSAGE, useUnmergedTree = true)
        .assertIsNotDisplayed()
  }

  @Test
  fun canEnterTitle() {
    val text = "title"
    composeTestRule.enterAddTodoTitle(text)
    composeTestRule.onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_TITLE).assertTextContains(text)
    composeTestRule
        .onNodeWithTag(AddToDoScreenTestTags.ERROR_MESSAGE, useUnmergedTree = true)
        .assertIsNotDisplayed()
  }

  @Test
  fun canEnterDescription() {
    val text = "description"
    composeTestRule.enterAddTodoDescription(text)
    composeTestRule
        .onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_DESCRIPTION)
        .assertTextContains(text)
    composeTestRule
        .onNodeWithTag(AddToDoScreenTestTags.ERROR_MESSAGE, useUnmergedTree = true)
        .assertIsNotDisplayed()
  }

  @Test
  fun canEnterAssigneeName() {
    val text = "assignee"
    composeTestRule.enterAddTodoAssignee(text)
    composeTestRule
        .onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_ASSIGNEE)
        .assertTextContains(text)
    composeTestRule
        .onNodeWithTag(AddToDoScreenTestTags.ERROR_MESSAGE, useUnmergedTree = true)
        .assertIsNotDisplayed()
  }

  @Test
  fun canEnterLocation() {
    val text = "location"
    composeTestRule.enterAddTodoLocation(text)
    composeTestRule
        .onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_LOCATION)
        .assertTextContains(text)
    composeTestRule
        .onNodeWithTag(AddToDoScreenTestTags.ERROR_MESSAGE, useUnmergedTree = true)
        .assertIsNotDisplayed()
  }

  @Test
  fun canEnterAValidDate() {
    val text = "31/02/2023"
    composeTestRule.enterAddTodoDate(text)
    composeTestRule.onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_DATE).assertTextContains(text)
  }

  @Test
  fun canEnterAnInvalidDate() {
    val text = "This date is not valid"
    composeTestRule.enterAddTodoDate(text)
    composeTestRule.onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_DATE).assertTextContains(text)
  }

  @Test
  fun savingWithInvalidTitleShouldDoNothing() = checkNoTodoWereAdded {
    composeTestRule.enterAddTodoDetails(
        todo = todo1.copy(name = " ") // Title is mandatory
        )
    composeTestRule.clickOnSaveForAddTodo()
    composeTestRule.onNodeWithTag(AddToDoScreenTestTags.TODO_SAVE).assertIsDisplayed()
  }

  @Test
  fun savingWithInvalidDescriptionShouldDoNothing() = checkNoTodoWereAdded {
    composeTestRule.enterAddTodoDetails(
        todo = todo1.copy(description = " ") // Description is mandatory
        )
    composeTestRule.clickOnSaveForAddTodo()
    composeTestRule.onNodeWithTag(AddToDoScreenTestTags.TODO_SAVE).assertIsDisplayed()
  }

  @Test
  fun savingWithInvalidAssigneeShouldDoNothing() = checkNoTodoWereAdded {
    composeTestRule.enterAddTodoDetails(
        todo = todo1.copy(assigneeName = " ") // Assignee is mandatory
        )
    composeTestRule.clickOnSaveForAddTodo()
    composeTestRule.onNodeWithTag(AddToDoScreenTestTags.TODO_SAVE).assertIsDisplayed()
  }

  @Test
  fun savingWithInvalidDateShouldDoNothing() = checkNoTodoWereAdded {
    composeTestRule.enterAddTodoDetails(
        todo = todo1, date = "This is not a date" // Invalid date format
        )
    composeTestRule.clickOnSaveForAddTodo()
    composeTestRule.onNodeWithTag(AddToDoScreenTestTags.TODO_SAVE).assertIsDisplayed()
  }

  @Test
  fun enteringEmptyTitleShowsErrorMessage() {
    val invalidTitle = " " // Title is mandatory
    composeTestRule.enterAddTodoTitle(invalidTitle)
    composeTestRule.checkErrorMessageIsDisplayedForAddTodo()
  }

  @Test
  fun enteringEmptyDescriptionShowsErrorMessage() {
    val invalidDescription = " " // Description is mandatory
    composeTestRule.enterAddTodoDescription(invalidDescription)
    composeTestRule.checkErrorMessageIsDisplayedForAddTodo()
  }

  @Test
  fun enteringEmptyAssigneeNameShowsErrorMessage() {
    val invalidAssigneeName = " " // Assignee is mandatory
    composeTestRule.enterAddTodoAssignee(invalidAssigneeName)
    composeTestRule.checkErrorMessageIsDisplayedForAddTodo()
  }

  @Test
  fun enteringInvalidDateShowsErrorMessage() {
    val invalidDate = "This is not a date" // Invalid date format
    composeTestRule.enterAddTodoDate(invalidDate)
    composeTestRule.checkErrorMessageIsDisplayedForAddTodo()
  }
}
