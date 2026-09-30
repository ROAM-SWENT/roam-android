package com.github.se.bootcamp.model.todo

import com.github.se.bootcamp.utils.BootcampMilestone
import com.github.se.bootcamp.utils.FirestoreBootcampTest
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ToDosRepositoryFirestoreTest : FirestoreBootcampTest(BootcampMilestone.B2) {

  @Before
  override fun setUp() {
    super.setUp()
  }

  @Test
  fun canAddToDosToRepository() = runTest {
    repository.addTodo(todo1)
    assertEquals(1, getTodosCount())
    val todos = repository.getAllTodos()

    assertEquals(1, todos.size)
    // Discard uid and ownerId for comparison since they don't matter in this test
    val expectedTodo = todo1.copy(uid = "None", ownerId = "None")
    val storedTodo = todos.first().copy(uid = expectedTodo.uid, ownerId = expectedTodo.ownerId)

    assertEquals(expectedTodo, storedTodo)
  }

  @Test
  fun addTodoWithTheCorrectID() = runTest {
    repository.addTodo(todo1)
    assertEquals(1, getTodosCount())
    val storedTodo = repository.getTodo(todo1.uid)
    assertEquals(storedTodo, todo1)
  }

  @Test
  fun canAddMultipleToDosToRepository() = runTest {
    repository.addTodo(todo1)
    repository.addTodo(todo2)
    repository.addTodo(todo3)
    assertEquals(3, getTodosCount())
    val todos = repository.getAllTodos()

    assertEquals(todos.size, 3)
    // Discard the ordering of the todos
    val expectedTodos = setOf(todo1, todo2, todo3)
    val storedTodos = todos.toSet()

    assertEquals(expectedTodos, storedTodos)
  }

  @Test
  fun uidAreUniqueInTheCollection() = runTest {
    val uid = "duplicate"
    val todo1Modified = todo1.copy(uid = uid)
    val todoDuplicatedUID = todo2.copy(uid = uid)
    // Depending on your implementation, adding a Todo with an existing UID
    // might not be permitted
    runCatching {
      repository.addTodo(todo1Modified)
      repository.addTodo(todoDuplicatedUID)
    }

    assertEquals(1, getTodosCount())

    val todos = repository.getAllTodos()
    assertEquals(todos.size, 1)
    val storedTodo = todos.first()
    assertEquals(storedTodo.uid, uid)
  }

  @Test
  fun getNewUidReturnsUniqueIDs() = runTest {
    val numberIDs = 100
    val uids = (0 until 100).toSet<Int>().map { repository.getNewUid() }.toSet()
    assertEquals(uids.size, numberIDs)
  }

  @Test
  fun canRetrieveATodoByID() = runTest {
    repository.addTodo(todo1)
    repository.addTodo(todo2)
    repository.addTodo(todo3)
    assertEquals(3, getTodosCount())
    val storedTodo = repository.getTodo(todo2.uid)
    assertEquals(storedTodo, todo2)
  }

  @Test
  fun canDeleteATodoByID() = runTest {
    repository.addTodo(todo1)
    repository.addTodo(todo2)
    repository.addTodo(todo3)

    repository.deleteTodo(todo2.uid)
    assertEquals(2, getTodosCount())
    val todos = repository.getAllTodos()
    assertEquals(todos.size, 2)

    val expectedTodos = setOf(todo1, todo3)
    assertEquals(expectedTodos, todos.toSet())
  }

  @Test
  fun canEditATodoByID() = runTest {
    repository.addTodo(todo1)
    assertEquals(1, getTodosCount())
    val todos = repository.getAllTodos()
    assertEquals(1, todos.size)

    val modifiedTodo = todo1.copy(name = "Modified Name", status = ToDoStatus.ARCHIVED)
    repository.editTodo(todo1.uid, modifiedTodo)
    assertEquals(1, getTodosCount())
    val todosAfterEdit = repository.getAllTodos()
    assertEquals(todosAfterEdit.size, 1)
    assertEquals(todosAfterEdit.first(), modifiedTodo)
  }

  @Test
  fun canEditTheCorrectTodoByID() = runTest {
    repository.addTodo(todo1)
    repository.addTodo(todo2)
    repository.addTodo(todo3)

    assertEquals(3, getTodosCount())

    val todos = repository.getAllTodos()
    assertEquals(todos.size, 3)

    val modifiedTodo = todo1.copy(name = "Modified Name", status = ToDoStatus.ARCHIVED)
    repository.editTodo(todo1.uid, modifiedTodo)
    val todosAfterEdit = repository.getAllTodos()

    assertEquals(3, getTodosCount())
    assertEquals(3, todosAfterEdit.size)

    val expectedTodos = setOf(modifiedTodo, todo2, todo3)
    assertEquals(expectedTodos, todosAfterEdit.toSet())
  }

  /** Handling invalid function parameters is not a requirement * */
}
