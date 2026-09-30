package com.github.se.bootcamp.ui.overview

// ***************************************************************************** //
// ***                                                                       *** //
// *** THIS FILE WILL BE OVERWRITTEN DURING GRADING. IT SHOULD BE LOCATED IN *** //
// *** `app/src/androidTest/java/com/github/se/bootcamp/ui/overview`.        *** //
// *** DO **NOT** IMPLEMENT YOUR OWN TESTS IN THIS FILE                      *** //
// ***                                                                       *** //
// ***************************************************************************** //

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.se.bootcamp.model.todo.ToDo
import com.github.se.bootcamp.model.todo.ToDoStatus
import com.github.se.bootcamp.utils.BootcampMilestone
import com.github.se.bootcamp.utils.InMemoryBootcampTest
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class EditToDoScreenB2Test : InMemoryBootcampTest(BootcampMilestone.B2) {
  @get:Rule val composeTestRule = createComposeRule()

  val todo = todo1

  private fun withContent(
      editedTodo: ToDo = todo,
      todoList: List<ToDo> = listOf<ToDo>(editedTodo),
      block: (ToDo) -> Unit
  ) {
    runTest {
      for (todo in todoList) {
        repository.addTodo(todo)
      }
    }

    composeTestRule.setContent { EditToDoScreen(todoUid = editedTodo.uid) }
    block(editedTodo)
  }

  @Test
  fun displayAllComponents() = withContent {
    composeTestRule
        .onNodeWithTag(EditToDoScreenTestTags.TODO_SAVE)
        .assertIsDisplayed()
        .assertTextContains("Save", substring = true, ignoreCase = true)
    composeTestRule
        .onNodeWithTag(EditToDoScreenTestTags.TODO_DELETE)
        .assertIsDisplayed()
        .assertTextContains("Delete", substring = true, ignoreCase = true)

    composeTestRule.onNodeWithTag(EditToDoScreenTestTags.TODO_DELETE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_DESCRIPTION).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_ASSIGNEE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_LOCATION).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_DATE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_STATUS).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EditToDoScreenTestTags.LOCATION_SUGGESTION).assertIsNotDisplayed()
    composeTestRule.onNodeWithTag(EditToDoScreenTestTags.ERROR_MESSAGE).assertIsNotDisplayed()
  }

  @Test
  fun canEnterStatus() = withContent { editedTodo ->
    composeTestRule.enterEditTodoStatus(editedTodo.status, ToDoStatus.ARCHIVED)
    composeTestRule.onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_STATUS).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EditToDoScreenTestTags.ERROR_MESSAGE).assertIsNotDisplayed()
  }

  @Test
  fun canEnterTitle() = withContent {
    val text = "title"
    composeTestRule.enterEditTodoTitle(text)
    composeTestRule.onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_TITLE).assertTextContains(text)
    composeTestRule
        .onNodeWithTag(AddToDoScreenTestTags.ERROR_MESSAGE, useUnmergedTree = true)
        .assertIsNotDisplayed()
  }

  @Test
  fun canEnterDescription() = withContent {
    val text = "description"
    composeTestRule.enterEditTodoDescription(text)
    composeTestRule
        .onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_DESCRIPTION)
        .assertTextContains(text)
    composeTestRule
        .onNodeWithTag(AddToDoScreenTestTags.ERROR_MESSAGE, useUnmergedTree = true)
        .assertIsNotDisplayed()
  }

  @Test
  fun canEnterAssigneeName() = withContent {
    val text = "assignee"
    composeTestRule.enterEditTodoAssignee(text)
    composeTestRule
        .onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_ASSIGNEE)
        .assertTextContains(text)
    composeTestRule
        .onNodeWithTag(AddToDoScreenTestTags.ERROR_MESSAGE, useUnmergedTree = true)
        .assertIsNotDisplayed()
  }

  @Test
  fun canEnterLocation() = withContent {
    val text = "location"
    composeTestRule.enterEditTodoLocation(text)
    composeTestRule
        .onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_LOCATION)
        .assertTextContains(text)
    composeTestRule
        .onNodeWithTag(AddToDoScreenTestTags.ERROR_MESSAGE, useUnmergedTree = true)
        .assertIsNotDisplayed()
  }

  @Test
  fun canEnterAValidDate() = withContent {
    val text = "31/02/2023"
    composeTestRule.enterEditTodoDate(text)
    composeTestRule.onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_DATE).assertTextContains(text)
  }

  @Test
  fun canEnterAnInvalidDate() = withContent {
    val text = "This date is not valid"
    composeTestRule.enterEditTodoDate(text)
    composeTestRule.onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_DATE).assertTextContains(text)
  }
}
