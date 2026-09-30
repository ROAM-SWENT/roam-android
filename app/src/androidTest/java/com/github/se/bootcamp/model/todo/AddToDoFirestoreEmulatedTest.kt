package com.github.se.bootcamp.model.todo

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.se.bootcamp.BootcampApp
import com.github.se.bootcamp.utils.BootcampMilestone
import com.github.se.bootcamp.utils.FirestoreBootcampTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AddToDoFirestoreEmulatedTest : FirestoreBootcampTest(BootcampMilestone.B2) {
  @get:Rule val composeTestRule = createComposeRule()

  @Before
  override fun setUp() {
    super.setUp()
    composeTestRule.setContent { BootcampApp() }
    composeTestRule.navigateToAddToDoScreen()
  }

  @Test
  fun canStoreNewTodoInDatabase() {
    composeTestRule.enterAddTodoDetails(todo1)
    composeTestRule.clickOnSaveForAddTodo(waitForRedirection = true)
    runTest {
      assertEquals(1, getTodosCount())
      val todos = repository.getAllTodos()
      val storedTodo = todos.first()
      assertTrue(todo1.b2Equals(storedTodo))
    }
  }

  @Test
  fun canStoreMultipleTodosInDatabase() {
    composeTestRule.enterAddTodoDetails(todo1)
    composeTestRule.clickOnSaveForAddTodo(waitForRedirection = true)
    composeTestRule.navigateToAddToDoScreen()
    composeTestRule.enterAddTodoDetails(todo2)
    composeTestRule.clickOnSaveForAddTodo(waitForRedirection = true)

    runTest {
      assertEquals(2, getTodosCount())
      val todos = repository.getAllTodos()

      val storedTodo1 = todos.find { it.name == todo1.name }
      val storedTodo2 = todos.find { it.name == todo2.name }

      Assert.assertNotNull(storedTodo1)
      Assert.assertNotNull(storedTodo2)

      assertTrue(todo1.copy(status = ToDoStatus.CREATED).b2Equals(storedTodo1!!))
      assertTrue(todo2.copy(status = ToDoStatus.CREATED).b2Equals(storedTodo2!!))
    }
  }
}
