// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.model.wishlist

import com.github.roamswent.roam.model.firestore.RoamFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Reads the wishlists of other users (accepted friends) in real time. */
interface FriendWishlistRepository {
  /**
   * Observes the monument names in the wishlist of [friendUid]. Entries with a blank or invalid
   * name are skipped.
   *
   * @param friendUid UID of an accepted friend whose wishlist the signed-in user may list.
   * @return A [Flow] emitting the names on every change; it fails with a
   *   [WishlistRepositoryException] when the user is signed out, [friendUid] is invalid or the
   *   Firestore listener fails.
   */
  fun observeWishlistedMonumentNames(friendUid: String): Flow<List<String>>
}

/** Firestore implementation listening to `users/{friendUid}/wishlist`. */
class FirestoreFriendWishlistRepository(
    private val firestore: FirebaseFirestore = RoamFirestore.instance,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : FriendWishlistRepository {

  override fun observeWishlistedMonumentNames(friendUid: String): Flow<List<String>> {
    if (auth.currentUser?.uid?.let(UID_REGEX::matches) != true) {
      throw WishlistRepositoryException.UnauthenticatedUser()
    }
    if (!UID_REGEX.matches(friendUid)) throw WishlistRepositoryException.InvalidUid()
    return callbackFlow {
      val registration =
          firestore
              .collection("users")
              .document(friendUid)
              .collection("wishlist")
              .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                  close(WishlistRepositoryException.FirestoreFailure(error))
                  return@addSnapshotListener
                }
                val names =
                    snapshot
                        ?.documents
                        .orEmpty()
                        .mapNotNull { it.get("monumentName") as? String }
                        .filter { it.isNotBlank() && it.length <= MAX_MONUMENT_NAME_LENGTH }
                trySend(names)
              }
      awaitClose { registration.remove() }
    }
  }

  private companion object {
    const val MAX_MONUMENT_NAME_LENGTH = 200
    val UID_REGEX = Regex("^[A-Za-z0-9_-]{1,128}$")
  }
}
