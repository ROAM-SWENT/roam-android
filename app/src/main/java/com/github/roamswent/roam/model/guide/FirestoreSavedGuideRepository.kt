// Co-authored-by: Codex <codex@openai.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Gemini 3.8 Flash <noreply@google.com>
package com.github.roamswent.roam.model.guide

import com.github.roamswent.roam.model.ai.GuideMilestone
import com.github.roamswent.roam.model.ai.GuideSection
import com.github.roamswent.roam.model.firestore.RoamFirestore
import com.github.roamswent.roam.model.location.Coordinates
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Firestore-backed saved guides with an unlimited persistent local cache. */
class FirestoreSavedGuideRepository(
    private val firestore: FirebaseFirestore = RoamFirestore.instance,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : SavedGuideRepository {

  override suspend fun addGuide(guide: Guide): Guide {
    val collection = savedGuidesCollection()
    val document = if (guide.id.isBlank()) collection.document() else collection.document(guide.id)
    val savedGuide = guide.copy(id = document.id)
    GuideDocumentSize.requireWithinLimit(savedGuide, document.path)
    document.set(GuideFirestoreMapper.toFields(savedGuide)).await()
    return savedGuide
  }

  override suspend fun removeGuide(guideId: String) {
    savedGuidesCollection().document(guideId).delete().await()
  }

  override suspend fun setFavorite(guideId: String, isFavorite: Boolean) {
    // update() writes only the flag, so the photo is not re-sent, and it fails instead of
    // recreating a guide that was deleted meanwhile.
    savedGuidesCollection()
        .document(guideId)
        .update(GuideFirestoreMapper.FAVORITE_FIELD, isFavorite)
        .await()
  }

  override suspend fun setLocation(guideId: String, location: Coordinates?) {
    // Like setFavorite, update() writes only this field and fails for a deleted guide. Clearing
    // deletes the field, matching guides that never had a location. update() queues the write
    // before anything suspends, and Firestore sends queued writes in order and drops rejected ones;
    // await() only waits for the acknowledgement, so this meets the ordering contract.
    savedGuidesCollection()
        .document(guideId)
        .update(
            GuideFirestoreMapper.LOCATION_FIELD,
            location?.let(GuideFirestoreMapper::locationFields) ?: FieldValue.delete(),
        )
        .await()
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

internal object GuideFirestoreMapper {
  const val FAVORITE_FIELD = "isFavorite"
  const val LOCATION_FIELD = "location"
  const val MONUMENT_LOCATION_FIELD = "monumentLocation"
  const val EXPLORE_LOCATION_FAILURE_FIELD = "exploreLocationFailure"
  const val EXPLORE_FAILURE_REASON_FIELD = "reason"
  const val EXPLORE_FAILURE_GALLERY_FIELD = "isGalleryImage"
  const val SHARE_AUDIENCE_FIELD = "shareAudience"
  const val SHARED_WITH_UIDS_FIELD = "sharedWithUids"
  const val DISMISSED_BY_UIDS_FIELD = "dismissedByUids"

  fun locationFields(location: Coordinates): Map<String, Double> =
      mapOf("latitude" to location.latitude, "longitude" to location.longitude)

  fun toFields(guide: Guide): Map<String, Any> = buildMap {
    put("photo", Blob.fromBytes(guide.photoBytes))
    put("monumentName", guide.monumentName)
    put("confidence", guide.confidence)
    put("history", guide.history)
    put("facts", guide.facts)
    put("culture", guide.culture)
    put("sections", guide.sections.map { mapOf("title" to it.title, "content" to it.content) })
    put("interestCount", guide.interestCount)
    guide.overview?.let { put("overview", it) }
    if (guide.timeline.isNotEmpty()) {
      put("timeline", guide.timeline.map { mapOf("year" to it.year, "event" to it.event) })
    }
    guide.location?.let { put(LOCATION_FIELD, locationFields(it)) }
    guide.monumentLocation?.let { put(MONUMENT_LOCATION_FIELD, locationFields(it)) }
    guide.exploreLocationFailure?.let { failure ->
      put(
          EXPLORE_LOCATION_FAILURE_FIELD,
          mapOf(
              EXPLORE_FAILURE_REASON_FIELD to failure.reason.name,
              EXPLORE_FAILURE_GALLERY_FIELD to failure.isGalleryImage,
          ),
      )
    }
    guide.discoveryDate?.let { put("discoveryDate", it) }
    put(FAVORITE_FIELD, guide.isFavorite)
    put(SHARE_AUDIENCE_FIELD, guide.shareSettings.audience.name)
    if (guide.shareSettings.sharedWithUids.isNotEmpty()) {
      put(SHARED_WITH_UIDS_FIELD, guide.shareSettings.sharedWithUids.sorted())
    }
    if (guide.shareSettings.dismissedByUids.isNotEmpty()) {
      put(DISMISSED_BY_UIDS_FIELD, guide.shareSettings.dismissedByUids.sorted())
    }
  }

  fun fromFields(id: String, fields: Map<String, Any>): Guide {
    val rawAudience = fields[SHARE_AUDIENCE_FIELD]
    val audience =
        when (rawAudience) {
          null -> ShareAudience.ALL_FRIENDS
          is String ->
              ShareAudience.entries.firstOrNull { it.name == rawAudience } ?: ShareAudience.NONE
          else -> ShareAudience.NONE
        }
    return Guide(
        id = id,
        photoBytes = required<Blob>(fields, "photo").toBytes(),
        monumentName = required(fields, "monumentName"),
        confidence = required(fields, "confidence"),
        history = required(fields, "history"),
        facts = requiredStringList(fields, "facts"),
        culture = required(fields, "culture"),
        sections = optionalSections(fields["sections"]),
        overview = fields["overview"] as? String,
        timeline = optionalTimeline(fields["timeline"]),
        interestCount = (fields["interestCount"] as? Number)?.toInt() ?: 0,
        location = optionalCoordinates(fields[LOCATION_FIELD]),
        monumentLocation =
            runCatching { optionalCoordinates(fields[MONUMENT_LOCATION_FIELD]) }.getOrNull(),
        exploreLocationFailure =
            optionalExploreLocationFailure(fields[EXPLORE_LOCATION_FAILURE_FIELD]),
        discoveryDate = (fields["discoveryDate"] as? Number)?.toLong(),
        // Guides saved before favourites existed have no flag, so they are not favourites.
        isFavorite = fields[FAVORITE_FIELD] as? Boolean ?: false,
        shareSettings =
            MonumentShareSettings(
                audience = audience,
                sharedWithUids = optionalStringSet(fields, SHARED_WITH_UIDS_FIELD),
                dismissedByUids = optionalStringSet(fields, DISMISSED_BY_UIDS_FIELD),
            ),
    )
  }

  private fun optionalStringSet(fields: Map<String, Any>, key: String): Set<String> =
      (fields[key] as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()

  private fun optionalSections(value: Any?): List<GuideSection> =
      (value as? List<*>)?.mapNotNull { item ->
        val section = item as? Map<*, *> ?: return@mapNotNull null
        val title = section["title"] as? String ?: return@mapNotNull null
        val content = section["content"] as? String ?: return@mapNotNull null
        GuideSection(title, content)
      } ?: emptyList()

  private fun optionalTimeline(value: Any?): List<GuideMilestone> =
      (value as? List<*>)
          ?.mapNotNull { item ->
            val milestone = item as? Map<*, *> ?: return@mapNotNull null
            val year = (milestone["year"] as? Number)?.toString()?.toIntOrNull()
            if (year == null || year == 0 || year == Int.MIN_VALUE) return@mapNotNull null
            val event =
                (milestone["event"] as? String)?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
            GuideMilestone(year, event)
          }
          .orEmpty()

  private fun optionalExploreLocationFailure(value: Any?): ExploreLocationFailure? {
    val fields = value as? Map<*, *> ?: return null
    val reason =
        ExploreUnavailableReason.entries.firstOrNull {
          it.name == fields[EXPLORE_FAILURE_REASON_FIELD]
        } ?: return null
    return ExploreLocationFailure(
        reason,
        fields[EXPLORE_FAILURE_GALLERY_FIELD] as? Boolean ?: false,
    )
  }

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
