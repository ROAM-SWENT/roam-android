package com.github.se.bootcamp.model.todo

/** Represents a repository that manages ToDo items. */
interface ToDosRepository {

  /** Generates and returns a new unique identifier for a ToDo item. */
  fun getNewUid(): String

  /**
   * Retrieves all ToDo items from the repository.
   *
   * @return A list of all ToDo items.
   */
  suspend fun getAllTodos(): List<ToDo>

  /**
   * Retrieves a specific ToDo item by its unique identifier.
   *
   * @param todoID The unique identifier of the ToDo item to retrieve.
   * @return The ToDo item with the specified identifier.
   * @throws Exception if the ToDo item is not found.
   */
  suspend fun getTodo(todoID: String): ToDo

  /**
   * Adds a new ToDo item to the repository.
   *
   * @param toDo The ToDo item to add.
   */
  suspend fun addTodo(toDo: ToDo)

  /**
   * Edits an existing ToDo item in the repository.
   *
   * @param todoID The unique identifier of the ToDo item to edit.
   * @param newValue The new value for the ToDo item.
   * @throws Exception if the ToDo item is not found.
   */
  suspend fun editTodo(todoID: String, newValue: ToDo)

  /**
   * Deletes a ToDo item from the repository.
   *
   * @param todoID The unique identifier of the ToDo item to delete.
   * @throws Exception if the ToDo item is not found.
   */
  suspend fun deleteTodo(todoID: String)
}
