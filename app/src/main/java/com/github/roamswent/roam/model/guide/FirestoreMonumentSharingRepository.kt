// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Gemini 3.8 Flash <noreply@google.com>

package com.github.roamswent.roam.model.guide

import com.github.roamswent.roam.model.firestore.RoamFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreMonumentSharingRepository(
    private val firestore: FirebaseFirestore = RoamFirestore.instance,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : MonumentSharingRepository {

  override suspend fun setShareAudience(
      guideId: String,
      audience: ShareAudience,
      recipientUids: Set<String>,
  ) {
    val currentUid = requireCurrentUid()
    validateGuideId(guideId)
    recipientUids.forEach(::validateUid)

    withFirestoreErrors {
      val guideRef = savedGuidesCollection(currentUid).document(guideId)
      firestore
          .runTransaction { transaction ->
            val snapshot = transaction.get(guideRef)
            if (!snapshot.exists()) throw MonumentSharingException.GuideNotFound()

            val updates =
                mutableMapOf<String, Any>(
                    GuideFirestoreMapper.SHARE_AUDIENCE_FIELD to audience.name,
                )

            if (audience == ShareAudience.SELECTED_FRIENDS) {
              updates[GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD] = recipientUids.sorted()
              if (recipientUids.isNotEmpty()) {
                updates[GuideFirestoreMapper.DISMISSED_BY_UIDS_FIELD] =
                    FieldValue.arrayRemove(*recipientUids.toTypedArray())
              }
            } else {
              updates[GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD] = FieldValue.delete()
            }

            if (audience == ShareAudience.ALL_FRIENDS || audience == ShareAudience.ALL_FOLLOWERS) {
              updates[GuideFirestoreMapper.DISMISSED_BY_UIDS_FIELD] = FieldValue.delete()
            }

            transaction.update(guideRef, updates)
          }
          .await()
    }
  }

  override suspend fun grantAccess(guideId: String, recipientUids: Set<String>) {
    val currentUid = requireCurrentUid()
    validateGuideId(guideId)
    recipientUids.forEach(::validateUid)
    if (recipientUids.isEmpty()) return

    withFirestoreErrors {
      val guideRef = savedGuidesCollection(currentUid).document(guideId)
      firestore
          .runTransaction { transaction ->
            val snapshot = transaction.get(guideRef)
            if (!snapshot.exists()) throw MonumentSharingException.GuideNotFound()

            val rawAudience = snapshot.getString(GuideFirestoreMapper.SHARE_AUDIENCE_FIELD)
            val updates = mutableMapOf<String, Any>()
            if (rawAudience == ShareAudience.NONE.name) {
              updates[GuideFirestoreMapper.SHARE_AUDIENCE_FIELD] =
                  ShareAudience.SELECTED_FRIENDS.name
            }
            updates[GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD] =
                FieldValue.arrayUnion(*recipientUids.toTypedArray())
            updates[GuideFirestoreMapper.DISMISSED_BY_UIDS_FIELD] =
                FieldValue.arrayRemove(*recipientUids.toTypedArray())

            transaction.update(guideRef, updates)
          }
          .await()
    }
  }

  override suspend fun revokeAccess(guideId: String, recipientUids: Set<String>) {
    val currentUid = requireCurrentUid()
    validateGuideId(guideId)
    recipientUids.forEach(::validateUid)
    if (recipientUids.isEmpty()) return

    withFirestoreErrors {
      val guideRef = savedGuidesCollection(currentUid).document(guideId)
      firestore
          .runTransaction { transaction ->
            val snapshot = transaction.get(guideRef)
            if (!snapshot.exists()) throw MonumentSharingException.GuideNotFound()

            transaction.update(
                guideRef,
                GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD,
                FieldValue.arrayRemove(*recipientUids.toTypedArray()),
            )
          }
          .await()
    }
  }

  override suspend fun dismissSharedGuide(ownerUid: String, guideId: String) {
    val currentUid = requireCurrentUid()
    validateUid(ownerUid)
    validateGuideId(guideId)

    withFirestoreErrors {
      val guideRef = savedGuidesCollection(ownerUid).document(guideId)
      guideRef
          .update(
              mapOf(
                  GuideFirestoreMapper.DISMISSED_BY_UIDS_FIELD to FieldValue.arrayUnion(currentUid),
                  GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD to FieldValue.arrayRemove(currentUid),
              ),
          )
          .await()
    }
  }

  override suspend fun getShareSettings(guideId: String): MonumentShareSettings {
    val currentUid = requireCurrentUid()
    validateGuideId(guideId)

    return withFirestoreErrors {
      val guideRef = savedGuidesCollection(currentUid).document(guideId)
      val snapshot = guideRef.get().await()
      if (!snapshot.exists()) throw MonumentSharingException.GuideNotFound()
      val data = snapshot.data ?: throw MonumentSharingException.GuideNotFound()
      GuideFirestoreMapper.fromFields(snapshot.id, data).shareSettings
    }
  }

  override suspend fun getSharedGuideFrom(ownerUid: String, guideId: String): Guide? {
    val currentUid = requireCurrentUid()
    validateUid(ownerUid)
    validateGuideId(guideId)

    return withFirestoreErrors {
      val snapshot =
          try {
            savedGuidesCollection(ownerUid).document(guideId).get().await()
          } catch (exception: FirebaseFirestoreException) {
            // Rules cannot distinguish a deleted guide from one whose access was revoked.
            if (
                ownerUid != currentUid &&
                    exception.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
            ) {
              return@withFirestoreErrors null
            }
            throw exception
          }
      if (!snapshot.exists()) return@withFirestoreErrors null
      val guide =
          GuideFirestoreMapper.fromFields(
              snapshot.id,
              checkNotNull(snapshot.data) { "Saved guide ${snapshot.id} has no data" },
          )
      if (
          filterEligibleGuides(listOf(guide), currentUid).isEmpty() ||
              (ownerUid != currentUid &&
                  guide.shareSettings.audience == ShareAudience.SELECTED_FRIENDS &&
                  currentUid !in guide.shareSettings.sharedWithUids)
      )
          null
      else guide
    }
  }

  override suspend fun getSharedGuidesFrom(ownerUid: String): List<Guide> {
    val currentUid = requireCurrentUid()
    validateUid(ownerUid)

    return withFirestoreErrors {
      val guides =
          if (currentUid == ownerUid) {
            getGuidesFromQuery(savedGuidesCollection(ownerUid))
          } else {
            val allFriendsQuery =
                savedGuidesCollection(ownerUid)
                    .whereEqualTo(
                        GuideFirestoreMapper.SHARE_AUDIENCE_FIELD,
                        ShareAudience.ALL_FRIENDS.name,
                    )
            val allFollowersQuery =
                savedGuidesCollection(ownerUid)
                    .whereEqualTo(
                        GuideFirestoreMapper.SHARE_AUDIENCE_FIELD,
                        ShareAudience.ALL_FOLLOWERS.name,
                    )
            val selectedQuery =
                savedGuidesCollection(ownerUid)
                    .whereEqualTo(
                        GuideFirestoreMapper.SHARE_AUDIENCE_FIELD,
                        ShareAudience.SELECTED_FRIENDS.name,
                    )
                    .whereArrayContains(GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD, currentUid)

            coroutineScope {
              val allFriendsDeferred = async { getGuidesAllowingPermissionDenied(allFriendsQuery) }
              val allFollowersDeferred = async {
                getGuidesAllowingPermissionDenied(allFollowersQuery)
              }
              val selectedDeferred = async { getGuidesAllowingPermissionDenied(selectedQuery) }

              allFriendsDeferred.await() + allFollowersDeferred.await() + selectedDeferred.await()
            }
          }

      filterEligibleGuides(guides, currentUid)
    }
  }

  override fun observeSharedGuidesFrom(ownerUid: String): Flow<List<Guide>> {
    val currentUid = requireCurrentUid()
    validateUid(ownerUid)

    return if (currentUid == ownerUid) {
      observeQuery(savedGuidesCollection(ownerUid)).map { guides ->
        filterEligibleGuides(guides, currentUid)
      }
    } else {
      val allFriendsQuery =
          savedGuidesCollection(ownerUid)
              .whereEqualTo(
                  GuideFirestoreMapper.SHARE_AUDIENCE_FIELD,
                  ShareAudience.ALL_FRIENDS.name,
              )
      val allFollowersQuery =
          savedGuidesCollection(ownerUid)
              .whereEqualTo(
                  GuideFirestoreMapper.SHARE_AUDIENCE_FIELD,
                  ShareAudience.ALL_FOLLOWERS.name,
              )
      val selectedQuery =
          savedGuidesCollection(ownerUid)
              .whereEqualTo(
                  GuideFirestoreMapper.SHARE_AUDIENCE_FIELD,
                  ShareAudience.SELECTED_FRIENDS.name,
              )
              .whereArrayContains(GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD, currentUid)

      combine(
          observeQueryAllowingPermissionDenied(allFriendsQuery),
          observeQueryAllowingPermissionDenied(allFollowersQuery),
          observeQueryAllowingPermissionDenied(selectedQuery),
      ) { allFriendsGuides, allFollowersGuides, selectedGuides ->
        val results = listOf(allFriendsGuides, allFollowersGuides, selectedGuides)
        if (results.all { it.isFailure }) throw checkNotNull(results.first().exceptionOrNull())
        filterEligibleGuides(
            results.flatMap { it.getOrDefault(emptyList()) },
            currentUid,
        )
      }
    }
  }

  private suspend fun getGuidesFromQuery(query: Query): List<Guide> =
      parseGuidesFromSnapshot(query.get().await())

  private suspend fun getGuidesAllowingPermissionDenied(query: Query): List<Guide> =
      try {
        getGuidesFromQuery(query)
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        if (
            (exception as? FirebaseFirestoreException)?.code ==
                FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                (exception.cause as? FirebaseFirestoreException)?.code ==
                    FirebaseFirestoreException.Code.PERMISSION_DENIED
        ) {
          emptyList()
        } else if (exception is MonumentSharingException) {
          throw exception
        } else {
          throw MonumentSharingException.FirestoreFailure(exception)
        }
      }

  private fun parseGuidesFromSnapshot(snapshot: QuerySnapshot): List<Guide> =
      snapshot.documents.map { document ->
        GuideFirestoreMapper.fromFields(
            id = document.id,
            fields = checkNotNull(document.data) { "Saved guide ${document.id} has no data" },
        )
      }

  private fun filterEligibleGuides(guides: List<Guide>, currentUid: String): List<Guide> =
      guides
          .distinctBy { it.id }
          .filter { guide ->
            currentUid !in guide.shareSettings.dismissedByUids &&
                guide.shareSettings.audience != ShareAudience.NONE
          }

  private fun observeQueryAllowingPermissionDenied(query: Query): Flow<Result<List<Guide>>> =
      observeQuery(query)
          .map { Result.success(it) }
          .catch { exception ->
            val firestoreException =
                generateSequence(exception) { it.cause?.takeUnless { cause -> cause === it } }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
            if (firestoreException?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
              emit(Result.failure(MonumentSharingException.FirestoreFailure(firestoreException)))
            } else {
              throw exception
            }
          }

  private fun observeQuery(query: Query): Flow<List<Guide>> = callbackFlow {
    val registration =
        query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
          if (error != null) {
            close(MonumentSharingException.FirestoreFailure(error))
          } else {
            try {
              val guides =
                  parseGuidesFromSnapshot(
                      checkNotNull(snapshot) { "Saved guides snapshot is missing" },
                  )
              trySend(guides)
            } catch (exception: Exception) {
              close(
                  if (exception is MonumentSharingException) exception
                  else MonumentSharingException.FirestoreFailure(exception),
              )
            }
          }
        }
    awaitClose { registration.remove() }
  }

  private fun requireCurrentUid(): String =
      auth.currentUser?.uid?.also(::validateUid)
          ?: throw MonumentSharingException.UnauthenticatedUser()

  private fun validateUid(uid: String) {
    if (!UID_PATTERN.matches(uid)) throw MonumentSharingException.InvalidUid()
  }

  private fun validateGuideId(guideId: String) {
    require(guideId.isNotBlank()) { "Guide ID cannot be blank." }
  }

  private fun savedGuidesCollection(uid: String) =
      firestore.collection(USERS_COLLECTION).document(uid).collection(SAVED_GUIDES_COLLECTION)

  private suspend fun <T> withFirestoreErrors(operation: suspend () -> T): T =
      try {
        operation()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: MonumentSharingException) {
        throw exception
      } catch (exception: Exception) {
        throw MonumentSharingException.FirestoreFailure(exception)
      }

  private companion object {
    const val USERS_COLLECTION = "users"
    const val SAVED_GUIDES_COLLECTION = "savedGuides"
    val UID_PATTERN = Regex("[A-Za-z0-9_-]{1,128}")
  }
}

class DefaultMonumentSharingProvider(
    private val override: MonumentSharingRepository? = null,
    private val firestore: FirebaseFirestore = RoamFirestore.instance,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : MonumentSharingProvider {
  override fun repository(): MonumentSharingRepository =
      override ?: FirestoreMonumentSharingRepository(firestore, auth)
}
