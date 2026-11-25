/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 * Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
 */

package com.github.roamswent.roam.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.leaderboard.LeaderboardEntry
import com.github.roamswent.roam.model.leaderboard.LeaderboardRepository
import com.github.roamswent.roam.model.profile.PublicProfile
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
    val avatarBytes: ByteArray? = null,
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

/**
 * State of the Friends screen.
 *
 * @property leaderboard The signed-in user and their friends, in the order and with the ranks given
 *   by [LeaderboardRepository.getFriendsLeaderboard]. Kept from the last successful load when a
 *   later load fails.
 * @property leaderboardError Failure of the last leaderboard load, separate from [error] so that
 *   friends and requests stay usable; cleared by the next successful load.
 * @property isLeaderboardLoading Whether a leaderboard load is in progress. Independent of
 *   [isLoading]: friends and requests are published without waiting for the leaderboard.
 * @property hasLoadedLeaderboard Whether a leaderboard load has finished, successfully or not. It
 *   stays `false` until the Leaderboard segment is first shown, as the leaderboard is only loaded
 *   on demand.
 * @property currentUserUid UID of the signed-in user, used to highlight their leaderboard entry.
 */
data class FriendsUiState(
    val friends: List<FriendUser> = emptyList(),
    val incomingRequests: List<FriendRequest> = emptyList(),
    val outgoingRequests: List<FriendRequest> = emptyList(),
    val searchResults: List<FriendSearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val isSearching: Boolean = false,
    val isMutating: Boolean = false,
    val error: Exception? = null,
    val message: String? = null,
    val leaderboard: List<LeaderboardEntry> = emptyList(),
    val leaderboardError: Exception? = null,
    val isLeaderboardLoading: Boolean = false,
    val hasLoadedLeaderboard: Boolean = false,
    val currentUserUid: String? = null,
)

/**
 * Coordinates friends, public-profile and leaderboard repositories for the Friends screen.
 *
 * The leaderboard is loaded on demand, only while the Leaderboard segment is shown, as reported by
 * [setLeaderboardVisible]:
 * - [loadFriends] loads friends and requests only.
 * - Each time the leaderboard goes from hidden to shown it is reloaded, as counts can change
 *   outside this screen, such as after a verified scan. Staying shown loads nothing more, and a
 *   load still running when it is shown again is kept unless a friend changed since it started.
 * - [refresh] reloads what is shown: the friend lists, or the leaderboard alone.
 * - Accepting or removing a friend reloads the leaderboard at once if it is shown, otherwise marks
 *   it stale. Other requests do not change who is on it.
 *
 * The leaderboard loads in its own job: friends, requests and their errors are published as soon as
 * they are ready, whatever the leaderboard is doing. A newer load cancels a pending one, so an
 * older result never overwrites a newer one.
 */
