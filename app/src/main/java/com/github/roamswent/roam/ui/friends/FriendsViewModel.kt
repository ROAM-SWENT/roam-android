/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendUser(
    val id: String,
    val username: String,
    val avatarUrl: String? = null,
)

data class FriendRequest(
    val user: FriendUser,
    val requestedAtMillis: Long = 0L,
    val id: String = "",
)

data class FriendSearchResult(
    val user: FriendUser,
    val outgoingRequestId: String? = null,
    val incomingRequestId: String? = null,
    val isFriend: Boolean = false,
)

data class FriendsUiState(
    val friends: List<FriendUser> = emptyList(),
    val incomingRequests: List<FriendRequest> = emptyList(),
    val outgoingRequests: List<FriendRequest> = emptyList(),
    val searchResults: List<FriendSearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val isMutating: Boolean = false,
    val error: Exception? = null,
    val message: String? = null,
)

/** Coordinates friends and public-profile repositories for the Friends screen. */
class FriendsViewModel(
    private val friendRepository: FriendRepository,
    private val publicProfileRepository: PublicProfileRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(FriendsUiState())
  val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

  private var selfUid: String? = null
  private var searchJob: Job? = null
  private var loadJob: Job? = null

  fun loadFriends() {
    if (loadJob?.isActive == true || _uiState.value.isLoading) return
    _uiState.update { it.copy(isLoading = true, error = null) }
    loadJob = viewModelScope.launch { refreshFriends() }
  }

  fun searchUsers(prefix: String) {
    searchJob?.cancel()
    if (prefix.isBlank()) {
      _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
      return
    }

    _uiState.update { it.copy(isSearching = true, error = null) }
    searchJob = viewModelScope.launch {
      delay(SEARCH_DEBOUNCE_MILLIS)
      try {
        val profiles = publicProfileRepository.searchProfiles(prefix.trim())
        val state = _uiState.value
        val friendIds = state.friends.mapTo(mutableSetOf(), FriendUser::id)
        val outgoingByUid = state.outgoingRequests.associateBy { it.user.id }
        val incomingByUid = state.incomingRequests.associateBy { it.user.id }
        _uiState.update {
          it.copy(
              searchResults =
                  profiles
                      .filterNot { profile -> profile.uid == selfUid }
                      .map { profile ->
                        FriendSearchResult(
                            user = FriendUser(profile.uid, profile.username),
                            outgoingRequestId = outgoingByUid[profile.uid]?.id,
                            incomingRequestId = incomingByUid[profile.uid]?.id,
                            isFriend = profile.uid in friendIds,
                        )
                      },
              isSearching = false,
          )
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update {
          it.copy(isSearching = false, searchResults = emptyList(), error = exception)
        }
      }
    }
  }

  fun sendRequest(user: FriendUser) {
    mutate("Friend request sent.") { friendRepository.sendFriendRequest(user.id) }
  }

  fun acceptRequest(request: FriendRequest) {
    mutate("Friend request accepted.") { friendRepository.acceptFriendRequest(request.id) }
  }

  fun rejectRequest(request: FriendRequest) {
    mutate("Friend request declined.") { friendRepository.rejectFriendRequest(request.id) }
  }

  fun cancelRequest(request: FriendRequest) {
    mutate("Friend request cancelled.") { friendRepository.cancelFriendRequest(request.id) }
  }

  fun removeFriend(friend: FriendUser) {
    mutate("Friend removed.") { friendRepository.removeFriend(friend.id) }
  }

  fun clearMessage() {
    _uiState.update { it.copy(message = null, error = null) }
  }

  private fun mutate(message: String, operation: suspend () -> Unit) {
    if (_uiState.value.isMutating) return
    _uiState.update { it.copy(isMutating = true, error = null, message = null) }
    viewModelScope.launch {
      try {
        operation()
        refreshFriends()
        if (_uiState.value.error == null) {
          _uiState.update { it.copy(message = message) }
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(error = exception) }
      } finally {
        _uiState.update { it.copy(isMutating = false) }
      }
    }
  }

  private suspend fun refreshFriends() {
    try {
      val currentProfile = publicProfileRepository.getCurrentProfile()
      selfUid = currentProfile?.uid
      val (friendships, incoming, outgoing) =
          kotlinx.coroutines.coroutineScope {
            val friendsDeferred = async { friendRepository.getFriends() }
            val incomingDeferred = async { friendRepository.getIncomingRequests() }
            val outgoingDeferred = async { friendRepository.getOutgoingRequests() }
            Triple(friendsDeferred.await(), incomingDeferred.await(), outgoingDeferred.await())
          }
      val uids =
          (friendships.map { it.friendUid } +
                  incoming.map { it.requesterUid } +
                  outgoing.map { it.recipientUid })
              .distinct()
      val usersByUid =
          kotlinx.coroutines.coroutineScope {
            uids
                .map { uid ->
                  async {
                    val profile = publicProfileRepository.getProfile(uid)
                    uid to FriendUser(uid, profile?.username ?: uid)
                  }
                }
                .map { it.await() }
                .toMap()
          }

      val mappedFriends = friendships.mapNotNull { usersByUid[it.friendUid] }
      val mappedIncoming = incoming.mapNotNull { request ->
        usersByUid[request.requesterUid]?.let { user ->
          FriendRequest(user = user, id = request.id)
        }
      }
      val mappedOutgoing = outgoing.mapNotNull { request ->
        usersByUid[request.recipientUid]?.let { user ->
          FriendRequest(user = user, id = request.id)
        }
      }
      _uiState.update { current ->
        val friendIds = mappedFriends.mapTo(mutableSetOf(), FriendUser::id)
        val outgoingByUid = mappedOutgoing.associateBy { it.user.id }
        val incomingByUid = mappedIncoming.associateBy { it.user.id }
        current.copy(
            friends = mappedFriends,
            incomingRequests = mappedIncoming,
            outgoingRequests = mappedOutgoing,
            searchResults =
                current.searchResults.map { result ->
                  result.copy(
                      outgoingRequestId = outgoingByUid[result.user.id]?.id,
                      incomingRequestId = incomingByUid[result.user.id]?.id,
                      isFriend = result.user.id in friendIds,
                  )
                },
            isLoading = false,
            error = null,
        )
      }
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      _uiState.update { it.copy(isLoading = false, error = exception) }
    }
  }

  private companion object {
    const val SEARCH_DEBOUNCE_MILLIS = 300L
  }
}
