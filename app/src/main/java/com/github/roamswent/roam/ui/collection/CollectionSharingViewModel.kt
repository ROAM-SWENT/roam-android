// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CollectionFriend(val uid: String, val username: String)

data class CollectionSharingUiState(
    val friends: List<CollectionFriend> = emptyList(),
    val isLoadingFriends: Boolean = false,
    val friendsError: Exception? = null,
)

/** Loads accepted friends and their names for the collection's sharing picker. */
class CollectionSharingViewModel(
    private val friendRepository: FriendRepository,
    private val publicProfileRepository: PublicProfileRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(CollectionSharingUiState())
  val uiState: StateFlow<CollectionSharingUiState> = _uiState.asStateFlow()
  private var loadJob: Job? = null

  fun loadFriends() {
    loadJob?.cancel()
    _uiState.update { it.copy(isLoadingFriends = true, friendsError = null) }
    loadJob = viewModelScope.launch {
      try {
        val friends = friendRepository.getFriends()
        val profiles = coroutineScope {
          friends
              .map { friendship ->
                async {
                  val profile = publicProfileRepository.getProfile(friendship.friendUid)
                  CollectionFriend(
                      uid = friendship.friendUid,
                      username = profile?.username ?: friendship.friendUid,
                  )
                }
              }
              .map { it.await() }
        }
        _uiState.update { it.copy(friends = profiles, isLoadingFriends = false) }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(isLoadingFriends = false, friendsError = exception) }
      }
    }
  }

  fun retryLoadingFriends() {
    loadFriends()
  }
}
