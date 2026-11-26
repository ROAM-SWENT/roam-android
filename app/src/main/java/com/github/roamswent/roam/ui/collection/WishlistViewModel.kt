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
import com.github.roamswent.roam.model.wishlist.FriendWishlistedMonument
import com.github.roamswent.roam.model.wishlist.FriendWishlistedMonumentsSource
import com.github.roamswent.roam.model.wishlist.ResolvedWishlistItem
import com.github.roamswent.roam.model.wishlist.WishlistItem
import com.github.roamswent.roam.model.wishlist.WishlistMonumentKey
import com.github.roamswent.roam.model.wishlist.WishlistRepository
import com.github.roamswent.roam.model.wishlist.WishlistUnavailableReason
import com.github.roamswent.roam.model.wishlist.normalizeSharedMonumentName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WishlistMonument(
    val id: String,
    val name: String,
    val city: String = "",
    val country: String = "",
    val photoUrl: String? = null,
    val scannedBy: List<WishlistFriend> = emptyList(),
    val wishlistedBy: List<WishlistFriend> = emptyList(),
    val isWishlisted: Boolean = true,
    val photoBytes: ByteArray? = null,
    val unavailable: Boolean = false,
    val unavailableReason: WishlistUnavailableReason? = null,
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
    val guideKeys: Map<Pair<String, String>, String> = emptyMap(),
    val scannedByError: Exception? = null,
    val wishlistedByError: Exception? = null,
    val mutationSuccess: WishlistMutationSuccess? = null,
)

data class WishlistMutationSuccess(val id: Long, val added: Boolean)