class FriendsViewModel(
    private val friendRepository: FriendRepository,
    private val publicProfileRepository: PublicProfileRepository,
    private val leaderboardRepository: LeaderboardRepository,
    private val cacheClockMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) : ViewModel() {
  private val _uiState = MutableStateFlow(FriendsUiState())
  val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

  private var selfUid: String? = null
  private var searchJob: Job? = null
  private var loadJob: Job? = null
  private var leaderboardJob: Job? = null
  private var isLeaderboardVisible = false
  // Cleared when a load starts and set by a later friend change or a failure, so a load still
  // running is only kept when shown again if its result will be up to date.
  private var isLeaderboardStale = true
  private val profileCache = mutableMapOf<String, CachedProfile>()

  fun loadFriends() {
    startLoading(forceRefresh = false)
  }

  /** Reloads what is shown: the leaderboard alone while it is visible, otherwise the friends. */
  fun refresh() {
    if (_uiState.value.isMutating) return
    if (isLeaderboardVisible) startLeaderboardLoad() else startLoading(forceRefresh = true)
  }

  /**
   * Called by the screen when the Leaderboard segment is shown or hidden, including when the screen
   * leaves the composition. Going from hidden to shown reloads the leaderboard, so changes made
   * while it was hidden, such as a newly verified monument, are shown. A repeated call while it
   * stays shown does nothing, and neither does showing it during a load that is still up to date.
   */
  fun setLeaderboardVisible(visible: Boolean) {
    val becameVisible = visible && !isLeaderboardVisible
    isLeaderboardVisible = visible
    if (!becameVisible) return
    if (leaderboardJob?.isActive == true && !isLeaderboardStale) return
    startLeaderboardLoad()
  }

  private fun startLoading(forceRefresh: Boolean) {
    if (loadJob?.isActive == true || _uiState.value.isLoading) return
    if (forceRefresh) profileCache.clear()
    _uiState.update { it.copy(isLoading = true, error = null) }
    loadJob = viewModelScope.launch { refreshFriendLists() }
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
                            user = FriendUser(profile.uid, profile.username, profile.avatarBytes),
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
    mutate("Friend request sent.", changesFriends = false) {
      friendRepository.sendFriendRequest(user.id)
    }
  }

  fun acceptRequest(request: FriendRequest) {
    mutate("Friend request accepted.", changesFriends = true) {
      friendRepository.acceptFriendRequest(request.id)
    }
  }

  fun rejectRequest(request: FriendRequest) {
    mutate("Friend request declined.", changesFriends = false) {
      friendRepository.rejectFriendRequest(request.id)
    }
  }

  fun cancelRequest(request: FriendRequest) {
    mutate("Friend request cancelled.", changesFriends = false) {
      friendRepository.cancelFriendRequest(request.id)
    }
  }

  fun removeFriend(friend: FriendUser) {
    mutate("Friend removed.", changesFriends = true) { friendRepository.removeFriend(friend.id) }
  }

  fun clearMessage() {
    _uiState.update { it.copy(message = null, error = null) }
  }

  /**
   * Runs [operation], then reloads the friend lists. When [changesFriends], who is on the
   * leaderboard changed: it is reloaded if shown, otherwise marked stale so that a load still
   * running is not kept on its next showing.
   */
  private fun mutate(message: String, changesFriends: Boolean, operation: suspend () -> Unit) {
    if (_uiState.value.isMutating) return
    _uiState.update { it.copy(isMutating = true, error = null, message = null) }
    viewModelScope.launch {
      try {
        operation()
        if (changesFriends) {
          if (isLeaderboardVisible) startLeaderboardLoad() else isLeaderboardStale = true
        }
        refreshFriendLists()
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

  /**
   * Loads the leaderboard in its own job, replacing any pending load. A failure keeps the previous
   * entries, is exposed as [FriendsUiState.leaderboardError] and leaves the leaderboard stale; it
   * is retried on its next showing like any other. A cancelled load changes nothing.
   */
  private fun startLeaderboardLoad() {
    leaderboardJob?.cancel()
    isLeaderboardStale = false
    _uiState.update { it.copy(isLeaderboardLoading = true) }
    leaderboardJob = viewModelScope.launch {
      try {
        val entries = leaderboardRepository.getFriendsLeaderboard()
        _uiState.update {
          it.copy(
              leaderboard = entries,
              leaderboardError = null,
              isLeaderboardLoading = false,
              hasLoadedLeaderboard = true,
          )
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        isLeaderboardStale = true
        _uiState.update {
          it.copy(
              leaderboardError = exception,
              isLeaderboardLoading = false,
              hasLoadedLeaderboard = true,
          )
        }
      }
    }
  }

  private suspend fun refreshFriendLists() {
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
      profileCache.keys.retainAll(uids.toSet())
      val cacheTimestamp = cacheClockMillis()
      val usersByUid =
          kotlinx.coroutines.coroutineScope {
            uids
                .map { uid ->
                  async {
                    val profile = getCachedProfile(uid, cacheTimestamp)
                    uid to
                        FriendUser(
                            uid,
                            profile?.username ?: uid,
                            profile?.avatarBytes,
                        )
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
            hasLoaded = true,
            error = null,
            currentUserUid = selfUid,
        )
      }
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      _uiState.update {
        it.copy(isLoading = false, hasLoaded = true, error = exception, currentUserUid = selfUid)
      }
    }
  }

  private suspend fun getCachedProfile(uid: String, nowMillis: Long): PublicProfile? {
    val cached = profileCache[uid]
    if (cached != null && nowMillis - cached.cachedAtMillis < PROFILE_CACHE_TTL_MILLIS) {
      return cached.profile
    }

    return publicProfileRepository.getProfile(uid).also { profile ->
      profileCache[uid] = CachedProfile(profile, nowMillis)
    }
  }

  private data class CachedProfile(
      val profile: PublicProfile?,
      val cachedAtMillis: Long,
  )

  private companion object {
    const val SEARCH_DEBOUNCE_MILLIS = 300L
    const val PROFILE_CACHE_TTL_MILLIS = 5 * 60 * 1000L
    const val NANOS_PER_MILLI = 1_000_000L
  }
}
