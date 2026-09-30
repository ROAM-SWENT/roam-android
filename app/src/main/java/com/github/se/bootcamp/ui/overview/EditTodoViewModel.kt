package com.github.se.bootcamp.ui.overview

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.bootcamp.HttpClientProvider
import com.github.se.bootcamp.model.map.Location
import com.github.se.bootcamp.model.map.LocationRepository
import com.github.se.bootcamp.model.map.NominatimLocationRepository
import com.github.se.bootcamp.model.todo.DateParser
import com.github.se.bootcamp.model.todo.ToDo
import com.github.se.bootcamp.model.todo.ToDoStatus
import com.github.se.bootcamp.model.todo.ToDosRepository
import com.github.se.bootcamp.model.todo.ToDosRepositoryProvider
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI state for the AddToDo screen. This state holds the data needed to create a new ToDo item. */
data class EditTodoUIState(
    val title: String = "",
    val description: String = "",
    val assigneeName: String = "",
    val dueDate: String = "",
    val selectedLocation: Location? = null,
    val status: ToDoStatus = ToDoStatus.CREATED,
    val errorMsg: String? = null,
    val locationQuery: String = "",
    val locationSuggestions: List<Location> = emptyList(),
    val invalidTitleMsg: String? = null,
    val invalidDescriptionMsg: String? = null,
    val invalidAssigneeNameMsg: String? = null,
    val invalidDueDateMsg: String? = null,
) {
  val isValid: Boolean
    get() =
        invalidTitleMsg == null &&
            invalidDescriptionMsg == null &&
            invalidAssigneeNameMsg == null &&
            invalidDueDateMsg == null &&
            title.isNotEmpty() &&
            description.isNotEmpty() &&
            assigneeName.isNotEmpty() &&
            dueDate.isNotEmpty()
}

class EditTodoViewModel(
    private val repository: ToDosRepository = ToDosRepositoryProvider.repository,
    private val locationRepository: LocationRepository =
        NominatimLocationRepository(HttpClientProvider.client)
) : ViewModel() {
  // AddToDo UI state
  private val _uiState = MutableStateFlow(EditTodoUIState())
  val uiState: StateFlow<EditTodoUIState> = _uiState.asStateFlow()

  /** Clears the error message in the UI state. */
  fun clearErrorMsg() {
    _uiState.value = _uiState.value.copy(errorMsg = null)
  }

  /** Sets an error message in the UI state. */
  private fun setErrorMsg(errorMsg: String) {
    _uiState.value = _uiState.value.copy(errorMsg = errorMsg)
  }

  /**
   * Loads a ToDo by its ID and updates the UI state.
   *
   * @param todoID The ID of the ToDo to be loaded.
   */
  fun loadTodo(todoID: String) {
    viewModelScope.launch {
      try {
        val todo = repository.getTodo(todoID)
        _uiState.value =
            EditTodoUIState(
                title = todo.name,
                description = todo.description,
                assigneeName = todo.assigneeName,
                dueDate =
                    todo.dueDate.let {
                      val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                      return@let dateFormat.format(todo.dueDate.toDate())
                    },
                selectedLocation = todo.location,
                status = todo.status)
      } catch (e: Exception) {
        Log.e("EditTodoViewModel", "Error loading ToDo by ID: $todoID", e)
        setErrorMsg("Failed to load ToDo: ${e.message}")
      }
    }
  }

  /**
   * Adds a ToDo document.
   *
   * @param todo The ToDo document to be added.
   */
  fun editTodo(id: String): Boolean {
    val state = _uiState.value
    if (!state.isValid) {
      setErrorMsg("At least one field is not valid")
      return false
    }
    val date = DateParser.parse(state.dueDate)!!

    val uid = Firebase.auth.currentUser?.uid ?: "None (B2)"

    editTodoToRepository(
        id = id,
        todo =
            ToDo(
                name = state.title,
                description = state.description,
                assigneeName = state.assigneeName,
                dueDate = Timestamp(date),
                location = state.selectedLocation,
                status = state.status,
                uid = id,
                ownerId = uid))
    clearErrorMsg()
    return true
  }

  /**
   * Edits a ToDo document in the repository.
   *
   * @param id The ID of the ToDo document to be edited.
   * @param todo The ToDo object containing the new values.
   */
  private fun editTodoToRepository(id: String, todo: ToDo) {
    viewModelScope.launch {
      try {
        repository.editTodo(todoID = id, newValue = todo)
      } catch (e: Exception) {
        Log.e("AddToDoViewModel", "Error adding ToDo", e)
        setErrorMsg("Failed to add ToDo: ${e.message}")
      }
    }
  }

  /**
   * Deletes a ToDo document by its ID.
   *
   * @param todoID The ID of the ToDo document to be deleted.
   */
  fun deleteToDo(todoID: String) {
    viewModelScope.launch {
      try {
        repository.deleteTodo(todoID = todoID)
      } catch (e: Exception) {
        Log.e("EditTodoViewModel", "Error deleting ToDo", e)
        setErrorMsg("Failed to delete ToDo: ${e.message}")
      }
    }
  }

  // Functions to update the UI state.

  fun setTitle(title: String) {
    _uiState.value =
        _uiState.value.copy(
            title = title, invalidTitleMsg = if (title.isBlank()) "Title cannot be empty" else null)
  }

  fun setDescription(description: String) {
    _uiState.value =
        _uiState.value.copy(
            description = description,
            invalidDescriptionMsg =
                if (description.isBlank()) "Description cannot be empty" else null)
  }

  fun setAssigneeName(assigneeName: String) {
    _uiState.value =
        _uiState.value.copy(
            assigneeName = assigneeName,
            invalidAssigneeNameMsg =
                if (assigneeName.isBlank()) "Assignee cannot be empty" else null)
  }

  fun setDueDate(dueDate: String) {
    _uiState.value =
        _uiState.value.copy(
            dueDate = dueDate,
            invalidDueDateMsg =
                if (DateParser.parse(dueDate) == null) "Date is not valid (format: dd/mm/yyyy)"
                else null)
  }

  fun setLocation(location: Location) {
    _uiState.value = _uiState.value.copy(selectedLocation = location, locationQuery = location.name)
  }

  fun setStatus(status: ToDoStatus) {
    _uiState.value = _uiState.value.copy(status = status)
  }

  fun setLocationQuery(query: String) {
    _uiState.value = _uiState.value.copy(locationQuery = query)

    if (query.isNotEmpty()) {
      viewModelScope.launch {
        try {
          val results = locationRepository.search(query)
          _uiState.value = _uiState.value.copy(locationSuggestions = results)
        } catch (e: Exception) {
          Log.e("EditToDoViewModel", "Error fetching location suggestions", e)
          _uiState.value = _uiState.value.copy(locationSuggestions = emptyList())
        }
      }
    } else {
      _uiState.value = _uiState.value.copy(locationSuggestions = emptyList())
    }
  }
}
