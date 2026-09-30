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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI state for the AddToDo screen. This state holds the data needed to create a new ToDo item. */
data class AddTodoUIState(
    val title: String = "",
    val description: String = "",
    val assigneeName: String = "",
    val dueDate: String = "",
    val selectedLocation: Location? = null,
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

/**
 * ViewModel for the AddToDo screen. This ViewModel manages the state of input fields for the
 * AddToDo screen.
 */
class AddTodoViewModel(
    private val repository: ToDosRepository = ToDosRepositoryProvider.repository,
    private val locationRepository: LocationRepository =
        NominatimLocationRepository(HttpClientProvider.client)
) : ViewModel() {
  // AddToDo UI state
  private val _uiState = MutableStateFlow(AddTodoUIState())
  val uiState: StateFlow<AddTodoUIState> = _uiState.asStateFlow()

  /** Clears the error message in the UI state. */
  fun clearErrorMsg() {
    _uiState.value = _uiState.value.copy(errorMsg = null)
  }

  /** Sets an error message in the UI state. */
  private fun setErrorMsg(errorMsg: String) {
    _uiState.value = _uiState.value.copy(errorMsg = errorMsg)
  }

  /** Adds a ToDo document. */
  fun addTodo(): Boolean {
    val state = _uiState.value
    if (!state.isValid) {
      setErrorMsg("At least one field is not valid")
      return false
    }
    val date = DateParser.parse(state.dueDate)!!
    val uid = Firebase.auth.currentUser?.uid ?: "None (B2)"

    addToDoToRepository(
        ToDo(
            name = state.title,
            description = state.description,
            assigneeName = state.assigneeName,
            dueDate = Timestamp(date),
            location = state.selectedLocation,
            status = ToDoStatus.CREATED,
            uid = repository.getNewUid(),
            ownerId = uid))
    clearErrorMsg()
    return true
  }

  private fun addToDoToRepository(todo: ToDo) {
    viewModelScope.launch {
      try {
        repository.addTodo(todo)
      } catch (e: Exception) {
        Log.e("AddToDoViewModel", "Error adding ToDo", e)
        setErrorMsg("Failed to add ToDo: ${e.message}")
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

  fun setLocationQuery(query: String) {
    _uiState.value = _uiState.value.copy(locationQuery = query)

    if (query.isNotEmpty()) {
      viewModelScope.launch {
        try {
          val results = locationRepository.search(query)
          _uiState.value = _uiState.value.copy(locationSuggestions = results)
        } catch (e: Exception) {
          Log.e("AddToDoViewModel", "Error fetching location suggestions", e)
          _uiState.value = _uiState.value.copy(locationSuggestions = emptyList())
        }
      }
    } else {
      _uiState.value = _uiState.value.copy(locationSuggestions = emptyList())
    }
  }
}
