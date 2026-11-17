// Co-authored-by: Gemini 3.8 Flash <noreply@google.com>

package com.github.roamswent.roam.model.guide

import com.github.roamswent.roam.model.firestore.RoamFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

  override suspend fun getSharedGuidesFrom(ownerUid: String): List<Guide> {
    val currentUid = requireCurrentUid()
    validateUid(ownerUid)

    return withFirestoreErrors {
      val snapshot = savedGuidesCollection(ownerUid).get().await()
      snapshot.documents
          .map { document ->
            GuideFirestoreMapper.fromFields(
                id = document.id,
                fields = checkNotNull(document.data) { "Saved guide ${document.id} has no data" },
            )
          }
          .filter { guide ->
            currentUid !in guide.shareSettings.dismissedByUids &&
                guide.shareSettings.audience != ShareAudience.NONE
          }
    }
  }

  override fun observeSharedGuidesFrom(ownerUid: String): Flow<List<Guide>> {
    val currentUid = requireCurrentUid()
    validateUid(ownerUid)

    return callbackFlow {
      val registration =
          savedGuidesCollection(ownerUid).addSnapshotListener(MetadataChanges.INCLUDE) {
              snapshot,
              error ->
            if (error != null) {
              close(MonumentSharingException.FirestoreFailure(error))
            } else {
              try {
                val guides =
                    checkNotNull(snapshot) { "Saved guides snapshot is missing" }
                        .documents
                        .map { document ->
                          GuideFirestoreMapper.fromFields(
                              id = document.id,
                              fields =
                                  checkNotNull(document.data) {
                                    "Saved guide ${document.id} has no data"
                                  },
                          )
                        }
                        .filter { guide ->
                          currentUid !in guide.shareSettings.dismissedByUids &&
                              guide.shareSettings.audience != ShareAudience.NONE
                        }
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
