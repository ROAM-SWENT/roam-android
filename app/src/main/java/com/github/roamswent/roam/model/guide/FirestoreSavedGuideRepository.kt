// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.model.guide

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** Firestore-backed saved guides. Firestore's default Android disk persistence remains enabled. */
class FirestoreSavedGuideRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : SavedGuideRepository {

  override suspend fun addGuide(guide: Guide): Guide {
    val collection = savedGuidesCollection()
    val document = if (guide.id.isBlank()) collection.document() else collection.document(guide.id)
    document.set(GuideFirestoreMapper.toFields(guide)).await()
    return guide.copy(id = document.id)
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

internal object GuideFirestoreMapper {
  fun toFields(guide: Guide): Map<String, Any> =
      mapOf(
          "photo" to Blob.fromBytes(guide.photoBytes),
          "monumentName" to guide.monumentName,
          "confidence" to guide.confidence,
          "history" to guide.history,
          "facts" to guide.facts,
          "culture" to guide.culture,
          "language" to guide.language,
      )

  fun fromFields(id: String, fields: Map<String, Any>): Guide =
      Guide(
          id = id,
          photoBytes = required<Blob>(fields, "photo").toBytes(),
          monumentName = required(fields, "monumentName"),
          confidence = required(fields, "confidence"),
          history = required(fields, "history"),
          facts = requiredStringList(fields, "facts"),
          culture = required(fields, "culture"),
          language = required(fields, "language"),
      )

  private fun requiredStringList(fields: Map<String, Any>, key: String): List<String> =
      required<List<*>>(fields, key).mapIndexed { index, value ->
        value as? String ?: error("Saved guide has an invalid '$key' item at index $index")
      }

  private inline fun <reified T> required(fields: Map<String, Any>, key: String): T =
      checkNotNull(fields[key] as? T) { "Saved guide is missing a valid '$key' field" }
}
