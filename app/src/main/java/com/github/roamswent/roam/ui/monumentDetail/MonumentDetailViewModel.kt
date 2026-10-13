package com.github.roamswent.roam.ui.monumentDetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch


data class Monument(
    val id: String,
    val name: String,
    val imageUri: String,
    val description: String,
)

// À adapter : ton vrai repository (cache local, backend, etc.)
interface MonumentDetailRepository {
  suspend fun getMonument(id: String): Monument
}

// --- État & événements -------------------------------------------------------

sealed interface MonumentDetailUiState {
  data object Loading : MonumentDetailUiState

  data class Success(val monument: Monument) : MonumentDetailUiState

  data class Error(val message: String) : MonumentDetailUiState
}

sealed interface MonumentDetailEvent {
  data object NavigateHome : MonumentDetailEvent
}

// --- ViewModel ---------------------------------------------------------------

class MonumentDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: MonumentDetailRepository,
) : ViewModel() {

  // L'identifiant du monument est récupéré depuis le SavedStateHandle.
  // On évite l'utilisation de `Result` qui entre en conflit avec kotlin.Result.
  private val monumentId: String = savedStateHandle["monumentId"] ?: ""

  private val _uiState = MutableStateFlow<MonumentDetailUiState>(MonumentDetailUiState.Loading)
  val uiState: StateFlow<MonumentDetailUiState> = _uiState.asStateFlow()

  private val _events = Channel<MonumentDetailEvent>(Channel.BUFFERED)
  val events: Flow<MonumentDetailEvent> = _events.receiveAsFlow()

  init {
    load()
  }

  fun load() {
    _uiState.value = MonumentDetailUiState.Loading
    viewModelScope.launch {
      _uiState.value =
          try {
            MonumentDetailUiState.Success(repository.getMonument(monumentId))
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            MonumentDetailUiState.Error(e.message ?: "Something went wrong")
          }
    }
  }

  // Action utilisateur : bouton retour
  fun onBackClick() {
    _events.trySend(MonumentDetailEvent.NavigateHome)
  }
}
