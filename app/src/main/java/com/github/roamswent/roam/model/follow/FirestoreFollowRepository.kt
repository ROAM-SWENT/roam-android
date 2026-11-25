// Co-authored-by: Gemini 4 Neon <noreply@google.com>

package com.github.roamswent.roam.model.follow

import android.util.Log
import com.github.roamswent.roam.model.firestore.RoamFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed implementation of [FollowRepository], storing one document per directed
 * `(ownerUid, followerUid)` pair.
 */
class FirestoreFollowRepository(
    private val firestore: FirebaseFirestore = RoamFirestore.instance,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : FollowRepository {

  override suspend fun follow(targetUid: String): Follow {
    val followerUid = requireCurrentUid()
    validateUid(targetUid)
    if (followerUid == targetUid) throw FollowRepositoryException.SelfFollow()

    return withFirestoreErrors {
      val followRef = followReference(targetUid, followerUid)
      val targetProfileRef = firestore.collection(PUBLIC_PROFILES_COLLECTION).document(targetUid)
      try {
        firestore
            .runTransaction { transaction ->
              val followSnapshot = transaction.get(followRef)
              val targetProfileSnapshot = transaction.get(targetProfileRef)
              val blockedSnapshot = transaction.get(blockedUserReference(followerUid, targetUid))
              if (followSnapshot.exists()) {
                return@runTransaction followSnapshot.toFollow()
              }
              if (blockedSnapshot.exists()) throw FollowRepositoryException.UserBlocked()
              if (!targetProfileSnapshot.exists()) {
                throw FollowRepositoryException.TargetProfileNotFound()
              }
              if (targetProfileSnapshot.getBoolean(IS_PUBLIC_FIELD) != true) {
                throw FollowRepositoryException.AccountNotPublic()
              }

              transaction.set(
                  followRef,
                  mapOf(
                      OWNER_UID_FIELD to targetUid,
                      FOLLOWER_UID_FIELD to followerUid,
                  ),
              )
              Follow(followRef.id, targetUid, followerUid)
            }
            .await()
      } catch (exception: FirebaseFirestoreException) {
        if (exception.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
          throw FollowRepositoryException.BlockedByUser()
        }
        throw exception
      }
    }
  }

  override suspend fun unfollow(targetUid: String) {
    val followerUid = requireCurrentUid()
    validateUid(targetUid)
    if (followerUid == targetUid) throw FollowRepositoryException.SelfFollow()

    withFirestoreErrors {
      val followRef = followReference(targetUid, followerUid)
      firestore
          .runTransaction { transaction ->
            val followSnapshot = transaction.get(followRef)
            if (!followSnapshot.exists()) throw FollowRepositoryException.FollowNotFound()
            followSnapshot.toFollow()
            transaction.delete(followRef)
          }
          .await()
    }
  }

  override suspend fun removeFollower(followerUid: String, blacklist: Boolean) {
    val ownerUid = requireCurrentUid()
    validateUid(followerUid)
    if (ownerUid == followerUid) throw FollowRepositoryException.SelfFollow()

    withFirestoreErrors {
      val followRef = followReference(ownerUid, followerUid)
      firestore
          .runTransaction { transaction ->
            val followSnapshot = transaction.get(followRef)
            if (!followSnapshot.exists()) throw FollowRepositoryException.FollowNotFound()
            followSnapshot.toFollow()

            if (!blacklist) {
              transaction.delete(followRef)
              return@runTransaction
            }

            val friendshipRef = friendshipReference(ownerUid, followerUid)
            val blockedRef = blockedUserReference(ownerUid, followerUid)
            val friendshipSnapshot = transaction.get(friendshipRef)
            val blockedSnapshot = transaction.get(blockedRef)

            transaction.delete(followRef)
            if (!blockedSnapshot.exists()) {
              transaction.set(blockedRef, mapOf(BLOCKED_UID_FIELD to followerUid))
            }
            if (friendshipSnapshot.exists()) {
              transaction.delete(friendshipRef)
            }
          }
          .await()
    }
  }

  override suspend fun getFollowing(): List<Follow> {
    val uid = requireCurrentUid()
    return withFirestoreErrors {
      firestore
          .collection(FOLLOWS_COLLECTION)
          .whereEqualTo(FOLLOWER_UID_FIELD, uid)
          .get()
          .await()
          .documents
          .map { it.toFollow() }
    }
  }

  override suspend fun getFollowers(): List<Follow> {
    val uid = requireCurrentUid()
    return withFirestoreErrors {
      firestore
          .collection(FOLLOWS_COLLECTION)
          .whereEqualTo(OWNER_UID_FIELD, uid)
          .get()
          .await()
          .documents
          .map { it.toFollow() }
    }
  }

  override suspend fun isFollowing(targetUid: String): Boolean {
    val followerUid = requireCurrentUid()
    validateUid(targetUid)
    if (followerUid == targetUid) return false

    return withFirestoreErrors { followReference(targetUid, followerUid).get().await().exists() }
  }

  private fun requireCurrentUid(): String =
      auth.currentUser?.uid?.also(::validateUid)
          ?: throw FollowRepositoryException.UnauthenticatedUser()

  private fun validateUid(uid: String) {
    if (!UID_PATTERN.matches(uid)) throw FollowRepositoryException.InvalidUid()
  }

  private fun followReference(ownerUid: String, followerUid: String): DocumentReference =
      firestore.collection(FOLLOWS_COLLECTION).document("$ownerUid$PAIR_SEPARATOR$followerUid")

  private fun friendshipReference(uid1: String, uid2: String): DocumentReference {
    val (firstUid, secondUid) = listOf(uid1, uid2).sorted()
    return firestore
        .collection(FRIENDSHIPS_COLLECTION)
        .document("$firstUid$PAIR_SEPARATOR$secondUid")
  }

  private fun blockedUserReference(blockerUid: String, blockedUid: String): DocumentReference =
      firestore
          .collection(USERS_COLLECTION)
          .document(blockerUid)
          .collection(BLOCKED_USERS_SUBCOLLECTION)
          .document(blockedUid)

  private suspend fun <T> withFirestoreErrors(operation: suspend () -> T): T =
      try {
        operation()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: FollowRepositoryException) {
        throw exception
      } catch (exception: Exception) {
        Log.e(TAG, "Firestore follow operation failed.", exception)
        throw FollowRepositoryException.FirestoreFailure(exception)
      }

  private fun DocumentSnapshot.toFollow(): Follow {
    val ownerUid =
        checkNotNull(getString(OWNER_UID_FIELD)) { "Follow record is missing its owner UID." }
    val followerUid =
        checkNotNull(getString(FOLLOWER_UID_FIELD)) { "Follow record is missing its follower UID." }
    check(
        UID_PATTERN.matches(ownerUid) &&
            UID_PATTERN.matches(followerUid) &&
            ownerUid != followerUid &&
            id == "$ownerUid$PAIR_SEPARATOR$followerUid",
    ) {
      "Follow record is malformed."
    }
    return Follow(id, ownerUid, followerUid)
  }

  private companion object {
    const val FOLLOWS_COLLECTION = "follows"
    const val PUBLIC_PROFILES_COLLECTION = "publicProfiles"
    const val FRIENDSHIPS_COLLECTION = "friendships"
    const val USERS_COLLECTION = "users"
    const val BLOCKED_USERS_SUBCOLLECTION = "blockedUsers"
    const val OWNER_UID_FIELD = "ownerUid"
    const val FOLLOWER_UID_FIELD = "followerUid"
    const val IS_PUBLIC_FIELD = "isPublic"
    const val BLOCKED_UID_FIELD = "blockedUid"
    const val PAIR_SEPARATOR = "~"
    const val TAG = "FirestoreFollowRepository"
    val UID_PATTERN = Regex("[A-Za-z0-9_-]{1,128}")
  }
}
