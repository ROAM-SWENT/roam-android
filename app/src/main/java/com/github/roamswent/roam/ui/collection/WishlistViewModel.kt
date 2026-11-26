// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.model.location.LocationName
import com.github.roamswent.roam.model.location.LocationNameResolver
import com.github.roamswent.roam.model.wishlist.FriendScannedMonument
import com.github.roamswent.roam.model.wishlist.FriendScannedMonumentsSource
import com.github.roamswent.roam.model.wishlist.ResolvedWishlistItem
import com.github.roamswent.roam.model.wishlist.WishlistItem
import com.github.roamswent.roam.model.wishlist.WishlistMonumentKey
import com.github.roamswent.roam.model.wishlist.WishlistRepository
import com.github.roamswent.roam.model.wishlist.normalizeSharedMonumentName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WishlistMonument(
    val id: String,
    val name: String,
    val city: String = "",
    val country: String = "",
    val photoUrl: String? = null,
    val scannedBy: List<WishlistFriend> = emptyList(),
    val isWishlisted: Boolean = true,
    val photoBytes: ByteArray? = null,
    val unavailable: Boolean = false,
    val location: Coordinates? = null,
    val sourceOwnerUid: String? = null,
    val sourceGuideId: String? = null,
)

data class WishlistFriend(
    val name: String,
    val avatarUrl: String? = null,
    val avatarBytes: ByteArray? = null,
)

/** Removed items remain visible until the next explicit load. */
data class WishlistUiState(
    val monuments: List<WishlistMonument> = emptyList(),
    val refreshVersion: Long = 0,
    val isLoading: Boolean = false,
    val error: Exception? = null,
    val mutationError: Exception? = null,
    val wishlistedKeys: Set<String> = emptySet(),
    val pendingKeys: Set<String> = emptySet(),
    val pendingGuides: Set<Pair<String, String>> = emptySet(),
    val guideKeys: Map<Pair<String, String>, String> = emptyMap(),
    val scannedByError: Exception? = null,
)

