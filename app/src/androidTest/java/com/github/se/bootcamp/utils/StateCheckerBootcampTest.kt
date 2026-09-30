package com.github.se.bootcamp.utils

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import com.github.se.bootcamp.model.todo.ToDo
import com.github.se.bootcamp.model.todo.ToDoStatus
import com.github.se.bootcamp.model.todo.ToDosRepository
import com.github.se.bootcamp.ui.overview.OverviewScreenTestTags
import com.google.firebase.Timestamp
import java.util.Calendar
import kotlinx.coroutines.runBlocking

open class StateCheckerBootcampTest(private val bootcampTest: BootcampTest) :
    BootcampTest(bootcampTest.milestone) {
  override fun createInitializedRepository(): ToDosRepository = runBlocking {
    bootcampTest.createInitializedRepository().apply {
      addTodo(firstTodo)
      for (i in 3 until 50) {
        addTodo(
            ToDo(
                uid = i.toString(),
                name = "Task $i",
                description = "Description for task $i",
                assigneeName = "User $i",
                dueDate = Timestamp.fromDate(2025, Calendar.DECEMBER, i),
                location = null,
                status = ToDoStatus.CREATED,
                ownerId = "user"))
      }

      addTodo(lastTodo)
    }
  }

  fun ComposeTestRule.scrollToLastTodo() {
    onNodeWithTag(OverviewScreenTestTags.TODO_LIST)
        .assertIsDisplayed()
        .performScrollToNode(hasTestTag(OverviewScreenTestTags.getTestTagForTodoItem(lastTodo)))
    onNodeWithTag(OverviewScreenTestTags.getTestTagForTodoItem(lastTodo)).assertIsDisplayed()
  }

  fun ComposeTestRule.checkTodoListIsStillScrolledDown() {
    onNodeWithTag(OverviewScreenTestTags.getTestTagForTodoItem(firstTodo)).assertIsNotDisplayed()
    onNodeWithTag(OverviewScreenTestTags.getTestTagForTodoItem(lastTodo)).assertIsDisplayed()
  }

  fun ComposeTestRule.checkTodoListIsNotScrolledDown() {
    onNodeWithTag(OverviewScreenTestTags.getTestTagForTodoItem(lastTodo)).assertIsNotDisplayed()
    onNodeWithTag(OverviewScreenTestTags.getTestTagForTodoItem(firstTodo)).assertIsDisplayed()
  }

  fun ComposeTestRule.scrollToTodoItem(todo: ToDo): SemanticsNodeInteraction =
      onNodeWithTag(OverviewScreenTestTags.TODO_LIST)
          .assertIsDisplayed()
          .performScrollToNode(hasTestTag(OverviewScreenTestTags.getTestTagForTodoItem(todo)))

  val firstTodo = todo1
  val lastTodo =
      ToDo(
          uid = "1000",
          name = "Swent Bootcamp",
          description = "Complete the SE Bootcamp",
          assigneeName = "Me",
          dueDate = Timestamp.Companion.fromDate(2025, Calendar.SEPTEMBER, 29),
          location = null,
          status = ToDoStatus.STARTED,
          ownerId = "user")
}