class WishlistViewModel(
    private val repository: WishlistRepository,
    private val friendScansSource: FriendScannedMonumentsSource? = null,
    private val friendWishlistsSource: FriendWishlistedMonumentsSource? = null,
    private val locationNameResolver: LocationNameResolver = LocationNameResolver { null },
) : ViewModel() {
  /**
   * A wishlist change shown before the repository confirms it. Firestore writes only complete once
   * the server answers, so the state must not wait for them.
   */
  private class Mutation(
      val item: WishlistItem,
      val isWishlisted: Boolean,
      val previouslyWishlisted: Boolean,
  ) {
    var written = false
  }

  private val _uiState = MutableStateFlow(WishlistUiState())
  val uiState: StateFlow<WishlistUiState> = _uiState.asStateFlow()
  private val items = mutableMapOf<String, WishlistItem>()
  private val mutations = mutableMapOf<String, Mutation>()
  private var latestSnapshotKeys = emptySet<String>()
  private var mutationId = 0L
  private val locationNames = mutableMapOf<Coordinates, LocationName>()
  private val resolvingLocations = mutableSetOf<Coordinates>()
  private var resolvesGuides = true
  private var loadJob: Job? = null
  private var scansJob: Job? = null
  private var wishlistsJob: Job? = null
  private var availabilityJob: Job? = null
  private var friendScans = emptyMap<String, FriendScannedMonument>()
  private var friendWishlists = emptyMap<String, FriendWishlistedMonument>()

  /** Observes the wishlist and resolves its guides, photos and place names for display. */
  fun loadWishlist() = observe(resolveGuides = true)

  /**
   * Only observes which monuments and guides are saved, for screens that just need a bookmark
   * state. It resolves and geocodes nothing, and ignores snapshots that changed no entry.
   */
  fun loadWishlistEntries() = observe(resolveGuides = false)

  private fun observe(resolveGuides: Boolean) {
    resolvesGuides = resolveGuides
    loadJob?.cancel()
    _uiState.update { it.copy(isLoading = true, error = null) }
    loadJob = viewModelScope.launch {
      var first = true
      try {
        val snapshots =
            if (resolveGuides) repository.observeWishlist()
            else repository.observeWishlist().distinctUntilChanged()
        snapshots.collectLatest { current ->
          latestSnapshotKeys = current.mapTo(mutableSetOf()) { it.monumentKey }
          val resolved =
              if (current.isEmpty() || !resolveGuides) emptyList()
              else repository.getResolvedWishlist()
          val resolvedByKey = resolved.associateBy { it.item.monumentKey }
          current.forEach { items[it.monumentKey] = it }
          mutations.entries.removeAll { (key, mutation) ->
            mutation.written && (key in latestSnapshotKeys) == mutation.isWishlisted
          }
          _uiState.update { state ->
            val effective =
                current.filterNot { it.monumentKey in mutations } +
                    mutations.values.filter { it.isWishlisted }.map { it.item }
            val keys = effective.mapTo(mutableSetOf()) { it.monumentKey }
            if (!resolveGuides) {
              return@update state.copy(
                  wishlistedKeys = keys,
                  guideKeys =
                      effective.associate {
                        (it.sourceOwnerUid to it.sourceGuideId) to it.monumentKey
                      },
                  isLoading = false,
                  error = null,
              )
            }
            val known = state.monuments.associateBy { it.id }
            val previous = if (first) emptyMap() else known
            val monuments =
                effective.map { item ->
                  val result = resolvedByKey[item.monumentKey]?.takeIf { it.item == item }
                  val existing = known[item.monumentKey]
                  val monument =
                      if (result == null && item.monumentKey in mutations && existing != null) {
                        existing.copy(isWishlisted = true)
                      } else {
                        resolve(WishlistMonument(item.monumentKey, item.monumentName), item, result)
                      }
                  decorate(monument)
                } + previous.values.filter { it.id !in keys }.map { it.copy(isWishlisted = false) }
            state.copy(
                monuments = monuments.sortedBy { it.name.lowercase() },
                wishlistedKeys = keys,
                guideKeys =
                    effective.associate {
                      (it.sourceOwnerUid to it.sourceGuideId) to it.monumentKey
                    },
                refreshVersion = if (first) state.refreshVersion + 1 else state.refreshVersion,
                isLoading = false,
                error = null,
            )
          }
          first = false
          if (resolveGuides) resolveLocationNames()
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(isLoading = false, error = exception) }
      }
    }
  }

  fun retryLoadingWishlist() = observe(resolvesGuides)

  /**
   * Resolves the referenced guides again, so a guide that was deleted or is no longer shared stops
   * looking available. It runs when the friends' shared guides change and on demand for a retry.
   */
  fun refreshAvailability() {
    availabilityJob?.cancel()
    availabilityJob = viewModelScope.launch {
      val resolved =
          try {
            repository.getResolvedWishlist()
          } catch (exception: CancellationException) {
            throw exception
          } catch (_: Exception) {
            return@launch
          }
      val resolvedByKey = resolved.associateBy { it.item.monumentKey }
      _uiState.update { state ->
        state.copy(
            monuments =
                state.monuments.map { monument ->
                  val item = items[monument.id]
                  val result = resolvedByKey[monument.id]
                  if (
                      item == null ||
                          result == null ||
                          result.item != item ||
                          !monument.isWishlisted
                  )
                      monument
                  else decorate(resolve(monument, item, result))
                }
        )
      }
      resolveLocationNames()
    }
  }

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
          refreshAvailability()
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

  fun retryLoadingWishlistFriends() {
    val source = friendWishlistsSource ?: return
    wishlistsJob?.cancel()
    _uiState.update { it.copy(wishlistedByError = null) }
    wishlistsJob = viewModelScope.launch {
      try {
        source.observe().collect { wishlists ->
          friendWishlists = wishlists.associateBy { it.key }
          _uiState.update { state ->
            state.copy(
                monuments =
                    state.monuments.map { monument ->
                      monument.copy(wishlistedBy = wishlistingFriendsFor(monument.name))
                    },
                wishlistedByError = null,
            )
          }
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        friendWishlists = emptyMap()
        _uiState.update {
          it.copy(
              monuments =
                  it.monuments.map { monument -> monument.copy(wishlistedBy = emptyList()) },
              wishlistedByError = exception,
          )
        }
      }
    }
  }

  /** Starts the friend observations unless they already run. */
  fun startFriendObservation() {
    if (scansJob?.isActive != true) retryLoadingScannedFriends()
    if (wishlistsJob?.isActive != true) retryLoadingWishlistFriends()
  }

  /** Closes the friends' listeners while the wishlist is not shown. */
  fun stopFriendObservation() {
    scansJob?.cancel()
    wishlistsJob?.cancel()
    availabilityJob?.cancel()
  }

  fun retryLoadingFriends() {
    retryLoadingScannedFriends()
    retryLoadingWishlistFriends()
  }

  private fun resolve(
      base: WishlistMonument,
      item: WishlistItem,
      result: ResolvedWishlistItem?,
  ): WishlistMonument =
      when (result) {
        is ResolvedWishlistItem.Available ->
            base.copy(
                name = result.guide.monumentName,
                photoBytes = result.guide.photoBytes,
                unavailable = false,
                unavailableReason = null,
                location = result.guide.location ?: item.location,
                sourceOwnerUid = item.sourceOwnerUid,
                sourceGuideId = item.sourceGuideId,
            )
        // A guide that cannot be read must not keep showing what it used to share.
        else ->
            base.copy(
                name = item.monumentName,
                photoBytes = null,
                unavailable = true,
                unavailableReason =
                    (result as? ResolvedWishlistItem.Unavailable)?.reason
                        ?: WishlistUnavailableReason.UNKNOWN,
                location = item.location,
                sourceOwnerUid = item.sourceOwnerUid,
                sourceGuideId = null,
            )
      }

  private fun decorate(monument: WishlistMonument): WishlistMonument {
    val name = monument.location?.let(locationNames::get)
    return monument.copy(
        city = name?.city.orEmpty(),
        country = name?.country.orEmpty(),
        scannedBy = friendsFor(monument.name, monument.sourceOwnerUid),
        wishlistedBy = wishlistingFriendsFor(monument.name),
    )
  }

  /**
   * Looks the place names up outside the wishlist collector: a slow or failing lookup must neither
   * delay nor stop wishlist updates. Without a name, the coordinates stay shown.
   */
  private fun resolveLocationNames() {
    _uiState.value.monuments
        .filter { it.isWishlisted }
        .mapNotNull { it.location }
        .distinct()
        .filter { it !in locationNames && resolvingLocations.add(it) }
        .forEach { coordinates ->
          viewModelScope.launch {
            try {
              val name = locationNameResolver.resolve(coordinates)
              if (name != null) {
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
            } catch (exception: CancellationException) {
              throw exception
            } catch (_: Exception) {
              // Treated as "no name".
            } finally {
              resolvingLocations.remove(coordinates)
            }
          }
        }
  }

  private fun wishlistingFriendsFor(name: String): List<WishlistFriend> =
      friendWishlists[normalizeSharedMonumentName(name)]?.friends.orEmpty().map {
        WishlistFriend(name = it.profile?.username.orEmpty(), avatarBytes = it.profile?.avatarBytes)
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

  fun clearMutationSuccess(id: Long) {
    _uiState.update { if (it.mutationSuccess?.id == id) it.copy(mutationSuccess = null) else it }
  }

  private fun applyMembership(item: WishlistItem, isWishlisted: Boolean) {
    val key = item.monumentKey
    val guideKey = item.sourceOwnerUid to item.sourceGuideId
    _uiState.update { state ->
      state.copy(
          wishlistedKeys =
              if (isWishlisted) state.wishlistedKeys + key else state.wishlistedKeys - key,
          guideKeys =
              if (isWishlisted) state.guideKeys + (guideKey to key) else state.guideKeys - guideKey,
          monuments =
              state.monuments.map {
                if (it.id == key) it.copy(isWishlisted = isWishlisted) else it
              },
      )
    }
  }

  /**
   * Shows the change at once and writes it in the background. The button is never locked while the
   * write is pending, as offline it only completes once the server answers. A failure restores the
   * previous state, unless a newer change of the same monument has replaced this one.
   */
  private fun updateWishlist(item: WishlistItem, isWishlisted: Boolean) {
    val key = item.monumentKey
    if (_uiState.value.isLoading || _uiState.value.error != null) return
    val mutation =
        Mutation(item, isWishlisted, previouslyWishlisted = key in _uiState.value.wishlistedKeys)
    mutations[key] = mutation
    items[key] = item
    applyMembership(item, isWishlisted)
    _uiState.update {
      it.copy(
          mutationError = null,
          mutationSuccess = WishlistMutationSuccess(++mutationId, isWishlisted),
      )
    }
    viewModelScope.launch {
      try {
        if (isWishlisted) repository.addToWishlist(item) else repository.removeFromWishlist(key)
        if (mutations[key] === mutation) {
          if ((key in latestSnapshotKeys) == isWishlisted) mutations.remove(key)
          else mutation.written = true
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        if (mutations[key] === mutation) {
          mutations.remove(key)
          applyMembership(item, mutation.previouslyWishlisted)
          _uiState.update { it.copy(mutationError = exception, mutationSuccess = null) }
        }
      }
    }
  }
}
