// Co-authored-by: Gemini 4 Neon <noreply@google.com>

package com.github.roamswent.roam.model.wishlist

import com.github.roamswent.roam.model.firestore.RoamFirestore
import com.github.roamswent.roam.model.friend.FirestoreFriendRepository
import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.friend.FriendRepositoryException
import com.github.roamswent.roam.model.guide.GuideFirestoreMapper
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.model.profile.FirestorePublicProfileRepository
import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.model.profile.PublicProfileException
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed implementation of [WishlistRepository] storing lightweight reference documents
 * under `users/{uid}/wishlist/{monumentKey}`.
 *
 * @param firestore The [FirebaseFirestore] instance (defaults to [RoamFirestore.instance] with
 *   offline persistence enabled).
 * @param auth The [FirebaseAuth] instance providing the currently signed-in user.
 * @param friendRepository Repository used to retrieve accepted friends for social aggregation.
 * @param publicProfileRepository Repository used to resolve public profiles for users who
 *   wishlisted a monument.
 */
class FirestoreWishlistRepository(
    private val firestore: FirebaseFirestore = RoamFirestore.instance,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val friendRepository: FriendRepository = FirestoreFriendRepository(firestore, auth),
    private val publicProfileRepository: PublicProfileRepository =
        FirestorePublicProfileRepository(firestore, auth),
) : WishlistRepository {

  /**
   * Adds or updates [item] in the signed-in user's wishlist, keyed idempotently by
   * [WishlistItem.monumentKey].
   *
   * @param item The wishlist reference entry to write.
   * @return The stored [WishlistItem].
   * @throws WishlistRepositoryException.UnauthenticatedUser if no valid signed-in user exists.
   * @throws WishlistRepositoryException.InvalidMonumentKey if [WishlistItem.monumentKey] is
   *   invalid.
   * @throws WishlistRepositoryException.InvalidUid if [WishlistItem.sourceOwnerUid] is invalid.
   * @throws WishlistRepositoryException.InvalidGuideId if [WishlistItem.sourceGuideId] is invalid.
   * @throws WishlistRepositoryException.InvalidMonumentName if [WishlistItem.monumentName] is
   *   invalid.
   * @throws IllegalArgumentException if [WishlistItem.addedAt] is negative.
   * @throws WishlistRepositoryException.FirestoreFailure if the Firestore write fails.
   */
  override suspend fun addToWishlist(item: WishlistItem): WishlistItem {
    val uid = requireCurrentUid()
    validateMonumentKey(item.monumentKey)
    validateUid(item.sourceOwnerUid)
    validateGuideId(item.sourceGuideId)
    validateMonumentName(item.monumentName)
    require(item.addedAt >= 0L) { "addedAt must be non-negative" }

    return withFirestoreErrors {
      wishlistCollection(uid).document(item.monumentKey).set(toFields(item)).await()
      item
    }
  }

  /**
   * Deletes the wishlist entry identified by [monumentKey] for the signed-in user.
   *
   * @param monumentKey The canonical monument key to remove.
   * @throws WishlistRepositoryException.UnauthenticatedUser if no valid signed-in user exists.
   * @throws WishlistRepositoryException.InvalidMonumentKey if [monumentKey] is invalid.
   * @throws WishlistRepositoryException.FirestoreFailure if the Firestore delete fails.
   */
  override suspend fun removeFromWishlist(monumentKey: String) {
    val uid = requireCurrentUid()
    validateMonumentKey(monumentKey)

    withFirestoreErrors { wishlistCollection(uid).document(monumentKey).delete().await() }
  }

  /**
   * Checks whether [monumentKey] exists in the signed-in user's wishlist.
   *
   * @param monumentKey The canonical monument key to check.
   * @return `true` if wishlisted, `false` otherwise.
   * @throws WishlistRepositoryException.UnauthenticatedUser if no valid signed-in user exists.
   * @throws WishlistRepositoryException.InvalidMonumentKey if [monumentKey] is invalid.
   * @throws WishlistRepositoryException.FirestoreFailure if the Firestore read fails.
   */
  override suspend fun isWishlisted(monumentKey: String): Boolean {
    val uid = requireCurrentUid()
    validateMonumentKey(monumentKey)

    return withFirestoreErrors {
      wishlistCollection(uid).document(monumentKey).get().await().exists()
    }
  }

  /**
   * Loads the signed-in user's wishlist sorted by descending [WishlistItem.addedAt] and ascending
   * [WishlistItem.monumentKey]. Malformed documents are skipped.
   *
   * @return The sorted list of valid [WishlistItem] entries.
   * @throws WishlistRepositoryException.UnauthenticatedUser if no valid signed-in user exists.
   * @throws WishlistRepositoryException.FirestoreFailure if the Firestore read fails.
   */
  override suspend fun getWishlist(): List<WishlistItem> {
    val uid = requireCurrentUid()
    return withFirestoreErrors {
      wishlistCollection(uid).get().await().documents.mapNotNull(::fromDocument).let(::sortWishlist)
    }
  }

  /**
   * Observes the signed-in user's wishlist in real time with offline cache metadata updates.
   *
   * @return A [Flow] emitting sorted [WishlistItem] lists whenever the wishlist changes.
   * @throws WishlistRepositoryException.UnauthenticatedUser if no valid signed-in user exists.
   */
  override fun observeWishlist(): Flow<List<WishlistItem>> {
    val uid = requireCurrentUid()
    return callbackFlow {
      val registration =
          wishlistCollection(uid).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
              close(WishlistRepositoryException.FirestoreFailure(error))
              return@addSnapshotListener
            }
            val items = snapshot?.documents.orEmpty().mapNotNull(::fromDocument).let(::sortWishlist)
            trySend(items)
          }
      awaitClose { registration.remove() }
    }
  }

  /**
   * Loads the signed-in user's wishlist and resolves each referenced guide in parallel via targeted
   * document reads. Individual revocation, deletion, or parse failures degrade to
   * [ResolvedWishlistItem.Unavailable].
   *
   * @return The list of resolved [ResolvedWishlistItem] entries in wishlist order.
   * @throws WishlistRepositoryException.UnauthenticatedUser if no valid signed-in user exists.
   * @throws WishlistRepositoryException.FirestoreFailure if loading the wishlist itself fails.
   */
  override suspend fun getResolvedWishlist(): List<ResolvedWishlistItem> {
    val items = getWishlist()
    if (items.isEmpty()) return emptyList()
    return coroutineScope { items.map { item -> async { resolveItem(item) } }.awaitAll() }
  }

  /**
   * Returns the public profiles of accepted friends who have wishlisted [monumentKey].
   *
   * @param monumentKey The canonical monument key to inspect.
   * @return Sorted list of [PublicProfile]s for accepted friends who wishlisted [monumentKey].
   * @throws WishlistRepositoryException.UnauthenticatedUser if no valid signed-in user exists.
   * @throws WishlistRepositoryException.InvalidMonumentKey if [monumentKey] is invalid.
   * @throws WishlistRepositoryException.FirestoreFailure if loading the friend list fails.
   */
  override suspend fun getFriendsWhoWishlisted(monumentKey: String): List<PublicProfile> {
    requireCurrentUid()
    validateMonumentKey(monumentKey)
    val friendUids = withFirestoreErrors {
      friendRepository.getFriends().map { it.friendUid }.toSet()
    }
    return getUsersWhoWishlisted(monumentKey, friendUids)
  }

  /**
   * Returns the public profiles of accessible users in [candidateUids] who have wishlisted
   * [monumentKey], excluding the signed-in user. Per-user read or profile lookup failures are
   * ignored so one inaccessible user never aborts aggregation.
   *
   * @param monumentKey The canonical monument key to inspect.
   * @param candidateUids The set of candidate user UIDs to check.
   * @return Sorted list of [PublicProfile]s for matching users.
   * @throws WishlistRepositoryException.UnauthenticatedUser if no valid signed-in user exists.
   * @throws WishlistRepositoryException.InvalidMonumentKey if [monumentKey] is invalid.
   * @throws WishlistRepositoryException.InvalidUid if any UID in [candidateUids] is invalid.
   */
  override suspend fun getUsersWhoWishlisted(
      monumentKey: String,
      candidateUids: Set<String>,
  ): List<PublicProfile> {
    val currentUid = requireCurrentUid()
    validateMonumentKey(monumentKey)
    candidateUids.forEach(::validateUid)
    val targetUids = candidateUids.filter { it != currentUid }
    if (targetUids.isEmpty()) return emptyList()

    return coroutineScope {
      val matchingUids =
          targetUids
              .map { targetUid ->
                async { if (hasWishlisted(targetUid, monumentKey)) targetUid else null }
              }
              .awaitAll()
              .filterNotNull()
      matchingUids
          .map { uid -> async { loadProfileOrNull(uid) } }
          .awaitAll()
          .filterNotNull()
          .sortedWith(compareBy<PublicProfile>({ it.username.lowercase(Locale.ROOT) }, { it.uid }))
    }
  }

  private suspend fun resolveItem(item: WishlistItem): ResolvedWishlistItem =
      try {
        val snapshot =
            firestore
                .collection(USERS_COLLECTION)
                .document(item.sourceOwnerUid)
                .collection(SAVED_GUIDES_COLLECTION)
                .document(item.sourceGuideId)
                .get()
                .await()
        val data = snapshot.data
        if (!snapshot.exists() || data == null) {
          ResolvedWishlistItem.Unavailable(item, WishlistUnavailableReason.GUIDE_DELETED)
        } else {
          ResolvedWishlistItem.Available(
              item,
              GuideFirestoreMapper.fromFields(snapshot.id, data),
          )
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: FirebaseFirestoreException) {
        val reason =
            when (exception.code) {
              FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                  WishlistUnavailableReason.ACCESS_REVOKED
              FirebaseFirestoreException.Code.NOT_FOUND -> WishlistUnavailableReason.GUIDE_DELETED
              else -> WishlistUnavailableReason.UNKNOWN
            }
        ResolvedWishlistItem.Unavailable(item, reason)
      } catch (_: Exception) {
        ResolvedWishlistItem.Unavailable(item, WishlistUnavailableReason.UNKNOWN)
      }

  private suspend fun hasWishlisted(targetUid: String, monumentKey: String): Boolean =
      try {
        wishlistCollection(targetUid).document(monumentKey).get().await().exists()
      } catch (exception: CancellationException) {
        throw exception
      } catch (_: Exception) {
        false
      }

  private suspend fun loadProfileOrNull(uid: String): PublicProfile? =
      try {
        publicProfileRepository.getProfile(uid)
      } catch (exception: CancellationException) {
        throw exception
      } catch (_: Exception) {
        null
      }

  private fun wishlistCollection(uid: String) =
      firestore.collection(USERS_COLLECTION).document(uid).collection(WISHLIST_COLLECTION)

  private fun requireCurrentUid(): String =
      auth.currentUser?.uid?.takeIf(UID_REGEX::matches)
          ?: throw WishlistRepositoryException.UnauthenticatedUser()

  private fun validateUid(uid: String) {
    if (!UID_REGEX.matches(uid)) throw WishlistRepositoryException.InvalidUid()
  }

  private fun validateMonumentKey(key: String) {
    if (!WishlistMonumentKey.isValidKey(key)) {
      throw WishlistRepositoryException.InvalidMonumentKey()
    }
  }

  private fun validateGuideId(guideId: String) {
    if (guideId.isBlank() || guideId.length > MAX_GUIDE_ID_LENGTH) {
      throw WishlistRepositoryException.InvalidGuideId()
    }
  }

  private fun validateMonumentName(name: String) {
    if (name.isBlank() || name.length > MAX_MONUMENT_NAME_LENGTH) {
      throw WishlistRepositoryException.InvalidMonumentName()
    }
  }

  private fun toFields(item: WishlistItem): Map<String, Any> = buildMap {
    put(MONUMENT_KEY_FIELD, item.monumentKey)
    put(SOURCE_OWNER_UID_FIELD, item.sourceOwnerUid)
    put(SOURCE_GUIDE_ID_FIELD, item.sourceGuideId)
    put(MONUMENT_NAME_FIELD, item.monumentName)
    put(ADDED_AT_FIELD, item.addedAt)
    item.location?.let {
      put(LOCATION_FIELD, mapOf(LATITUDE_FIELD to it.latitude, LONGITUDE_FIELD to it.longitude))
    }
  }

  private fun fromDocument(snapshot: DocumentSnapshot): WishlistItem? {
    val data = snapshot.data ?: return null
    val monumentKey =
        (data[MONUMENT_KEY_FIELD] as? String ?: snapshot.id).takeIf(WishlistMonumentKey::isValidKey)
            ?: return null
    val sourceOwnerUid =
        (data[SOURCE_OWNER_UID_FIELD] as? String)?.takeIf(UID_REGEX::matches) ?: return null
    val sourceGuideId =
        (data[SOURCE_GUIDE_ID_FIELD] as? String)?.takeIf {
          it.isNotBlank() && it.length <= MAX_GUIDE_ID_LENGTH
        } ?: return null
    val monumentName =
        (data[MONUMENT_NAME_FIELD] as? String)?.takeIf {
          it.isNotBlank() && it.length <= MAX_MONUMENT_NAME_LENGTH
        } ?: return null
    val addedAt = (data[ADDED_AT_FIELD] as? Number)?.toLong()?.takeIf { it >= 0L } ?: return null
    val location =
        if (LOCATION_FIELD in data) {
          parseLocation(data[LOCATION_FIELD]) ?: return null
        } else {
          null
        }
    return WishlistItem(
        monumentKey = monumentKey,
        sourceOwnerUid = sourceOwnerUid,
        sourceGuideId = sourceGuideId,
        monumentName = monumentName,
        addedAt = addedAt,
        location = location,
    )
  }

  private fun parseLocation(raw: Any?): Coordinates? {
    val map = raw as? Map<*, *> ?: return null
    val latitude = (map[LATITUDE_FIELD] as? Number)?.toDouble() ?: return null
    val longitude = (map[LONGITUDE_FIELD] as? Number)?.toDouble() ?: return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    return Coordinates(latitude, longitude)
  }

  private fun sortWishlist(items: List<WishlistItem>): List<WishlistItem> =
      items.sortedWith(compareByDescending<WishlistItem> { it.addedAt }.thenBy { it.monumentKey })

  private suspend fun <T> withFirestoreErrors(operation: suspend () -> T): T =
      try {
        operation()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: WishlistRepositoryException) {
        throw exception
      } catch (_: FriendRepositoryException.UnauthenticatedUser) {
        throw WishlistRepositoryException.UnauthenticatedUser()
      } catch (_: PublicProfileException.UnauthenticatedUser) {
        throw WishlistRepositoryException.UnauthenticatedUser()
      } catch (exception: Exception) {
        throw WishlistRepositoryException.FirestoreFailure(exception)
      }

  private companion object {
    const val USERS_COLLECTION = "users"
    const val WISHLIST_COLLECTION = "wishlist"
    const val SAVED_GUIDES_COLLECTION = "savedGuides"
    const val MONUMENT_KEY_FIELD = "monumentKey"
    const val SOURCE_OWNER_UID_FIELD = "sourceOwnerUid"
    const val SOURCE_GUIDE_ID_FIELD = "sourceGuideId"
    const val MONUMENT_NAME_FIELD = "monumentName"
    const val ADDED_AT_FIELD = "addedAt"
    const val LOCATION_FIELD = "location"
    const val LATITUDE_FIELD = "latitude"
    const val LONGITUDE_FIELD = "longitude"
    const val MAX_GUIDE_ID_LENGTH = 128
    const val MAX_MONUMENT_NAME_LENGTH = 200
    val UID_REGEX = Regex("^[A-Za-z0-9_-]{1,128}$")
  }
}
