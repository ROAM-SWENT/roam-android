// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.model.friend

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.CancellationException
import kotlinx.coroutines.tasks.await

/** Firestore-backed friend requests and friendships, with one document per UID pair. */
class FirestoreFriendRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : FriendRepository {

  override suspend fun sendFriendRequest(toUid: String): FriendRequest {
    val requesterUid = requireCurrentUid()
    validateUid(toUid)
    if (requesterUid == toUid) throw FriendRepositoryException.SelfFriendRequest()

    return withFirestoreErrors {
      val reference = friendshipReference(requesterUid, toUid)
      val recipientProfile = firestore.collection(PUBLIC_PROFILES_COLLECTION).document(toUid)
      firestore
          .runTransaction { transaction ->
            val existingSnapshot = transaction.get(reference)
            val recipientSnapshot = transaction.get(recipientProfile)
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
            if (!recipientSnapshot.exists()) {
              throw FriendRepositoryException.RecipientProfileNotFound()
            }

            val record = FriendRecord(reference.id, requesterUid, toUid, STATUS_PENDING)
            transaction.set(reference, record.toFields())
            record.toFriendRequest()
          }
          .await()
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

  override suspend fun rejectFriendRequest(requestId: String) {
    deletePendingRequest(requestId) { uid, request ->
      if (request.recipientUid != uid) throw FriendRepositoryException.NotRequestRecipient()
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

  override suspend fun removeFriend(friendUid: String) {
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

            transaction.delete(reference)
          }
          .await()
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

  private fun friendshipReference(requestId: String) =
      firestore.collection(FRIENDSHIPS_COLLECTION).document(validateRequestId(requestId))

  private fun friendshipId(uid1: String, uid2: String): String {
    validateUid(uid1)
    validateUid(uid2)
    if (uid1 == uid2) throw FriendRepositoryException.SelfFriendRequest()
    val (firstUid, secondUid) = listOf(uid1, uid2).sorted()
    return "$firstUid$PAIR_SEPARATOR$secondUid"
  }

  private fun validateRequestId(requestId: String): String {
    val separatorIndex = requestId.indexOf(PAIR_SEPARATOR)
    if (
        separatorIndex <= 0 ||
            separatorIndex != requestId.lastIndexOf(PAIR_SEPARATOR) ||
            friendshipId(
                requestId.substring(0, separatorIndex),
                requestId.substring(separatorIndex + 1),
            ) != requestId
    ) {
      throw FriendRepositoryException.RequestNotFound()
    }
    return requestId
  }

  private suspend fun <T> withFirestoreErrors(operation: suspend () -> T): T =
      try {
        operation()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: FriendRepositoryException) {
        throw exception
      } catch (exception: Exception) {
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
    const val PARTICIPANTS_FIELD = "participantUids"
    const val REQUESTER_UID_FIELD = "requesterUid"
    const val RECIPIENT_UID_FIELD = "recipientUid"
    const val STATUS_FIELD = "status"
    const val STATUS_PENDING = "PENDING"
    const val STATUS_ACCEPTED = "ACCEPTED"
    const val PAIR_SEPARATOR = "~"
    val UID_PATTERN = Regex("[A-Za-z0-9_-]{1,128}")
  }
}
