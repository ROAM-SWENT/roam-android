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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import com.github.se.bootcamp.model.todo.ToDo
import com.github.se.bootcamp.utils.BootcampMilestone
import com.github.se.bootcamp.utils.InMemoryBootcampTest
import com.google.firebase.Timestamp
import java.util.Calendar
import kotlin.collections.last
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class OverviewScreenB1Test : InMemoryBootcampTest(BootcampMilestone.B1) {
  @get:Rule val composeTestRule = createComposeRule()

  fun setContent(withInitialTodos: List<ToDo> = emptyList()) {
    runTest { withInitialTodos.forEach { repository.addTodo(it) } }
    composeTestRule.setContent { OverviewScreen() }
  }

  @Test
  fun testTagsCorrectlySetWhenListIsEmpty() {
    setContent()
    composeTestRule.onNodeWithTag(OverviewScreenTestTags.TODO_LIST).assertIsNotDisplayed()
    composeTestRule.onNodeWithTag(OverviewScreenTestTags.EMPTY_TODO_LIST_MSG).assertIsDisplayed()
  }

  @Test
  fun testTagsCorrectlySetWhenListIsNotEmpty() {
    setContent(withInitialTodos = listOf(todo1, todo2))
    composeTestRule.onNodeWithTag(OverviewScreenTestTags.TODO_LIST).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(OverviewScreenTestTags.getTestTagForTodoItem(todo1))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(OverviewScreenTestTags.getTestTagForTodoItem(todo2))
        .assertIsDisplayed()
  }

  @Test
  fun todoListDisplaysTaskName() {
    val todoList = listOf(todo1)
    setContent(withInitialTodos = todoList)
    composeTestRule.onTodoItem(todo1, hasText(todo1.name))
  }

  @Test
  fun todoListDisplaysAssigneeName() {
    val todoList = listOf(todo1)
    setContent(withInitialTodos = todoList)
    composeTestRule.onTodoItem(todo1, hasText(todo1.assigneeName))
  }

  @Test
  fun todoListDisplaysDueDate() {
    val todo = todo1.copy(dueDate = Timestamp.fromDate(2023, Calendar.DECEMBER, 25))
    val todoList = listOf(todo)
    val dueDate = "25/12/2023"
    setContent(withInitialTodos = todoList)
    composeTestRule.onTodoItem(todo, hasText(dueDate))
  }

  @Test
  fun todoListDisplaysStatus() {
    val todoList = listOf(todo1)
    setContent(withInitialTodos = todoList)
    composeTestRule.onTodoItem(
        todo1, hasText(todo1.status.toString(), substring = false, ignoreCase = true))
  }

  @Test
  fun todoListDisplaysExistingTodos() {
    val todoList = listOf(todo1, todo2)
    setContent(withInitialTodos = todoList)
    // Check that the todo item is displayed correctly.
    todoList.forEach {
      composeTestRule.onTodoItem(it, hasText(it.name))
      composeTestRule.onTodoItem(it, hasText(it.assigneeName))
      composeTestRule.onTodoItem(
          it, hasText(it.status.toString(), substring = false, ignoreCase = true))
    }
  }

  @Test
  fun dueDateIsCorrectlyFormatted() {
    val todo1 = todo1.copy(uid = "1", dueDate = Timestamp.fromDate(2023, Calendar.DECEMBER, 25))
    val todoList = listOf(todo1)
    val dueDate1 = "25/12/2023"
    setContent(withInitialTodos = todoList)
    composeTestRule.onTodoItem(todo1, hasText(dueDate1))
  }

  @Test
  fun canScrollOnTheTodoList() {
    val todos =
        (1..50).toList<Int>().map { todo1.copy(uid = it.toString(), name = "${todo1.name} #$it") }
    setContent(withInitialTodos = todos)
    composeTestRule
        .onNodeWithTag(OverviewScreenTestTags.getTestTagForTodoItem(todos.first()))
        .assertIsDisplayed()
    val lastNode =
        composeTestRule.onNodeWithTag(OverviewScreenTestTags.getTestTagForTodoItem(todos.last()))
    lastNode.assertIsNotDisplayed()
    composeTestRule
        .onNodeWithTag(OverviewScreenTestTags.TODO_LIST)
        .performScrollToNode(hasTestTag(OverviewScreenTestTags.getTestTagForTodoItem(todos.last())))
    lastNode.assertIsDisplayed()
  }
}
