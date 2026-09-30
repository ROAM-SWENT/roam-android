package com.github.se.bootcamp.model.todo

import com.github.se.bootcamp.model.map.Location
import com.google.firebase.Timestamp
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.MockitoAnnotations

// ***************************************************************************** //
// ***                                                                       *** //
// *** THIS FILE WILL BE OVERWRITTEN DURING GRADING. IT SHOULD BE LOCATED IN *** //
// *** `app/src/test/java/com/github/se/bootcamp/model/todo/`                *** //
// *** DO **NOT** IMPLEMENT YOUR OWN TESTS IN THIS FILE                      *** //
// ***                                                                       *** //
// ***************************************************************************** //

class ToDosRepositoryLocalTest {

  private lateinit var toDosRepositoryLocal: ToDosRepositoryLocal

  private val todo =
      ToDo(
          name = "Todo",
          uid = "1",
          status = ToDoStatus.CREATED,
          location = Location(name = "EPFL", latitude = 0.0, longitude = 0.0),
          dueDate = Timestamp.now(),
          assigneeName = "me",
          description = "Do something",
          ownerId = "12345")

  @Before
  fun setUp() {
    MockitoAnnotations.openMocks(this)

    toDosRepositoryLocal = ToDosRepositoryLocal()
  }

  /**
   * This test verifies that getNewUid generates a non-empty identifier, and that a second call to
   * getNewUid generates a different identifier.
   */
  @Test
  fun correcltyGeneratesNewUID() {
    val uid = toDosRepositoryLocal.getNewUid()
    assertTrue(uid.isNotEmpty()) // Ensure the UID is not empty

    val anotherUid = toDosRepositoryLocal.getNewUid()
    assertTrue(uid != anotherUid)
  }

  /**
   * This test verifies that addToDo successfully adds a ToDo item to the local repository It also
   * tests that getAllTodos and getTodo successfully retrieve the todos.
   */
  @Test
  fun addToDo_succeeds() = runTest {
    toDosRepositoryLocal.addTodo(todo)

    // Verify that the ToDo was added
    val todos = toDosRepositoryLocal.getAllTodos()
    assertTrue(todos.contains(todo)) // Ensure the todo is present
    assertEquals(1, todos.size) // Ensure only one todo is present

    val retrievedTodo = toDosRepositoryLocal.getTodo(todo.uid)
    assertEquals(todo, retrievedTodo)
  }

  /**
   * This test verifies that updateToDo successfully updates an existing ToDo item in the local
   * repository, and calls the onSuccess callback. It also checks that the old ToDo item is no
   * longer present and the updated item is present with the correct updated values.
   */
  @Test
  fun updateToDo_succeeds() = runTest {
    toDosRepositoryLocal.addTodo(todo)

    val updatedTodo = todo.copy(name = "Updated Todo")

    toDosRepositoryLocal.editTodo(todo.uid, updatedTodo)

    // Verify that the ToDo was updated
    val todos = toDosRepositoryLocal.getAllTodos()
    assertTrue(todos.contains(updatedTodo)) // Ensure the updated todo is present
    assertTrue(!todos.contains(todo)) // Ensure the old todo is not present
    assertEquals(1, todos.size) // Ensure only one todo is present
  }

  /**
   * This test verifies that updateToDo calls onFailure when trying to update a ToDo item that does
   * not exist in the local repository.
   */
  @Test
  fun updateToDo_failsWhenToDoNotFound() {
    assertThrows(Exception::class.java) {
      runTest { toDosRepositoryLocal.editTodo(todo.uid, todo) }
    }
  }

  /**
   * This test verifies that deleteToDoById successfully removes a ToDo item from the local
   * repository, and calls the onSuccess callback.
   */
  @Test
  fun deleteToDoById_callsOnSuccess() = runTest {
    toDosRepositoryLocal.addTodo(todo)

    toDosRepositoryLocal.deleteTodo(todo.uid)

    // Verify that the ToDo was deleted
    val todos = toDosRepositoryLocal.getAllTodos()
    assertTrue(!todos.contains(todo)) // Ensure the todo is not present
    assertEquals(0, todos.size) // Ensure no todos are present

    assertThrows(Exception::class.java) { runBlocking { toDosRepositoryLocal.getTodo(todo.uid) } }
  }

  @Test
  fun deleteToDoById_deletesTheCorrectToDo() = runTest {
    val todo2 = todo.copy(uid = "2", name = "Second Todo")
    toDosRepositoryLocal.addTodo(todo)
    toDosRepositoryLocal.addTodo(todo2)

    toDosRepositoryLocal.deleteTodo(todo.uid)

    // Verify that the correct ToDo was deleted
    val todos = toDosRepositoryLocal.getAllTodos()
    assertTrue(!todos.contains(todo)) // Ensure the first todo is not present
    assertTrue(todos.contains(todo2)) // Ensure the second todo is still present
  }
  /**
   * This test verifies that deleteToDoById calls onFailure when trying to delete a ToDo item that
   * does not exist in the local repository.
   */
  @Test
  fun deleteToDoById_callsOnFailure_whenToDoNotFound() {
    assertThrows(Exception::class.java) {
      runBlocking { toDosRepositoryLocal.deleteTodo("non-existent-id") }
    }
  }
}
