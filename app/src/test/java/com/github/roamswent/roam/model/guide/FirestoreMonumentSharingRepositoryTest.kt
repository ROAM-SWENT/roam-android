// Co-authored-by: Gemini 3.8 Flash <noreply@google.com>

package com.github.roamswent.roam.model.guide

import com.github.roamswent.roam.model.location.Coordinates
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Transaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FirestoreMonumentSharingRepositoryTest {
  private val firestore = mockk<FirebaseFirestore>()
  private val auth = mockk<FirebaseAuth>()
  private val user = mockk<FirebaseUser>()
  private val usersCollection = mockk<CollectionReference>()
  private val userDocument = mockk<DocumentReference>()
  private val savedGuidesCollection = mockk<CollectionReference>()

  private val ownerUserDocument = mockk<DocumentReference>()
  private val ownerSavedGuidesCollection = mockk<CollectionReference>()

  private val repository = FirestoreMonumentSharingRepository(firestore, auth)

  private val currentUid = "current-uid"
  private val ownerUid = "owner-uid"
  private val guideId = "guide-123"

  private val baseGuide =
      Guide(
          id = guideId,
          photoBytes = byteArrayOf(1, 2, 3),
          monumentName = "Eiffel Tower",
          confidence = 0.98,
          history = "Historic landmark in Paris.",
          facts = listOf("Constructed in 1889", "330m tall"),
          culture = "Iconic French symbol.",
          location = Coordinates(48.8584, 2.2945),
          discoveryDate = 1700000000000L,
          isFavorite = true,
          shareSettings =
              MonumentShareSettings(
                  audience = ShareAudience.ALL_FRIENDS,
                  sharedWithUids = emptySet(),
                  dismissedByUids = emptySet(),
              ),
      )

  @Before
  fun setUp() {
    every { auth.currentUser } returns user
    every { user.uid } returns currentUid

    every { firestore.collection("users") } returns usersCollection
    every { usersCollection.document(currentUid) } returns userDocument
    every { userDocument.collection("savedGuides") } returns savedGuidesCollection

    every { usersCollection.document(ownerUid) } returns ownerUserDocument
    every { ownerUserDocument.collection("savedGuides") } returns ownerSavedGuidesCollection
  }

  private inline fun <reified T> runTransactionWith(transaction: Transaction) {
    every { firestore.runTransaction<T>(any()) } answers
        {
          val function = firstArg<Transaction.Function<T>>()
          Tasks.forResult(function.apply(transaction))
        }
  }

  // region setShareAudience tests

  @Test
  fun setShareAudienceSelectedFriendsWritesSortedRecipientsAndClearsDismissed() = runTest {
    val guideRef = mockk<DocumentReference>()
    val snapshot = mockk<DocumentSnapshot>()
    val transaction = mockk<Transaction>()

    every { savedGuidesCollection.document(guideId) } returns guideRef
    every { transaction.get(guideRef) } returns snapshot
    every { snapshot.exists() } returns true

    val updatesSlot = slot<Map<String, Any>>()
    every { transaction.update(guideRef, capture(updatesSlot)) } returns transaction
    runTransactionWith<Unit>(transaction)

    repository.setShareAudience(
        guideId = guideId,
        audience = ShareAudience.SELECTED_FRIENDS,
        recipientUids = setOf("uid-z", "uid-a", "uid-m"),
    )

    val updates = updatesSlot.captured
    assertEquals(
        ShareAudience.SELECTED_FRIENDS.name,
        updates[GuideFirestoreMapper.SHARE_AUDIENCE_FIELD],
    )
    assertEquals(
        listOf("uid-a", "uid-m", "uid-z"),
        updates[GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD],
    )
    assertTrue(updates[GuideFirestoreMapper.DISMISSED_BY_UIDS_FIELD] is FieldValue)
  }

  @Test
  fun setShareAudienceSelectedFriendsWithEmptyRecipientsDoesNotRemoveDismissed() = runTest {
    val guideRef = mockk<DocumentReference>()
    val snapshot = mockk<DocumentSnapshot>()
    val transaction = mockk<Transaction>()

    every { savedGuidesCollection.document(guideId) } returns guideRef
    every { transaction.get(guideRef) } returns snapshot
    every { snapshot.exists() } returns true

    val updatesSlot = slot<Map<String, Any>>()
    every { transaction.update(guideRef, capture(updatesSlot)) } returns transaction
    runTransactionWith<Unit>(transaction)

    repository.setShareAudience(
        guideId = guideId,
        audience = ShareAudience.SELECTED_FRIENDS,
        recipientUids = emptySet(),
    )

    val updates = updatesSlot.captured
    assertEquals(
        ShareAudience.SELECTED_FRIENDS.name,
        updates[GuideFirestoreMapper.SHARE_AUDIENCE_FIELD],
    )
    assertEquals(emptyList<String>(), updates[GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD])
    assertTrue(GuideFirestoreMapper.DISMISSED_BY_UIDS_FIELD !in updates)
  }

  @Test
  fun setShareAudienceAllFriendsDeletesSharedWithAndDismissedBy() = runTest {
    val guideRef = mockk<DocumentReference>()
    val snapshot = mockk<DocumentSnapshot>()
    val transaction = mockk<Transaction>()

    every { savedGuidesCollection.document(guideId) } returns guideRef
    every { transaction.get(guideRef) } returns snapshot
    every { snapshot.exists() } returns true

    val updatesSlot = slot<Map<String, Any>>()
    every { transaction.update(guideRef, capture(updatesSlot)) } returns transaction
    runTransactionWith<Unit>(transaction)

    repository.setShareAudience(
        guideId = guideId,
        audience = ShareAudience.ALL_FRIENDS,
        recipientUids = emptySet(),
    )

    val updates = updatesSlot.captured
    assertEquals(ShareAudience.ALL_FRIENDS.name, updates[GuideFirestoreMapper.SHARE_AUDIENCE_FIELD])
    assertEquals(FieldValue.delete(), updates[GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD])
    assertEquals(FieldValue.delete(), updates[GuideFirestoreMapper.DISMISSED_BY_UIDS_FIELD])
  }

  @Test
  fun setShareAudienceAllFollowersDeletesSharedWithAndDismissedBy() = runTest {
    val guideRef = mockk<DocumentReference>()
    val snapshot = mockk<DocumentSnapshot>()
    val transaction = mockk<Transaction>()

    every { savedGuidesCollection.document(guideId) } returns guideRef
    every { transaction.get(guideRef) } returns snapshot
    every { snapshot.exists() } returns true

    val updatesSlot = slot<Map<String, Any>>()
    every { transaction.update(guideRef, capture(updatesSlot)) } returns transaction
    runTransactionWith<Unit>(transaction)

    repository.setShareAudience(
        guideId = guideId,
        audience = ShareAudience.ALL_FOLLOWERS,
        recipientUids = emptySet(),
    )

    val updates = updatesSlot.captured
    assertEquals(
        ShareAudience.ALL_FOLLOWERS.name,
        updates[GuideFirestoreMapper.SHARE_AUDIENCE_FIELD],
    )
    assertEquals(FieldValue.delete(), updates[GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD])
    assertEquals(FieldValue.delete(), updates[GuideFirestoreMapper.DISMISSED_BY_UIDS_FIELD])
  }

  @Test
  fun setShareAudienceNoneDeletesSharedWithWithoutDeletingDismissedBy() = runTest {
    val guideRef = mockk<DocumentReference>()
    val snapshot = mockk<DocumentSnapshot>()
    val transaction = mockk<Transaction>()

    every { savedGuidesCollection.document(guideId) } returns guideRef
    every { transaction.get(guideRef) } returns snapshot
    every { snapshot.exists() } returns true

    val updatesSlot = slot<Map<String, Any>>()
    every { transaction.update(guideRef, capture(updatesSlot)) } returns transaction
    runTransactionWith<Unit>(transaction)

    repository.setShareAudience(
        guideId = guideId,
        audience = ShareAudience.NONE,
        recipientUids = emptySet(),
    )

    val updates = updatesSlot.captured
    assertEquals(ShareAudience.NONE.name, updates[GuideFirestoreMapper.SHARE_AUDIENCE_FIELD])
    assertEquals(FieldValue.delete(), updates[GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD])
    assertTrue(GuideFirestoreMapper.DISMISSED_BY_UIDS_FIELD !in updates)
  }

  @Test
  fun setShareAudienceThrowsGuideNotFoundWhenSnapshotDoesNotExist() = runTest {
    val guideRef = mockk<DocumentReference>()
    val snapshot = mockk<DocumentSnapshot>()
    val transaction = mockk<Transaction>()

    every { savedGuidesCollection.document(guideId) } returns guideRef
    every { transaction.get(guideRef) } returns snapshot
    every { snapshot.exists() } returns false
    runTransactionWith<Unit>(transaction)

    assertThrows(MonumentSharingException.GuideNotFound::class.java) {
      kotlinx.coroutines.test.runTest {
        repository.setShareAudience(guideId, ShareAudience.ALL_FRIENDS)
      }
    }
  }

  // endregion

  // region grantAccess tests

  @Test
  fun grantAccessPromotesNoneToSelectedFriendsAndClearsDismissed() = runTest {
    val guideRef = mockk<DocumentReference>()
    val snapshot = mockk<DocumentSnapshot>()
    val transaction = mockk<Transaction>()

    every { savedGuidesCollection.document(guideId) } returns guideRef
    every { transaction.get(guideRef) } returns snapshot
    every { snapshot.exists() } returns true
    every { snapshot.getString(GuideFirestoreMapper.SHARE_AUDIENCE_FIELD) } returns
        ShareAudience.NONE.name

    val updatesSlot = slot<Map<String, Any>>()
    every { transaction.update(guideRef, capture(updatesSlot)) } returns transaction
    runTransactionWith<Unit>(transaction)

    repository.grantAccess(guideId, setOf("uid-alice", "uid-bob"))

    val updates = updatesSlot.captured
    assertEquals(
        ShareAudience.SELECTED_FRIENDS.name,
        updates[GuideFirestoreMapper.SHARE_AUDIENCE_FIELD],
    )
    assertTrue(updates[GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD] is FieldValue)
    assertTrue(updates[GuideFirestoreMapper.DISMISSED_BY_UIDS_FIELD] is FieldValue)
  }

  @Test
  fun grantAccessRetainsExistingAudienceWhenNotNone() = runTest {
    val guideRef = mockk<DocumentReference>()
    val snapshot = mockk<DocumentSnapshot>()
    val transaction = mockk<Transaction>()

    every { savedGuidesCollection.document(guideId) } returns guideRef
    every { transaction.get(guideRef) } returns snapshot
    every { snapshot.exists() } returns true
    every { snapshot.getString(GuideFirestoreMapper.SHARE_AUDIENCE_FIELD) } returns
        ShareAudience.SELECTED_FRIENDS.name

    val updatesSlot = slot<Map<String, Any>>()
    every { transaction.update(guideRef, capture(updatesSlot)) } returns transaction
    runTransactionWith<Unit>(transaction)

    repository.grantAccess(guideId, setOf("uid-charlie"))

    val updates = updatesSlot.captured
    assertTrue(GuideFirestoreMapper.SHARE_AUDIENCE_FIELD !in updates)
    assertTrue(updates[GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD] is FieldValue)
    assertTrue(updates[GuideFirestoreMapper.DISMISSED_BY_UIDS_FIELD] is FieldValue)
  }

  @Test
  fun grantAccessEmptyRecipientsIsNoOp() = runTest {
    repository.grantAccess(guideId, emptySet())
    verify(exactly = 0) { firestore.runTransaction<Any>(any()) }
  }

  @Test
  fun grantAccessThrowsGuideNotFoundWhenSnapshotDoesNotExist() = runTest {
    val guideRef = mockk<DocumentReference>()
    val snapshot = mockk<DocumentSnapshot>()
    val transaction = mockk<Transaction>()

    every { savedGuidesCollection.document(guideId) } returns guideRef
    every { transaction.get(guideRef) } returns snapshot
    every { snapshot.exists() } returns false
    runTransactionWith<Unit>(transaction)

    assertThrows(MonumentSharingException.GuideNotFound::class.java) {
      kotlinx.coroutines.test.runTest { repository.grantAccess(guideId, setOf("uid-alice")) }
    }
  }

  // endregion

  // region revokeAccess tests

  @Test
  fun revokeAccessRemovesRecipientsFromSharedWith() = runTest {
    val guideRef = mockk<DocumentReference>()
    val snapshot = mockk<DocumentSnapshot>()
    val transaction = mockk<Transaction>()

    every { savedGuidesCollection.document(guideId) } returns guideRef
    every { transaction.get(guideRef) } returns snapshot
    every { snapshot.exists() } returns true
    every {
      transaction.update(
          guideRef,
          GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD,
          any<FieldValue>(),
      )
    } returns transaction
    runTransactionWith<Unit>(transaction)

    repository.revokeAccess(guideId, setOf("uid-alice", "uid-bob"))

    verify {
      transaction.update(
          guideRef,
          GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD,
          any<FieldValue>(),
      )
    }
  }

  @Test
  fun revokeAccessEmptyRecipientsIsNoOp() = runTest {
    repository.revokeAccess(guideId, emptySet())
    verify(exactly = 0) { firestore.runTransaction<Any>(any()) }
  }

  @Test
  fun revokeAccessThrowsGuideNotFoundWhenSnapshotDoesNotExist() = runTest {
    val guideRef = mockk<DocumentReference>()
    val snapshot = mockk<DocumentSnapshot>()
    val transaction = mockk<Transaction>()

    every { savedGuidesCollection.document(guideId) } returns guideRef
    every { transaction.get(guideRef) } returns snapshot
    every { snapshot.exists() } returns false
    runTransactionWith<Unit>(transaction)

    assertThrows(MonumentSharingException.GuideNotFound::class.java) {
      kotlinx.coroutines.test.runTest { repository.revokeAccess(guideId, setOf("uid-alice")) }
    }
  }

  // endregion

  // region dismissSharedGuide tests

  @Test
  fun dismissSharedGuideAddsOnlyCurrentUidToDismissedAndRemovesFromShared() = runTest {
    val guideRef = mockk<DocumentReference>()
    every { ownerSavedGuidesCollection.document(guideId) } returns guideRef
    val updatesSlot = slot<Map<String, Any>>()
    every { guideRef.update(capture(updatesSlot)) } returns Tasks.forResult(null)

    repository.dismissSharedGuide(ownerUid, guideId)

    val updates = updatesSlot.captured
    assertTrue(updates[GuideFirestoreMapper.DISMISSED_BY_UIDS_FIELD] is FieldValue)
    assertTrue(updates[GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD] is FieldValue)
    verify(exactly = 0) { guideRef.delete() }
  }

  // endregion

  // region getShareSettings tests

  @Test
  fun getShareSettingsReturnsParsedSettingsWhenGuideExists() = runTest {
    val guideRef = mockk<DocumentReference>()
    val snapshot = mockk<DocumentSnapshot>()
    every { savedGuidesCollection.document(guideId) } returns guideRef
    every { guideRef.get() } returns Tasks.forResult(snapshot)
    every { snapshot.exists() } returns true
    every { snapshot.id } returns guideId

    val expectedSettings =
        MonumentShareSettings(
            audience = ShareAudience.SELECTED_FRIENDS,
            sharedWithUids = setOf("friend-1", "friend-2"),
            dismissedByUids = setOf("dismissed-1"),
        )
    val guide = baseGuide.copy(shareSettings = expectedSettings)
    every { snapshot.data } returns GuideFirestoreMapper.toFields(guide)

    val settings = repository.getShareSettings(guideId)
    assertEquals(expectedSettings, settings)
  }

  @Test
  fun getShareSettingsThrowsGuideNotFoundWhenSnapshotDoesNotExist() = runTest {
    val guideRef = mockk<DocumentReference>()
    val snapshot = mockk<DocumentSnapshot>()
    every { savedGuidesCollection.document(guideId) } returns guideRef
    every { guideRef.get() } returns Tasks.forResult(snapshot)
    every { snapshot.exists() } returns false

    assertThrows(MonumentSharingException.GuideNotFound::class.java) {
      kotlinx.coroutines.test.runTest { repository.getShareSettings(guideId) }
    }
  }

  @Test
  fun getShareSettingsThrowsGuideNotFoundWhenSnapshotDataIsNull() = runTest {
    val guideRef = mockk<DocumentReference>()
    val snapshot = mockk<DocumentSnapshot>()
    every { savedGuidesCollection.document(guideId) } returns guideRef
    every { guideRef.get() } returns Tasks.forResult(snapshot)
    every { snapshot.exists() } returns true
    every { snapshot.data } returns null

    assertThrows(MonumentSharingException.GuideNotFound::class.java) {
      kotlinx.coroutines.test.runTest { repository.getShareSettings(guideId) }
    }
  }

  // endregion

  // region getSharedGuidesFrom & observeSharedGuidesFrom tests

  @Test
  fun getSharedGuidesFromFiltersOutNoneAndDismissedGuides() = runTest {
    val querySnapshot = mockk<QuerySnapshot>()
    every { ownerSavedGuidesCollection.get() } returns Tasks.forResult(querySnapshot)

    val sharedGuide =
        baseGuide.copy(
            id = "guide-shared",
            shareSettings = MonumentShareSettings(audience = ShareAudience.ALL_FRIENDS),
        )
    val noneGuide =
        baseGuide.copy(
            id = "guide-none",
            shareSettings = MonumentShareSettings(audience = ShareAudience.NONE),
        )
    val dismissedGuide =
        baseGuide.copy(
            id = "guide-dismissed",
            shareSettings =
                MonumentShareSettings(
                    audience = ShareAudience.ALL_FRIENDS,
                    dismissedByUids = setOf(currentUid),
                ),
        )

    val doc1 = mockk<QueryDocumentSnapshot>()
    every { doc1.id } returns sharedGuide.id
    every { doc1.data } returns GuideFirestoreMapper.toFields(sharedGuide)

    val doc2 = mockk<QueryDocumentSnapshot>()
    every { doc2.id } returns noneGuide.id
    every { doc2.data } returns GuideFirestoreMapper.toFields(noneGuide)

    val doc3 = mockk<QueryDocumentSnapshot>()
    every { doc3.id } returns dismissedGuide.id
    every { doc3.data } returns GuideFirestoreMapper.toFields(dismissedGuide)

    every { querySnapshot.documents } returns listOf(doc1, doc2, doc3)

    val result = repository.getSharedGuidesFrom(ownerUid)
    assertEquals(listOf(sharedGuide), result)
  }

  @Test
  fun observeSharedGuidesFromEmitsFilteredGuidesAndRemovesListenerWhenCancelled() = runTest {
    val registration = mockk<ListenerRegistration>(relaxed = true)

    val sharedGuide =
        baseGuide.copy(
            id = "guide-shared",
            shareSettings = MonumentShareSettings(audience = ShareAudience.ALL_FRIENDS),
        )
    val noneGuide =
        baseGuide.copy(
            id = "guide-none",
            shareSettings = MonumentShareSettings(audience = ShareAudience.NONE),
        )
    val dismissedGuide =
        baseGuide.copy(
            id = "guide-dismissed",
            shareSettings =
                MonumentShareSettings(
                    audience = ShareAudience.ALL_FRIENDS,
                    dismissedByUids = setOf(currentUid),
                ),
        )

    val doc1 = mockk<QueryDocumentSnapshot>()
    every { doc1.id } returns sharedGuide.id
    every { doc1.data } returns GuideFirestoreMapper.toFields(sharedGuide)

    val doc2 = mockk<QueryDocumentSnapshot>()
    every { doc2.id } returns noneGuide.id
    every { doc2.data } returns GuideFirestoreMapper.toFields(noneGuide)

    val doc3 = mockk<QueryDocumentSnapshot>()
    every { doc3.id } returns dismissedGuide.id
    every { doc3.data } returns GuideFirestoreMapper.toFields(dismissedGuide)

    val querySnapshot = mockk<QuerySnapshot>()
    every { querySnapshot.documents } returns listOf(doc1, doc2, doc3)

    every { ownerSavedGuidesCollection.addSnapshotListener(MetadataChanges.INCLUDE, any()) } answers
        {
          secondArg<EventListener<QuerySnapshot>>().onEvent(querySnapshot, null)
          registration
        }

    val emissions = repository.observeSharedGuidesFrom(ownerUid).take(1).toList()

    assertEquals(listOf(listOf(sharedGuide)), emissions)
    verify { ownerSavedGuidesCollection.addSnapshotListener(MetadataChanges.INCLUDE, any()) }
    verify { registration.remove() }
  }

  @Test
  fun observeSharedGuidesFromEmitsErrorWhenListenerFails() = runTest {
    val registration = mockk<ListenerRegistration>(relaxed = true)
    val firestoreException =
        FirebaseFirestoreException("Network error", FirebaseFirestoreException.Code.UNAVAILABLE)

    every { ownerSavedGuidesCollection.addSnapshotListener(MetadataChanges.INCLUDE, any()) } answers
        {
          secondArg<EventListener<QuerySnapshot>>().onEvent(null, firestoreException)
          registration
        }

    assertThrows(MonumentSharingException.FirestoreFailure::class.java) {
      kotlinx.coroutines.test.runTest {
        repository.observeSharedGuidesFrom(ownerUid).take(1).toList()
      }
    }
    verify { registration.remove() }
  }

  @Test
  fun observeSharedGuidesFromClosesWithErrorWhenDocumentDataMalformed() = runTest {
    val registration = mockk<ListenerRegistration>(relaxed = true)
    val doc = mockk<QueryDocumentSnapshot>()
    every { doc.id } returns "malformed-guide"
    every { doc.data } returns emptyMap<String, Any>()

    val querySnapshot = mockk<QuerySnapshot>()
    every { querySnapshot.documents } returns listOf(doc)

    every { ownerSavedGuidesCollection.addSnapshotListener(MetadataChanges.INCLUDE, any()) } answers
        {
          secondArg<EventListener<QuerySnapshot>>().onEvent(querySnapshot, null)
          registration
        }

    assertThrows(MonumentSharingException.FirestoreFailure::class.java) {
      kotlinx.coroutines.test.runTest {
        repository.observeSharedGuidesFrom(ownerUid).take(1).toList()
      }
    }
    verify { registration.remove() }
  }

  // endregion

  // region Error mapping & input validation tests

  @Test
  fun requireCurrentUidThrowsUnauthenticatedUserWhenUserIsNull() = runTest {
    every { auth.currentUser } returns null

    assertThrows(MonumentSharingException.UnauthenticatedUser::class.java) {
      kotlinx.coroutines.test.runTest {
        repository.setShareAudience(guideId, ShareAudience.ALL_FRIENDS)
      }
    }
    assertThrows(MonumentSharingException.UnauthenticatedUser::class.java) {
      kotlinx.coroutines.test.runTest { repository.grantAccess(guideId, setOf("uid-alice")) }
    }
    assertThrows(MonumentSharingException.UnauthenticatedUser::class.java) {
      kotlinx.coroutines.test.runTest { repository.revokeAccess(guideId, setOf("uid-alice")) }
    }
    assertThrows(MonumentSharingException.UnauthenticatedUser::class.java) {
      kotlinx.coroutines.test.runTest { repository.dismissSharedGuide(ownerUid, guideId) }
    }
    assertThrows(MonumentSharingException.UnauthenticatedUser::class.java) {
      kotlinx.coroutines.test.runTest { repository.getShareSettings(guideId) }
    }
    assertThrows(MonumentSharingException.UnauthenticatedUser::class.java) {
      kotlinx.coroutines.test.runTest { repository.getSharedGuidesFrom(ownerUid) }
    }
    assertThrows(MonumentSharingException.UnauthenticatedUser::class.java) {
      repository.observeSharedGuidesFrom(ownerUid)
    }
  }

  @Test
  fun invalidCurrentUidThrowsInvalidUid() = runTest {
    every { user.uid } returns "invalid uid with spaces!"

    assertThrows(MonumentSharingException.InvalidUid::class.java) {
      kotlinx.coroutines.test.runTest {
        repository.setShareAudience(guideId, ShareAudience.ALL_FRIENDS)
      }
    }
  }

  @Test
  fun invalidRecipientUidThrowsInvalidUid() = runTest {
    assertThrows(MonumentSharingException.InvalidUid::class.java) {
      kotlinx.coroutines.test.runTest {
        repository.setShareAudience(
            guideId,
            ShareAudience.SELECTED_FRIENDS,
            setOf("valid-uid", "invalid uid!"),
        )
      }
    }
    assertThrows(MonumentSharingException.InvalidUid::class.java) {
      kotlinx.coroutines.test.runTest { repository.grantAccess(guideId, setOf("invalid uid!")) }
    }
    assertThrows(MonumentSharingException.InvalidUid::class.java) {
      kotlinx.coroutines.test.runTest { repository.revokeAccess(guideId, setOf("invalid uid!")) }
    }
  }

  @Test
  fun invalidOwnerUidThrowsInvalidUid() = runTest {
    assertThrows(MonumentSharingException.InvalidUid::class.java) {
      kotlinx.coroutines.test.runTest { repository.dismissSharedGuide("invalid uid!", guideId) }
    }
    assertThrows(MonumentSharingException.InvalidUid::class.java) {
      kotlinx.coroutines.test.runTest { repository.getSharedGuidesFrom("invalid uid!") }
    }
    assertThrows(MonumentSharingException.InvalidUid::class.java) {
      repository.observeSharedGuidesFrom("invalid uid!")
    }
  }

  @Test
  fun blankGuideIdThrowsIllegalArgumentException() = runTest {
    assertThrows(IllegalArgumentException::class.java) {
      kotlinx.coroutines.test.runTest {
        repository.setShareAudience("   ", ShareAudience.ALL_FRIENDS)
      }
    }
    assertThrows(IllegalArgumentException::class.java) {
      kotlinx.coroutines.test.runTest { repository.grantAccess("", setOf("uid-alice")) }
    }
    assertThrows(IllegalArgumentException::class.java) {
      kotlinx.coroutines.test.runTest { repository.revokeAccess("  ", setOf("uid-alice")) }
    }
    assertThrows(IllegalArgumentException::class.java) {
      kotlinx.coroutines.test.runTest { repository.dismissSharedGuide(ownerUid, "") }
    }
    assertThrows(IllegalArgumentException::class.java) {
      kotlinx.coroutines.test.runTest { repository.getShareSettings("") }
    }
  }

  @Test
  fun firestoreGenericExceptionMapsToFirestoreFailure() = runTest {
    val guideRef = mockk<DocumentReference>()
    every { savedGuidesCollection.document(guideId) } returns guideRef
    every { guideRef.get() } returns Tasks.forException(IOException("Firestore down"))

    val exception =
        assertThrows(MonumentSharingException.FirestoreFailure::class.java) {
          kotlinx.coroutines.test.runTest { repository.getShareSettings(guideId) }
        }
    assertTrue(exception.cause is IOException)
  }

  @Test
  fun cancellationExceptionIsRethrownWithoutBeingWrapped() = runTest {
    val guideRef = mockk<DocumentReference>()
    every { savedGuidesCollection.document(guideId) } returns guideRef
    every { firestore.runTransaction<Unit>(any()) } answers
        {
          throw CancellationException("Cancelled")
        }

    assertThrows(CancellationException::class.java) {
      kotlinx.coroutines.test.runTest {
        repository.setShareAudience(guideId, ShareAudience.ALL_FRIENDS)
      }
    }
  }

  // endregion

  // region DefaultMonumentSharingProvider tests

  @Test
  fun defaultMonumentSharingProviderReturnsOverrideWhenProvided() {
    val customRepo = mockk<MonumentSharingRepository>()
    val provider =
        DefaultMonumentSharingProvider(override = customRepo, firestore = firestore, auth = auth)
    assertSame(customRepo, provider.repository())
  }

  @Test
  fun defaultMonumentSharingProviderCreatesFirestoreRepositoryByDefault() {
    val provider =
        DefaultMonumentSharingProvider(override = null, firestore = firestore, auth = auth)
    val repo = provider.repository()
    assertTrue(repo is FirestoreMonumentSharingRepository)
  }

  // endregion

  // region FakeMonumentSharingRepository tests

  @Test
  fun fakeRepositorySetShareAudienceUpdatesStateAndSynchronizesGuides() = runTest {
    val fakeRepo =
        FakeMonumentSharingRepository(
            initialGuidesByOwner = mapOf(ownerUid to listOf(baseGuide)),
            currentUserId = currentUid,
        )

    fakeRepo.setShareAudience(
        guideId = guideId,
        audience = ShareAudience.SELECTED_FRIENDS,
        recipientUids = setOf("uid-alice", "uid-bob"),
    )

    val settings = fakeRepo.getShareSettings(guideId)
    assertEquals(ShareAudience.SELECTED_FRIENDS, settings.audience)
    assertEquals(setOf("uid-alice", "uid-bob"), settings.sharedWithUids)

    fakeRepo.setShareAudience(guideId = guideId, audience = ShareAudience.ALL_FRIENDS)
    val allFriendsSettings = fakeRepo.getShareSettings(guideId)
    assertEquals(ShareAudience.ALL_FRIENDS, allFriendsSettings.audience)
    assertTrue(allFriendsSettings.sharedWithUids.isEmpty())

    fakeRepo.setShareAudience(guideId = guideId, audience = ShareAudience.NONE)
    val noneSettings = fakeRepo.getShareSettings(guideId)
    assertEquals(ShareAudience.NONE, noneSettings.audience)
  }

  @Test
  fun fakeRepositoryGrantAndRevokeAccess() = runTest {
    val fakeRepo =
        FakeMonumentSharingRepository(
            initialSettingsByGuideId =
                mapOf(guideId to MonumentShareSettings(audience = ShareAudience.NONE)),
            currentUserId = currentUid,
        )

    fakeRepo.grantAccess(guideId, emptySet())
    assertEquals(ShareAudience.NONE, fakeRepo.getShareSettings(guideId).audience)

    fakeRepo.grantAccess(guideId, setOf("uid-alice", "uid-bob"))
    val settingsAfterGrant = fakeRepo.getShareSettings(guideId)
    assertEquals(ShareAudience.SELECTED_FRIENDS, settingsAfterGrant.audience)
    assertEquals(setOf("uid-alice", "uid-bob"), settingsAfterGrant.sharedWithUids)

    fakeRepo.revokeAccess(guideId, emptySet())
    assertEquals(setOf("uid-alice", "uid-bob"), fakeRepo.getShareSettings(guideId).sharedWithUids)

    fakeRepo.revokeAccess(guideId, setOf("uid-alice"))
    val settingsAfterRevoke = fakeRepo.getShareSettings(guideId)
    assertEquals(setOf("uid-bob"), settingsAfterRevoke.sharedWithUids)
  }

  @Test
  fun fakeRepositoryDismissSharedGuideAndFilter() = runTest {
    val sharedGuide =
        baseGuide.copy(
            id = "g-shared",
            shareSettings =
                MonumentShareSettings(
                    audience = ShareAudience.SELECTED_FRIENDS,
                    sharedWithUids = setOf(currentUid),
                ),
        )
    val noneGuide =
        baseGuide.copy(
            id = "g-none",
            shareSettings = MonumentShareSettings(audience = ShareAudience.NONE),
        )
    val fakeRepo =
        FakeMonumentSharingRepository(
            initialGuidesByOwner = mapOf(ownerUid to listOf(sharedGuide, noneGuide)),
            currentUserId = currentUid,
        )

    val guidesBeforeDismiss = fakeRepo.getSharedGuidesFrom(ownerUid)
    assertEquals(listOf(sharedGuide), guidesBeforeDismiss)

    val observedBefore = fakeRepo.observeSharedGuidesFrom(ownerUid).first()
    assertEquals(listOf(sharedGuide), observedBefore)

    fakeRepo.dismissSharedGuide(ownerUid, "g-shared")
    val guidesAfterDismiss = fakeRepo.getSharedGuidesFrom(ownerUid)
    assertTrue(guidesAfterDismiss.isEmpty())
  }

  @Test
  fun fakeRepositoryErrorHooksThrowConfiguredExceptions() = runTest {
    val fakeRepo = FakeMonumentSharingRepository(currentUserId = currentUid)
    fakeRepo.updateError = IOException("Update failed")
    fakeRepo.dismissError = IOException("Dismiss failed")
    fakeRepo.loadError = IOException("Load failed")

    assertThrows(IOException::class.java) {
      kotlinx.coroutines.test.runTest {
        fakeRepo.setShareAudience(guideId, ShareAudience.ALL_FRIENDS)
      }
    }
    assertThrows(IOException::class.java) {
      kotlinx.coroutines.test.runTest { fakeRepo.grantAccess(guideId, setOf("uid-1")) }
    }
    assertThrows(IOException::class.java) {
      kotlinx.coroutines.test.runTest { fakeRepo.revokeAccess(guideId, setOf("uid-1")) }
    }
    assertThrows(IOException::class.java) {
      kotlinx.coroutines.test.runTest { fakeRepo.dismissSharedGuide(ownerUid, guideId) }
    }
    assertThrows(IOException::class.java) {
      kotlinx.coroutines.test.runTest { fakeRepo.getShareSettings(guideId) }
    }
    assertThrows(IOException::class.java) {
      kotlinx.coroutines.test.runTest { fakeRepo.getSharedGuidesFrom(ownerUid) }
    }
    assertThrows(IOException::class.java) {
      kotlinx.coroutines.test.runTest { fakeRepo.observeSharedGuidesFrom(ownerUid).first() }
    }
  }

  @Test
  fun fakeRepositoryValidationAndMissingGuideHandling() = runTest {
    val fakeRepo = FakeMonumentSharingRepository(currentUserId = currentUid)
    assertThrows(IllegalArgumentException::class.java) {
      kotlinx.coroutines.test.runTest {
        fakeRepo.setShareAudience("   ", ShareAudience.ALL_FRIENDS)
      }
    }
    assertThrows(MonumentSharingException.GuideNotFound::class.java) {
      kotlinx.coroutines.test.runTest { fakeRepo.getShareSettings("missing-guide") }
    }
  }

  // endregion
}
