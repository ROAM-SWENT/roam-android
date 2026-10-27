// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.model.guide

import com.github.roamswent.roam.model.location.Coordinates
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Firestore-backed saved guides with an unlimited persistent local cache. */
class FirestoreSavedGuideRepository(
    private val firestore: FirebaseFirestore = savedGuidesFirestore(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : SavedGuideRepository {

  override suspend fun addGuide(guide: Guide): Guide {
    val collection = savedGuidesCollection()
    val document = if (guide.id.isBlank()) collection.document() else collection.document(guide.id)
    val savedGuide = guide.copy(id = document.id)
    GuideDocumentSize.requireWithinLimit(savedGuide)
    document.set(GuideFirestoreMapper.toFields(savedGuide)).await()
    return savedGuide
  }

  override suspend fun removeGuide(guideId: String) {
    savedGuidesCollection().document(guideId).delete().await()
  }

  override suspend fun getSavedGuides(): List<Guide> =
      savedGuidesCollection().get().await().documents.map { document ->
        GuideFirestoreMapper.fromFields(
            id = document.id,
            fields = checkNotNull(document.data) { "Saved guide ${document.id} has no data" },
        )
      }

  override fun observeSavedGuides(): Flow<List<Guide>> = callbackFlow {
    val registration =
        savedGuidesCollection().addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
          if (error != null) {
            close(error)
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
              trySend(guides)
            } catch (exception: Exception) {
              close(exception)
            }
          }
        }
    awaitClose { registration.remove() }
  }

  override suspend fun getGuide(guideId: String): Guide? {
    val document = savedGuidesCollection().document(guideId).get().await()
    if (!document.exists()) return null
    return GuideFirestoreMapper.fromFields(
        id = document.id,
        fields = checkNotNull(document.data) { "Saved guide ${document.id} has no data" },
    )
  }

  private fun savedGuidesCollection() =
      firestore
          .collection(USERS_COLLECTION)
          .document(
              checkNotNull(auth.currentUser) {
                    "A signed-in user is required to access saved guides"
                  }
                  .uid
          )
          .collection(SAVED_GUIDES_COLLECTION)

  private companion object {
    const val USERS_COLLECTION = "users"
    const val SAVED_GUIDES_COLLECTION = "savedGuides"
  }
}

private val firestoreWithUnlimitedLocalCache: FirebaseFirestore by lazy {
  FirebaseFirestore.getInstance().apply {
    firestoreSettings =
        FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                PersistentCacheSettings.newBuilder()
                    .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build()
            )
            .build()
  }
}

private fun savedGuidesFirestore(): FirebaseFirestore = firestoreWithUnlimitedLocalCache

internal object GuideFirestoreMapper {
  fun toFields(guide: Guide): Map<String, Any> = buildMap {
    put("photo", Blob.fromBytes(guide.photoBytes))
    put("monumentName", guide.monumentName)
    put("confidence", guide.confidence)
    put("history", guide.history)
    put("facts", guide.facts)
    put("culture", guide.culture)
    guide.location?.let {
      put("location", mapOf("latitude" to it.latitude, "longitude" to it.longitude))
    }
    guide.discoveryDate?.let { put("discoveryDate", it) }
  }

  fun fromFields(id: String, fields: Map<String, Any>): Guide =
      Guide(
          id = id,
          photoBytes = required<Blob>(fields, "photo").toBytes(),
          monumentName = required(fields, "monumentName"),
          confidence = required(fields, "confidence"),
          history = required(fields, "history"),
          facts = requiredStringList(fields, "facts"),
          culture = required(fields, "culture"),
          location = optionalCoordinates(fields["location"]),
          discoveryDate = (fields["discoveryDate"] as? Number)?.toLong(),
      )

  private fun optionalCoordinates(value: Any?): Coordinates? {
    if (value == null) return null
    val coordinates = value as? Map<*, *> ?: error("Saved guide has an invalid 'location' field")
    val latitude = (coordinates["latitude"] as? Number)?.toDouble()
    val longitude = (coordinates["longitude"] as? Number)?.toDouble()
    if (latitude == null || longitude == null) {
      error("Saved guide has an invalid 'location' field")
    }
    return Coordinates(latitude, longitude)
  }

  private fun requiredStringList(fields: Map<String, Any>, key: String): List<String> =
      required<List<*>>(fields, key).mapIndexed { index, value ->
        value as? String ?: error("Saved guide has an invalid '$key' item at index $index")
      }

  private inline fun <reified T> required(fields: Map<String, Any>, key: String): T =
      checkNotNull(fields[key] as? T) { "Saved guide is missing a valid '$key' field" }
}
