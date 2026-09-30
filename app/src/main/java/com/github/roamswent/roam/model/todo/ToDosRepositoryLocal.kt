package com.github.se.bootcamp.model.todo

/** Represents a repository that manages a local list of todos. */
class ToDosRepositoryLocal : ToDosRepository {
  private val todos: MutableList<ToDo> = mutableListOf()

  private var counter = 0

  override fun getNewUid(): String {
    return (counter++).toString()
  }

  override suspend fun getAllTodos(): List<ToDo> {
    return todos
  }

  override suspend fun getTodo(todoID: String): ToDo {
    return todos.find { it.uid == todoID }
        ?: throw Exception("ToDosRepositoryLocal: ToDo not found")
  }

  override suspend fun addTodo(toDo: ToDo) {
    todos.add(toDo)
  }

  override suspend fun editTodo(todoID: String, newValue: ToDo) {
    val index = todos.indexOfFirst { it.uid == todoID }
    if (index != -1) {
      todos[index] = newValue
    } else {
      throw Exception("ToDosRepositoryLocal: ToDo not found")
    }
  }

  override suspend fun deleteTodo(todoID: String) {
    val index = todos.indexOfFirst { it.uid == todoID }
    if (index != -1) {
      todos.removeAt(index)
    } else {
      throw Exception("ToDosRepositoryLocal: ToDo not found")
    }
  }
}