class WishlistViewModel(
    private val repository: WishlistRepository,
    private val friendScansSource: FriendScannedMonumentsSource? = null,
    private val locationNameResolver: LocationNameResolver = LocationNameResolver { null },
) : ViewModel() {
  private val _uiState = MutableStateFlow(WishlistUiState())
  val uiState: StateFlow<WishlistUiState> = _uiState.asStateFlow()
  private val items = mutableMapOf<String, WishlistItem>()
  private val locationNames = mutableMapOf<Coordinates, LocationName>()
  private var loadJob: Job? = null
  private var scansJob: Job? = null
  private var friendScans = emptyMap<String, FriendScannedMonument>()

  fun loadWishlist() {
    loadJob?.cancel()
    _uiState.update { it.copy(isLoading = true, error = null) }
    loadJob = viewModelScope.launch {
      var first = true
      try {
        repository.observeWishlist().collectLatest { current ->
          val resolved = if (current.isEmpty()) emptyList() else repository.getResolvedWishlist()
          val resolvedByKey = resolved.associateBy { it.item.monumentKey }
          current.forEach { items[it.monumentKey] = it }
          val keys = current.mapTo(mutableSetOf()) { it.monumentKey }
          _uiState.update { state ->
            val previous = if (first) emptyMap() else state.monuments.associateBy { it.id }
            val monuments =
                current.map { item ->
                  val result = resolvedByKey[item.monumentKey]?.takeIf { it.item == item }
                  val guide = (result as? ResolvedWishlistItem.Available)?.guide
                  val location = guide?.location ?: item.location
                  val locationName = locationNames[location]
                  WishlistMonument(
                      id = item.monumentKey,
                      name = guide?.monumentName ?: item.monumentName,
                      photoBytes = guide?.photoBytes,
                      unavailable = result !is ResolvedWishlistItem.Available,
                      location = location,
                      city = locationName?.city.orEmpty(),
                      country = locationName?.country.orEmpty(),
                      sourceOwnerUid = item.sourceOwnerUid,
                      sourceGuideId = if (guide != null) item.sourceGuideId else null,
                      scannedBy =
                          friendsFor(guide?.monumentName ?: item.monumentName, item.sourceOwnerUid),
                  )
                } + previous.values.filter { it.id !in keys }.map { it.copy(isWishlisted = false) }
            state.copy(
                monuments = monuments.sortedBy { it.name.lowercase() },
                wishlistedKeys = keys,
                guideKeys =
                    current.associate { (it.sourceOwnerUid to it.sourceGuideId) to it.monumentKey },
                refreshVersion = if (first) state.refreshVersion + 1 else state.refreshVersion,
                isLoading = false,
                error = null,
            )
          }
          first = false
          coroutineScope {
            _uiState.value.monuments
                .filter { it.id in keys && it.location != null }
                .forEach { monument ->
                  val coordinates = monument.location ?: return@forEach
                  if (coordinates !in locationNames) {
                    launch {
                      val name = locationNameResolver.resolve(coordinates) ?: return@launch
                      locationNames[coordinates] = name
                      _uiState.update { state ->
                        state.copy(
                            monuments =
                                state.monuments.map {
                                  if (it.location == coordinates)
                                      it.copy(city = name.city, country = name.country)
                                  else it
                                }
                        )
                      }
                    }
                  }
                }
          }
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(isLoading = false, error = exception) }
      }
    }
  }

  fun retryLoadingWishlist() = loadWishlist()

  fun retryLoadingScannedFriends() {
    val source = friendScansSource ?: return
    scansJob?.cancel()
    _uiState.update { it.copy(scannedByError = null) }
    scansJob = viewModelScope.launch {
      try {
        source.observe().collect { scans ->
          friendScans = scans.associateBy { it.key }
          _uiState.update { state ->
            state.copy(
                monuments =
                    state.monuments.map { monument ->
                      monument.copy(scannedBy = friendsFor(monument.name, monument.sourceOwnerUid))
                    },
                scannedByError = null,
            )
          }
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        friendScans = emptyMap()
        _uiState.update {
          it.copy(
              monuments = it.monuments.map { monument -> monument.copy(scannedBy = emptyList()) },
              scannedByError = exception,
          )
        }
      }
    }
  }

  private fun friendsFor(name: String, ownerUid: String?): List<WishlistFriend> =
      friendScans[normalizeSharedMonumentName(name)]
          ?.friends
          .orEmpty()
          .sortedBy { it.uid != ownerUid }
          .map {
            WishlistFriend(
                name = it.profile?.username.orEmpty(),
                avatarBytes = it.profile?.avatarBytes,
            )
          }

  fun setWishlisted(monumentId: String, isWishlisted: Boolean) {
    val item = items[monumentId]
    if (item == null) {
      _uiState.update { it.copy(mutationError = IllegalStateException("Wishlist item is missing")) }
      return
    }
    updateWishlist(item, isWishlisted)
  }

  fun toggleGuide(ownerUid: String, guide: Guide) {
    try {
      val guideKey = ownerUid to guide.id
      val existingKey = _uiState.value.guideKeys[guideKey]
      val key = existingKey ?: WishlistMonumentKey.forSharedGuide(ownerUid, guide.id)
      val isWishlisted = existingKey != null
      val item =
          items[key]?.takeIf { isWishlisted }
              ?: WishlistItem(
                  monumentKey = key,
                  sourceOwnerUid = ownerUid,
                  sourceGuideId = guide.id,
                  monumentName = guide.monumentName,
                  addedAt = System.currentTimeMillis(),
                  location = guide.location,
              )
      updateWishlist(item, !isWishlisted)
    } catch (exception: IllegalArgumentException) {
      _uiState.update { it.copy(mutationError = exception) }
    }
  }

  fun clearMutationError() {
    _uiState.update { it.copy(mutationError = null) }
  }

  private fun updateWishlist(item: WishlistItem, isWishlisted: Boolean) {
    val key = item.monumentKey
    val guideKey = item.sourceOwnerUid to item.sourceGuideId
    if (
        key in _uiState.value.pendingKeys ||
            guideKey in _uiState.value.pendingGuides ||
            _uiState.value.isLoading ||
            _uiState.value.error != null
    )
        return
    _uiState.update {
      it.copy(
          pendingKeys = it.pendingKeys + key,
          pendingGuides = it.pendingGuides + guideKey,
          mutationError = null,
      )
    }
    viewModelScope.launch {
      try {
        if (isWishlisted) repository.addToWishlist(item) else repository.removeFromWishlist(key)
        items[key] = item
        _uiState.update { state ->
          state.copy(
              wishlistedKeys =
                  if (isWishlisted) state.wishlistedKeys + key else state.wishlistedKeys - key,
              guideKeys =
                  if (isWishlisted)
                      state.guideKeys + ((item.sourceOwnerUid to item.sourceGuideId) to key)
                  else state.guideKeys - (item.sourceOwnerUid to item.sourceGuideId),
              monuments =
                  state.monuments.map {
                    if (it.id == key) it.copy(isWishlisted = isWishlisted) else it
                  },
          )
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(mutationError = exception) }
      } finally {
        _uiState.update {
          it.copy(pendingKeys = it.pendingKeys - key, pendingGuides = it.pendingGuides - guideKey)
        }
      }
    }
  }
}
