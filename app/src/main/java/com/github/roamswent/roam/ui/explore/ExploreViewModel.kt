/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 * Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
 * Co-authored-by: Codex <codex@openai.com>
 */

package com.github.roamswent.roam.ui.explore

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.roamswent.roam.model.basemap.BaseMapPolygon
import com.github.roamswent.roam.model.basemap.OfflineBaseMapRepository
import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.guide.MonumentSharingRepository
import com.github.roamswent.roam.model.guide.SavedGuideRepository
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.model.location.LocationProvider
import com.github.roamswent.roam.model.permission.LocationPermissionDecision
import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import com.github.roamswent.roam.model.wishlist.WishlistItem
import com.github.roamswent.roam.model.wishlist.WishlistMonumentKey
import com.github.roamswent.roam.model.wishlist.WishlistRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal enum class ExploreWishlistStatus {
  LOADING,
  SAVED,
  NOT_SAVED,
  CHECK_FAILED,
  UNAVAILABLE,
}

internal data class ExploreWishlistWrite(val key: String, val status: Status) {
  enum class Status {
    PENDING,
    SAVED,
    FAILED,
  }
}

sealed interface ExploreUiState {
  data class PermissionRequired(val decision: LocationPermissionDecision) : ExploreUiState

  data object Loading : ExploreUiState

  data object LocationUnavailable : ExploreUiState

  data class LocationAvailable(val coordinates: Coordinates) : ExploreUiState
}

/** The map shares one avatar icon across the independent pins from this owner. */
data class ExploreFriendMonuments(
    val ownerUid: String,
    val profile: PublicProfile?,
    val monuments: List<ExploreMonument>,
    val scanDates: Map<String, Long> = emptyMap(),
)

/**
 * Loads the user's current coordinates and exposes permission and retrieval state to the UI through
 * [uiState], the saved monuments to pin on the map through [monuments], and the world map drawn
 * under them while offline through [offlineBaseMap].
 */
