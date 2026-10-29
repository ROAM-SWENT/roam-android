// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.guide

import com.github.roamswent.roam.model.location.Coordinates
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FirestoreSavedGuideRepositoryTest {
  private val firestore = mockk<FirebaseFirestore>()
  private val auth = mockk<FirebaseAuth>()
  private val user = mockk<FirebaseUser>()
  private val usersCollection = mockk<CollectionReference>()
  private val userDocument = mockk<DocumentReference>()
  private val savedGuidesCollection = mockk<CollectionReference>()
  private val repository = FirestoreSavedGuideRepository(firestore, auth)

  private val guide =
      Guide(
          id = "guide-id",
          photoBytes = byteArrayOf(1, 2, 3),
          monumentName = "Eiffel Tower",
          confidence = 0.92,
          history = "Built in 1889.",
          facts = listOf("330 meters tall"),
          culture = "A symbol of Paris.",
          location = Coordinates(48.8584, 2.2945),
          discoveryDate = 1_798_000_000_000,
      )

  @Before
  fun setUp() {
    every { auth.currentUser } returns user
    every { user.uid } returns "user-id"
    every { firestore.collection("users") } returns usersCollection
    every { usersCollection.document("user-id") } returns userDocument
    every { userDocument.collection("savedGuides") } returns savedGuidesCollection
  }

  @Test
  fun defaultConstructorConfiguresUnlimitedPersistentCache() {
    mockkStatic(FirebaseFirestore::class)
    mockkStatic(FirebaseAuth::class)
    try {
      every { FirebaseFirestore.getInstance() } returns firestore
      every { FirebaseAuth.getInstance() } returns auth
      every { firestore.firestoreSettings = any() } just Runs

      FirestoreSavedGuideRepository()

      verify { FirebaseFirestore.getInstance() }
      verify { firestore.firestoreSettings = any() }
    } finally {
      unmockkStatic(FirebaseAuth::class)
      unmockkStatic(FirebaseFirestore::class)
    }
  }

  @Test
  fun addGuideUsesProvidedIdAndPersistsAllFields() = runTest {
    val document = mockk<DocumentReference>()
    every { savedGuidesCollection.document("guide-id") } returns document
    every { document.id } returns "guide-id"
    every { document.path } returns "users/user-id/savedGuides/guide-id"
    every { document.set(any<Map<String, Any>>()) } returns Tasks.forResult(null)

    val savedGuide = repository.addGuide(guide)

    assertEquals(guide, savedGuide)
    verify { document.set(GuideFirestoreMapper.toFields(guide)) }
  }

  @Test
  fun addGuideCreatesIdWhenBlank() = runTest {
    val document = mockk<DocumentReference>()
    val guideWithoutId = guide.copy(id = "")
    every { savedGuidesCollection.document() } returns document
    every { document.id } returns "generated-id"
    every { document.path } returns "users/user-id/savedGuides/generated-id"
    every { document.set(any<Map<String, Any>>()) } returns Tasks.forResult(null)

    val savedGuide = repository.addGuide(guideWithoutId)

    assertEquals("generated-id", savedGuide.id)
    verify { document.set(GuideFirestoreMapper.toFields(savedGuide)) }
  }

  @Test
  fun removeGuideDeletesTheRequestedDocument() = runTest {
    val document = mockk<DocumentReference>()
    every { savedGuidesCollection.document("guide-id") } returns document
    every { document.delete() } returns Tasks.forResult(null)

    repository.removeGuide("guide-id")

    verify { document.delete() }
  }

  @Test
  fun setFavoriteUpdatesOnlyTheFavoriteFlag() = runTest {
    val document = mockk<DocumentReference>()
    every { savedGuidesCollection.document("guide-id") } returns document
    every { document.update("isFavorite", true) } returns Tasks.forResult(null)

    repository.setFavorite("guide-id", true)

    verify(exactly = 1) { document.update("isFavorite", true) }
    verify(exactly = 0) { document.set(any()) }
  }

  @Test
  fun setFavoritePropagatesFirestoreFailures() = runTest {
    val document = mockk<DocumentReference>()
    val failure =
        FirebaseFirestoreException("guide deleted", FirebaseFirestoreException.Code.NOT_FOUND)
    every { savedGuidesCollection.document("guide-id") } returns document
    every { document.update("isFavorite", false) } returns Tasks.forException(failure)

    val error = captureFailure { repository.setFavorite("guide-id", false) }

    assertTrue(error === failure)
  }

  @Test
  fun setFavoriteRequiresAnAuthenticatedUser() = runTest {
    every { auth.currentUser } returns null

    val error = captureFailure { repository.setFavorite("guide-id", true) }

    assertTrue(error is IllegalStateException)
  }

  @Test
  fun getSavedGuidesMapsEveryDocument() = runTest {
    val document = mockk<QueryDocumentSnapshot>()
    val snapshot = mockk<QuerySnapshot>()
    every { savedGuidesCollection.get() } returns Tasks.forResult(snapshot)
    every { snapshot.documents } returns listOf(document)
    every { document.id } returns guide.id
    every { document.data } returns GuideFirestoreMapper.toFields(guide)

    assertEquals(listOf(guide), repository.getSavedGuides())
  }

  @Test
  fun getSavedGuidesRejectsMalformedDocuments() = runTest {
    val document = mockk<QueryDocumentSnapshot>()
    val snapshot = mockk<QuerySnapshot>()
    every { savedGuidesCollection.get() } returns Tasks.forResult(snapshot)
    every { snapshot.documents } returns listOf(document)
    every { document.id } returns guide.id
    every { document.data } returns mapOf("photo" to Blob.fromBytes(guide.photoBytes))

    val error = captureFailure { repository.getSavedGuides() }

    assertTrue(error is IllegalStateException)
    assertEquals("Saved guide is missing a valid 'monumentName' field", error.message)
  }

  @Test
  fun getGuideReturnsNullWhenDocumentDoesNotExist() = runTest {
    val document = mockk<DocumentReference>()
    val snapshot = mockk<com.google.firebase.firestore.DocumentSnapshot>()
    every { savedGuidesCollection.document("missing-id") } returns document
    every { document.get() } returns Tasks.forResult(snapshot)
    every { snapshot.exists() } returns false

    assertNull(repository.getGuide("missing-id"))
  }

  @Test
  fun getGuideMapsAnExistingDocument() = runTest {
    val document = mockk<DocumentReference>()
    val snapshot = mockk<com.google.firebase.firestore.DocumentSnapshot>()
    every { savedGuidesCollection.document("guide-id") } returns document
    every { document.get() } returns Tasks.forResult(snapshot)
    every { snapshot.exists() } returns true
    every { snapshot.id } returns guide.id
    every { snapshot.data } returns GuideFirestoreMapper.toFields(guide)

    assertEquals(guide, repository.getGuide("guide-id"))
  }

  @Test
  fun getGuideRejectsExistingDocumentWithoutData() = runTest {
    val document = mockk<DocumentReference>()
    val snapshot = mockk<com.google.firebase.firestore.DocumentSnapshot>()
    every { savedGuidesCollection.document("guide-id") } returns document
    every { document.get() } returns Tasks.forResult(snapshot)
    every { snapshot.exists() } returns true
    every { snapshot.id } returns guide.id
    every { snapshot.data } returns null

    val error = captureFailure { repository.getGuide("guide-id") }

    assertTrue(error is IllegalStateException)
    assertEquals("Saved guide guide-id has no data", error.message)
  }

  @Test
  fun observeSavedGuidesEmitsSnapshotsAndRemovesListenerWhenCancelled() = runTest {
    val document = mockk<QueryDocumentSnapshot>()
    val snapshot = mockk<QuerySnapshot>()
    val registration = mockk<ListenerRegistration>(relaxed = true)
    every { savedGuidesCollection.addSnapshotListener(MetadataChanges.INCLUDE, any()) } answers
        {
          secondArg<EventListener<QuerySnapshot>>().onEvent(snapshot, null)
          registration
        }
    every { snapshot.documents } returns listOf(document)
    every { document.id } returns guide.id
    every { document.data } returns GuideFirestoreMapper.toFields(guide)

    val emissions = repository.observeSavedGuides().take(1).toList()

    assertEquals(listOf(listOf(guide)), emissions)
    verify { savedGuidesCollection.addSnapshotListener(MetadataChanges.INCLUDE, any()) }
    verify { registration.remove() }
  }

  @Test
  fun observeSavedGuidesPropagatesListenerErrorsAndRemovesListener() = runTest {
    val registration = mockk<ListenerRegistration>(relaxed = true)
    val listenerError =
        FirebaseFirestoreException(
            "listener failed",
            FirebaseFirestoreException.Code.UNKNOWN,
        )
    every { savedGuidesCollection.addSnapshotListener(MetadataChanges.INCLUDE, any()) } answers
        {
          secondArg<EventListener<QuerySnapshot>>().onEvent(null, listenerError)
          registration
        }

    val error = captureFailure { repository.observeSavedGuides().toList() }

    assertTrue(error === listenerError)
    assertEquals(listenerError, error)
    verify { registration.remove() }
  }

  @Test
  fun observeSavedGuidesReportsMissingSnapshotAndMalformedDocuments() = runTest {
    val registrationForMissingSnapshot = mockk<ListenerRegistration>(relaxed = true)
    every { savedGuidesCollection.addSnapshotListener(MetadataChanges.INCLUDE, any()) } answers
        {
          secondArg<EventListener<QuerySnapshot>>().onEvent(null, null)
          registrationForMissingSnapshot
        }

    val missingSnapshotError = captureFailure { repository.observeSavedGuides().toList() }
    assertTrue(missingSnapshotError is IllegalStateException)
    assertEquals("Saved guides snapshot is missing", missingSnapshotError.message)
    verify { registrationForMissingSnapshot.remove() }

    val document = mockk<QueryDocumentSnapshot>()
    val snapshot = mockk<QuerySnapshot>()
    val registrationForMalformedGuide = mockk<ListenerRegistration>(relaxed = true)
    every { savedGuidesCollection.addSnapshotListener(MetadataChanges.INCLUDE, any()) } answers
        {
          secondArg<EventListener<QuerySnapshot>>().onEvent(snapshot, null)
          registrationForMalformedGuide
        }
    every { snapshot.documents } returns listOf(document)
    every { document.id } returns guide.id
    every { document.data } returns mapOf("photo" to Blob.fromBytes(guide.photoBytes))

    val malformedGuideError = captureFailure { repository.observeSavedGuides().toList() }

    assertTrue(malformedGuideError is IllegalStateException)
    assertEquals("Saved guide is missing a valid 'monumentName' field", malformedGuideError.message)
    verify { registrationForMalformedGuide.remove() }
  }

  @Test
  fun savedGuideOperationsRequireAnAuthenticatedUser() = runTest {
    every { auth.currentUser } returns null

    val error = captureFailure { repository.getSavedGuides() }

    assertTrue(error is IllegalStateException)
    assertEquals("A signed-in user is required to access saved guides", error.message)
  }

  @Test
  fun mapperOmitsUnavailableOptionalFields() {
    val fields = GuideFirestoreMapper.toFields(guide.copy(location = null, discoveryDate = null))

    assertEquals(false, fields.containsKey("location"))
    assertEquals(false, fields.containsKey("discoveryDate"))
  }

  @Test
  fun mapperRejectsInvalidCoordinatesAndFacts() = runTest {
    val validFields = GuideFirestoreMapper.toFields(guide)
    val invalidLocationFields = validFields + ("location" to "not coordinates")
    val missingLatitudeFields = validFields + ("location" to mapOf("longitude" to 2.2945))
    val invalidFactsFields = validFields + ("facts" to listOf(42))

    val invalidLocationError = captureFailure {
      GuideFirestoreMapper.fromFields(guide.id, invalidLocationFields)
    }
    val missingLatitudeError = captureFailure {
      GuideFirestoreMapper.fromFields(guide.id, missingLatitudeFields)
    }
    val invalidFactsError = captureFailure {
      GuideFirestoreMapper.fromFields(guide.id, invalidFactsFields)
    }

    assertEquals("Saved guide has an invalid 'location' field", invalidLocationError.message)
    assertEquals("Saved guide has an invalid 'location' field", missingLatitudeError.message)
    assertEquals("Saved guide has an invalid 'facts' item at index 0", invalidFactsError.message)
  }

  private suspend fun captureFailure(action: suspend () -> Unit): Exception {
    try {
      action()
    } catch (exception: Exception) {
      return exception
    }
    throw AssertionError("Expected operation to fail")
  }
}
