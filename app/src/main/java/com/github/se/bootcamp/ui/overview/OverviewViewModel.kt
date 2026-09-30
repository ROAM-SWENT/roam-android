package com.github.se.bootcamp.ui.overview

import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.bootcamp.model.authentication.AuthRepository
import com.github.se.bootcamp.model.authentication.AuthRepositoryFirebase
import com.github.se.bootcamp.model.todo.ToDo
import com.github.se.bootcamp.model.todo.ToDosRepository
import com.github.se.bootcamp.model.todo.ToDosRepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Represents the UI state for the Overview screen.
 *
 * @property todos A list of `ToDo` items to be displayed in the Overview screen. Defaults to an
 *   empty list if no items are available.
 * @property errorMsg An error message to be shown when fetching todos fails. `null` if no error is
 *   present.
 */
data class OverviewUIState(
    val todos: List<ToDo> = emptyList(),
    val errorMsg: String? = null,
    val signedOut: Boolean = false
)

/**
 * ViewModel for the Overview screen.
 *
 * Responsible for managing the UI state, by fetching and providing ToDo items via the
 * [ToDosRepository].
 *
 * @property todoRepository The repository used to fetch and manage ToDo items.
 */
class OverviewViewModel(
    private val todoRepository: ToDosRepository = ToDosRepositoryProvider.repository,
    private val authRepository: AuthRepository = AuthRepositoryFirebase(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(OverviewUIState())
  val uiState: StateFlow<OverviewUIState> = _uiState.asStateFlow()

  init {
    //    Firebase.auth.addAuthStateListener {
    //      if (it.currentUser != null) {
    getAllTodos()
    //      }
    //    }
  }

  /** Clears the error message in the UI state. */
  fun clearErrorMsg() {
    _uiState.value = _uiState.value.copy(errorMsg = null)
  }

  /** Sets an error message in the UI state. */
  private fun setErrorMsg(errorMsg: String) {
    _uiState.value = _uiState.value.copy(errorMsg = errorMsg)
  }

  /** Refreshes the UI state by fetching all ToDo items from the repository. */
  fun refreshUIState() {
    getAllTodos()
  }

  /** Fetches all todos from the repository and updates the UI state. */
  private fun getAllTodos() {
    viewModelScope.launch {
      try {
        val todos = todoRepository.getAllTodos()
        _uiState.value = OverviewUIState(todos = todos)
      } catch (e: Exception) {
        Log.e("OverviewViewModel", "Error fetching todos", e)
        setErrorMsg("Failed to load todos: ${e.message}")
      }
    }
  }

  /** Initiates sign-out and updates the UI state on success or failure. */
  fun signOut(credentialManager: CredentialManager): Unit {
    viewModelScope.launch {
      authRepository
          .signOut()
          .fold(
              onSuccess = { _uiState.update { it.copy(signedOut = true) } },
              onFailure = { throwable ->
                _uiState.update { it.copy(errorMsg = throwable.localizedMessage) }
              })
      credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }
  }
}