class ExploreViewModel(
    private val locationProvider: LocationProvider,
    savedGuideRepository: SavedGuideRepository,
    private val offlineBaseMapRepository: OfflineBaseMapRepository,
    friendRepository: FriendRepository,
    private val publicProfileRepository: PublicProfileRepository,
    private val monumentSharingRepository: MonumentSharingRepository,
    private val wishlistRepository: WishlistRepository? = null,
) : ViewModel() {
  private val _uiState =
      MutableStateFlow<ExploreUiState>(
          ExploreUiState.PermissionRequired(LocationPermissionDecision.NOT_ASKED)
      )
  val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

  private val _monumentsLoaded = MutableStateFlow(false)

  /**
   * True once the saved monuments have been read (from the local cache when offline), or once
   * reading ended or failed. Lets the UI wait for them before framing the map, instead of showing
   * the world view for a moment and then jumping to the monuments.
   */
  val monumentsLoaded: StateFlow<Boolean> = _monumentsLoaded.asStateFlow()

  /**
   * Saved guides that have a location, kept up to date with the repository while observed. Starts
   * empty; if observation fails, the failure is logged and the last received monuments stay on the
   * map. Cancellation is rethrown so it is never swallowed.
   */
  val monuments: StateFlow<List<ExploreMonument>> =
      savedGuideRepository
          .observeSavedGuides()
          .map { guides -> guides.toExploreMonuments() }
          .distinctUntilChanged()
          // Runs just before the value reaches the StateFlow below, with no suspension in between,
          // so the UI never sees "loaded" with the previous (empty) list.
          .onEach { _monumentsLoaded.value = true }
          .catch { exception ->
            if (exception is CancellationException) throw exception
            Log.e(TAG, "Failed to observe saved monuments", exception)
          }
          .onCompletion { _monumentsLoaded.value = true }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

  /** Reloads the friendship snapshot whenever Explore resumes collecting. */
  val friendMonuments: StateFlow<List<ExploreFriendMonuments>> = flow {
    val friendUids = friendRepository.getFriends().map { it.friendUid }.distinct()
    if (friendUids.isNotEmpty()) {
      emitAll(
          combine(friendUids.map(::observeFriendMonuments)) { friends ->
            friends.filter { it.monuments.isNotEmpty() }
          }
      )
    }
  }
      .catch { exception ->
        if (exception is CancellationException) throw exception
        Log.w(TAG, "Failed to load friends for Explore", exception)
        emit(emptyList())
      }
      .stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(replayExpirationMillis = 0),
          emptyList(),
      )

  private fun observeFriendMonuments(ownerUid: String): Flow<ExploreFriendMonuments> = flow {
    emit(ExploreFriendMonuments(ownerUid, null, emptyList()))
    var profile: PublicProfile? = null
    var profileLoaded = false
    monumentSharingRepository.observeSharedGuidesFrom(ownerUid).collect { guides ->
      val monuments = guides.toExploreMonuments()
      val scanDates = buildMap {
        for (guide in guides) {
          val date = guide.discoveryDate
          if (guide.location != null && date != null) put(guide.id, date)
        }
      }
      emit(ExploreFriendMonuments(ownerUid, profile, monuments, scanDates))
      if (!profileLoaded && monuments.isNotEmpty()) {
        profileLoaded = true
        profile = loadFriendProfile(ownerUid)
        emit(ExploreFriendMonuments(ownerUid, profile, monuments, scanDates))
      }
    }
  }
      .catch { exception ->
        if (exception is CancellationException) throw exception
        Log.w(TAG, "Failed to observe shared monuments from $ownerUid", exception)
        emit(ExploreFriendMonuments(ownerUid, null, emptyList()))
      }

  internal suspend fun checkWishlist(key: String): ExploreWishlistStatus {
    val repository = wishlistRepository ?: return ExploreWishlistStatus.UNAVAILABLE
    return try {
      if (repository.isWishlisted(key)) ExploreWishlistStatus.SAVED
      else ExploreWishlistStatus.NOT_SAVED
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      Log.w(TAG, "Failed to check Explore wishlist", exception)
      ExploreWishlistStatus.CHECK_FAILED
    }
  }

  private val _wishlistWrite = MutableStateFlow<ExploreWishlistWrite?>(null)
  internal val wishlistWrite = _wishlistWrite.asStateFlow()

  internal fun addToWishlist(pin: ExploreSharedPin) {
    val repository = wishlistRepository ?: return
    val key = WishlistMonumentKey.fromCoordinates(pin.monument.coordinates)
    val previous = _wishlistWrite.value
    if (
        previous?.status == ExploreWishlistWrite.Status.PENDING ||
            previous == ExploreWishlistWrite(key, ExploreWishlistWrite.Status.SAVED)
    )
        return
    _wishlistWrite.value = ExploreWishlistWrite(key, ExploreWishlistWrite.Status.PENDING)
    viewModelScope.launch {
      try {
        repository.addToWishlist(
            WishlistItem(
                monumentKey = key,
                sourceOwnerUid = pin.ownerUid,
                sourceGuideId = pin.monument.guideId,
                monumentName = pin.monument.name,
                addedAt = System.currentTimeMillis(),
            )
        )
        _wishlistWrite.value = ExploreWishlistWrite(key, ExploreWishlistWrite.Status.SAVED)
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Log.w(TAG, "Failed to add Explore monument to wishlist", exception)
        _wishlistWrite.value = ExploreWishlistWrite(key, ExploreWishlistWrite.Status.FAILED)
      }
    }
  }

  internal suspend fun friendsWhoWishlisted(key: String): List<PublicProfile> =
      try {
        wishlistRepository?.getFriendsWhoWishlisted(key).orEmpty()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Log.w(TAG, "Failed to load friend wishlist information", exception)
        emptyList()
      }

  private suspend fun loadFriendProfile(ownerUid: String): PublicProfile? =
      try {
        publicProfileRepository.getProfile(ownerUid)
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Log.w(TAG, "Failed to load Explore avatar for $ownerUid", exception)
        null
      }

  private val _offlineBaseMap = MutableStateFlow<List<BaseMapPolygon>>(emptyList())

  /**
   * Country outlines drawn instead of the map tiles while offline. Empty until [loadOfflineBaseMap]
   * has been called and has finished, or if the bundled map cannot be read.
   */
  val offlineBaseMap: StateFlow<List<BaseMapPolygon>> = _offlineBaseMap.asStateFlow()

  private var offlineBaseMapRequest: Job? = null

  /**
   * Loads the offline base map once; later calls do nothing unless the previous load failed. Called
   * when Explore goes offline, so users who stay online never parse it.
   */
  fun loadOfflineBaseMap() {
    if (offlineBaseMapRequest?.isActive == true || _offlineBaseMap.value.isNotEmpty()) return
    offlineBaseMapRequest = viewModelScope.launch {
      try {
        _offlineBaseMap.value = offlineBaseMapRepository.countries()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        // The pins stay usable on an empty map, so the failure is only logged.
        Log.e(TAG, "Failed to load the offline base map", exception)
      }
    }
  }

  private var permissionGranted = false
  private var locationRequest: Job? = null
  private var requestGeneration = 0

  /** Receives the latest permission snapshot from the UI permission controller. */
  fun onPermissionStateChanged(
      isGranted: Boolean,
      decision: LocationPermissionDecision,
  ) {
    if (!isGranted) {
      permissionGranted = false
      requestGeneration++
      locationRequest?.cancel()
      locationRequest = null
      _uiState.value = ExploreUiState.PermissionRequired(decision)
      return
    }

    if (!permissionGranted) {
      permissionGranted = true
      loadLocation()
    }
  }

  /** Retries the location lookup when permission is currently granted. */
  fun retryLocation() {
    if (permissionGranted) loadLocation()
  }

  private fun loadLocation() {
    val generation = ++requestGeneration
    locationRequest?.cancel()
    _uiState.value = ExploreUiState.Loading
    locationRequest = viewModelScope.launch {
      val coordinates = locationProvider.currentCoordinates()
      if (permissionGranted && generation == requestGeneration) {
        _uiState.value =
            coordinates?.let { ExploreUiState.LocationAvailable(it) }
                ?: ExploreUiState.LocationUnavailable
      }
    }
  }

  private companion object {
    const val TAG = "ExploreViewModel"

    /** Keeps observing across short interruptions such as configuration changes. */
    const val STOP_TIMEOUT_MILLIS = 5_000L
  }
}
