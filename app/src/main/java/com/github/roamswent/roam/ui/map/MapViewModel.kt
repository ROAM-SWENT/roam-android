package com.github.se.bootcamp.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.bootcamp.model.map.Location
import com.github.se.bootcamp.model.todo.ToDo
import com.github.se.bootcamp.model.todo.ToDosRepository
import com.github.se.bootcamp.model.todo.ToDosRepositoryProvider
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MapUIState(
    val target: LatLng = LatLng(0.0, 0.0),
    val todos: List<ToDo> = emptyList(),
    val errorMsg: String? = null
)

class MapViewModel(private val repository: ToDosRepository = ToDosRepositoryProvider.repository) :
    ViewModel() {

  companion object {
    private val EPFL_LOCATION =
        Location(46.5191, 6.5668, "École Polytechnique Fédérale de Lausanne (EPFL), Switzerland")

    private fun toLatLng(location: Location): LatLng {
      return LatLng(location.latitude, location.longitude)
    }
  }

  private val _uiState = MutableStateFlow(MapUIState())
  val uiState: StateFlow<MapUIState> = _uiState.asStateFlow()

  init {
    Firebase.auth.addAuthStateListener {
      if (it.currentUser != null) {
        fetchLocalizableTodos()
      }
    }
  }

  private fun setErrorMsg(errorMsg: String) {
    _uiState.value = _uiState.value.copy(errorMsg = errorMsg)
  }

  fun clearErrorMsg() {
    _uiState.value = _uiState.value.copy(errorMsg = null)
  }

  fun refreshUIState() {
    fetchLocalizableTodos()
  }

  private fun fetchLocalizableTodos() {
    viewModelScope.launch {
      try {
        val todos = repository.getAllTodos().filter { it.location != null }
        val target = todos.firstOrNull<ToDo>()?.location ?: EPFL_LOCATION
        _uiState.value = MapUIState(target = toLatLng(target), todos = todos)
      } catch (e: Exception) {
        setErrorMsg("Failed to load todos: ${e.message}")
      }
    }
  }
}
