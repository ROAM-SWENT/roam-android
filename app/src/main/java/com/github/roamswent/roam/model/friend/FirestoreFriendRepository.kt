// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Gemini 4 Neon <noreply@google.com>

package com.github.roamswent.roam.model.friend

import android.util.Log
import com.github.roamswent.roam.model.firestore.RoamFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.tasks.await

/** Firestore-backed friend requests and friendships, with one document per UID pair. */
class FirestoreFriendRepository(
    private val firestore: FirebaseFirestore = RoamFirestore.instance,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : FriendRepository {

  override suspend fun sendFriendRequest(toUid: String): FriendRequest {
    val requesterUid = requireCurrentUid()
    validateUid(toUid)
    if (requesterUid == toUid) throw FriendRepositoryException.SelfFriendRequest()

    return withFirestoreErrors {
      val reference = friendshipReference(requesterUid, toUid)
      val recipientProfile = firestore.collection(PUBLIC_PROFILES_COLLECTION).document(toUid)
      try {
        firestore
            .runTransaction { transaction ->
              val existingSnapshot = transaction.get(reference)
              val recipientSnapshot = transaction.get(recipientProfile)
              val blockedSnapshot = transaction.get(blockedUserReference(requesterUid, toUid))
              if (existingSnapshot.exists()) {
                val existing = existingSnapshot.toFriendRecord()
                when {
                  existing.status == STATUS_ACCEPTED ->
                      throw FriendRepositoryException.AlreadyFriends()
                  existing.requesterUid == requesterUid ->
                      return@runTransaction existing.toFriendRequest()
                  else -> throw FriendRepositoryException.IncomingRequestPending()
                }
              }
              if (blockedSnapshot.exists()) throw FriendRepositoryException.UserBlocked()
              if (!recipientSnapshot.exists()) {
                throw FriendRepositoryException.RecipientProfileNotFound()
              }
              if (recipientSnapshot.getBoolean(ALLOW_FRIEND_REQUESTS_FIELD) == false) {
                throw FriendRepositoryException.FriendRequestsDisabled()
              }

              val record = FriendRecord(reference.id, requesterUid, toUid, STATUS_PENDING)
              transaction.set(reference, record.toFields())
              record.toFriendRequest()
            }
            .await()
      } catch (exception: FirebaseFirestoreException) {
        if (exception.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
          throw FriendRepositoryException.BlockedByUser()
        }
        throw exception
      }
    }
  }

  override suspend fun acceptFriendRequest(requestId: String) {
    val uid = requireCurrentUid()
    withFirestoreErrors {
      val reference = friendshipReference(requestId)
      firestore
          .runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            if (!snapshot.exists()) throw FriendRepositoryException.RequestNotFound()

            val request = snapshot.toFriendRecord()
            if (request.recipientUid != uid) {
              throw FriendRepositoryException.NotRequestRecipient()
            }
            if (request.status == STATUS_ACCEPTED) return@runTransaction
            if (request.status != STATUS_PENDING) {
              throw FriendRepositoryException.RequestNotPending()
            }

            transaction.update(reference, STATUS_FIELD, STATUS_ACCEPTED)
          }
          .await()
    }
  }

  override suspend fun rejectFriendRequest(requestId: String, blacklist: Boolean) {
    if (!blacklist) {
      deletePendingRequest(requestId) { uid, request ->
        if (request.recipientUid != uid) throw FriendRepositoryException.NotRequestRecipient()
      }
      return
    }

    val uid = requireCurrentUid()
    withFirestoreErrors {
      val reference = friendshipReference(requestId)
      firestore
          .runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            if (!snapshot.exists()) throw FriendRepositoryException.RequestNotFound()

            val request = snapshot.toFriendRecord()
            if (request.status != STATUS_PENDING) {
              throw FriendRepositoryException.RequestNotPending()
            }
            if (request.recipientUid != uid) {
              throw FriendRepositoryException.NotRequestRecipient()
            }

            val followReference = incomingFollowReference(uid, request.requesterUid)
            val blockedReference = blockedUserReference(uid, request.requesterUid)
            val followSnapshot = transaction.get(followReference)
            val blockedSnapshot = transaction.get(blockedReference)

            transaction.delete(reference)
            if (!blockedSnapshot.exists()) {
              transaction.set(
                  blockedReference,
                  mapOf(BLOCKED_UID_FIELD to request.requesterUid),
              )
            }
            if (followSnapshot.exists()) {
              transaction.delete(followReference)
            }
          }
          .await()
    }
  }

  override suspend fun cancelFriendRequest(requestId: String) {
    deletePendingRequest(requestId) { uid, request ->
      if (request.requesterUid != uid) throw FriendRepositoryException.NotRequestSender()
    }
  }

  override suspend fun getIncomingRequests(): List<FriendRequest> {
    val uid = requireCurrentUid()
    return withFirestoreErrors {
      getRelationships(uid)
          .filter { it.status == STATUS_PENDING && it.recipientUid == uid }
          .map(FriendRecord::toFriendRequest)
    }
  }

  override suspend fun getOutgoingRequests(): List<FriendRequest> {
    val uid = requireCurrentUid()
    return withFirestoreErrors {
      getRelationships(uid)
          .filter { it.status == STATUS_PENDING && it.requesterUid == uid }
          .map(FriendRecord::toFriendRequest)
    }
  }

  override suspend fun getFriends(): List<Friendship> {
    val uid = requireCurrentUid()
    return withFirestoreErrors {
      getRelationships(uid)
          .filter { it.status == STATUS_ACCEPTED }
          .map { record ->
            Friendship(
                id = record.id,
                friendUid =
                    if (record.requesterUid == uid) record.recipientUid else record.requesterUid,
            )
          }
    }
  }

  override suspend fun removeFriend(friendUid: String, blacklist: Boolean) {
    val uid = requireCurrentUid()
    validateUid(friendUid)
    if (uid == friendUid) throw FriendRepositoryException.SelfFriendRequest()

    withFirestoreErrors {
      val reference = friendshipReference(uid, friendUid)
      firestore
          .runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            if (!snapshot.exists()) throw FriendRepositoryException.FriendshipNotFound()

            val relationship = snapshot.toFriendRecord()
            if (relationship.status != STATUS_ACCEPTED) {
              throw FriendRepositoryException.FriendshipNotFound()
            }
            if (uid !in relationship.participantUids) {
              throw FriendRepositoryException.FriendshipNotFound()
            }

            if (!blacklist) {
              transaction.delete(reference)
              return@runTransaction
            }

            val followReference = incomingFollowReference(uid, friendUid)
            val blockedReference = blockedUserReference(uid, friendUid)
            val followSnapshot = transaction.get(followReference)
            val blockedSnapshot = transaction.get(blockedReference)

            transaction.delete(reference)
            if (!blockedSnapshot.exists()) {
              transaction.set(blockedReference, mapOf(BLOCKED_UID_FIELD to friendUid))
            }
            if (followSnapshot.exists()) {
              transaction.delete(followReference)
            }
          }
          .await()
    }
  }

  override suspend fun blockUser(blockedUid: String) {
    val uid = requireCurrentUid()
    validateUid(blockedUid)
    if (uid == blockedUid) throw FriendRepositoryException.SelfFriendRequest()

    withFirestoreErrors {
      val friendshipRef = friendshipReference(uid, blockedUid)
      val followRef = incomingFollowReference(uid, blockedUid)
      val blockedRef = blockedUserReference(uid, blockedUid)
      firestore
          .runTransaction { transaction ->
            val friendshipSnapshot = transaction.get(friendshipRef)
            val followSnapshot = transaction.get(followRef)
            val blockedSnapshot = transaction.get(blockedRef)

            if (!blockedSnapshot.exists()) {
              transaction.set(blockedRef, mapOf(BLOCKED_UID_FIELD to blockedUid))
            }
            if (friendshipSnapshot.exists()) {
              transaction.delete(friendshipRef)
            }
            if (followSnapshot.exists()) {
              transaction.delete(followRef)
            }
          }
          .await()
    }
  }

  override suspend fun unblockUser(blockedUid: String) {
    val uid = requireCurrentUid()
    validateUid(blockedUid)
    if (uid == blockedUid) throw FriendRepositoryException.SelfFriendRequest()

    withFirestoreErrors { blockedUserReference(uid, blockedUid).delete().await() }
  }

  override suspend fun getBlockedUids(): Set<String> {
    val uid = requireCurrentUid()
    return withFirestoreErrors {
      firestore
          .collection(USERS_COLLECTION)
          .document(uid)
          .collection(BLOCKED_USERS_SUBCOLLECTION)
          .get()
          .await()
          .documents
          .mapTo(mutableSetOf()) { it.id }
    }
  }

  private suspend fun deletePendingRequest(
      requestId: String,
      authorize: (uid: String, request: FriendRecord) -> Unit,
  ) {
    val uid = requireCurrentUid()
    withFirestoreErrors {
      val reference = friendshipReference(requestId)
      firestore
          .runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            if (!snapshot.exists()) throw FriendRepositoryException.RequestNotFound()

            val request = snapshot.toFriendRecord()
            if (request.status != STATUS_PENDING) {
              throw FriendRepositoryException.RequestNotPending()
            }
            authorize(uid, request)
            transaction.delete(reference)
          }
          .await()
    }
  }

  private suspend fun getRelationships(uid: String): List<FriendRecord> =
      firestore
          .collection(FRIENDSHIPS_COLLECTION)
          .whereArrayContains(PARTICIPANTS_FIELD, uid)
          .get()
          .await()
          .documents
          .map { it.toFriendRecord() }

  private fun requireCurrentUid(): String =
      auth.currentUser?.uid?.also(::validateUid)
          ?: throw FriendRepositoryException.UnauthenticatedUser()

  private fun validateUid(uid: String) {
    if (!UID_PATTERN.matches(uid)) throw FriendRepositoryException.InvalidUid()
  }

  private fun friendshipReference(uid1: String, uid2: String) =
      firestore.collection(FRIENDSHIPS_COLLECTION).document(friendshipId(uid1, uid2))

  private fun blockedUserReference(blockerUid: String, blockedUid: String): DocumentReference =
      firestore
          .collection(USERS_COLLECTION)
          .document(blockerUid)
          .collection(BLOCKED_USERS_SUBCOLLECTION)
          .document(blockedUid)

  private fun incomingFollowReference(ownerUid: String, followerUid: String): DocumentReference =
      firestore.collection(FOLLOWS_COLLECTION).document("$ownerUid$PAIR_SEPARATOR$followerUid")

  private fun friendshipReference(requestId: String): DocumentReference {
    val uids = requestId.split(PAIR_SEPARATOR)

    if (uids.size != 2 || friendshipId(uids[0], uids[1]) != requestId) {
      throw FriendRepositoryException.RequestNotFound()
    }

    return firestore.collection(FRIENDSHIPS_COLLECTION).document(requestId)
  }

  private fun friendshipId(uid1: String, uid2: String): String {
    validateUid(uid1)
    validateUid(uid2)
    if (uid1 == uid2) throw FriendRepositoryException.SelfFriendRequest()
    val (firstUid, secondUid) = listOf(uid1, uid2).sorted()
    return "$firstUid$PAIR_SEPARATOR$secondUid"
  }

  private suspend fun <T> withFirestoreErrors(operation: suspend () -> T): T =
      try {
        operation()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: FriendRepositoryException) {
        throw exception
      } catch (exception: Exception) {
        Log.e(TAG, "Firestore friend operation failed.", exception)
        throw FriendRepositoryException.FirestoreFailure(exception)
      }

  private fun DocumentSnapshot.toFriendRecord(): FriendRecord {
    val requesterUid =
        checkNotNull(getString(REQUESTER_UID_FIELD)) {
          "Friend record is missing its requester UID."
        }
    val recipientUid =
        checkNotNull(getString(RECIPIENT_UID_FIELD)) {
          "Friend record is missing its recipient UID."
        }
    val participants =
        (data?.get(PARTICIPANTS_FIELD) as? List<*>)?.map {
          it as? String ?: error("Invalid participant UID.")
        } ?: error("Friend record is missing participant UIDs.")
    val status = checkNotNull(getString(STATUS_FIELD)) { "Friend record is missing its status." }
    check(
        participants.size == 2 &&
            participants == participants.sorted() &&
            participants.all(UID_PATTERN::matches) &&
            requesterUid != recipientUid &&
            requesterUid in participants &&
            recipientUid in participants &&
            id == friendshipId(requesterUid, recipientUid) &&
            status in setOf(STATUS_PENDING, STATUS_ACCEPTED),
    ) {
      "Friend record is malformed."
    }
    return FriendRecord(id, requesterUid, recipientUid, status, participants)
  }

  private data class FriendRecord(
      val id: String,
      val requesterUid: String,
      val recipientUid: String,
      val status: String,
      val participantUids: List<String> = listOf(requesterUid, recipientUid).sorted(),
  ) {
    fun toFriendRequest() = FriendRequest(id, requesterUid, recipientUid)

    fun toFields(): Map<String, Any> =
        mapOf(
            PARTICIPANTS_FIELD to participantUids,
            REQUESTER_UID_FIELD to requesterUid,
            RECIPIENT_UID_FIELD to recipientUid,
            STATUS_FIELD to status,
        )
  }

  private companion object {
    const val PUBLIC_PROFILES_COLLECTION = "publicProfiles"
    const val FRIENDSHIPS_COLLECTION = "friendships"
    const val USERS_COLLECTION = "users"
    const val BLOCKED_USERS_SUBCOLLECTION = "blockedUsers"
    const val FOLLOWS_COLLECTION = "follows"
    const val ALLOW_FRIEND_REQUESTS_FIELD = "allowFriendRequests"
    const val BLOCKED_UID_FIELD = "blockedUid"
    const val PARTICIPANTS_FIELD = "participantUids"
    const val REQUESTER_UID_FIELD = "requesterUid"
    const val RECIPIENT_UID_FIELD = "recipientUid"
    const val STATUS_FIELD = "status"
    const val STATUS_PENDING = "PENDING"
    const val STATUS_ACCEPTED = "ACCEPTED"
    const val PAIR_SEPARATOR = "~"
    const val TAG = "FirestoreFriendRepository"
    val UID_PATTERN = Regex("[A-Za-z0-9_-]{1,128}")
  }
}
