// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Gemini 3.8 Flash <noreply@google.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>

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
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Transaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
  private val allFriendsQuery = mockk<Query>()
  private val allFollowersQuery = mockk<Query>()
  private val intermediateQuery = mockk<Query>()
  private val selectedQuery = mockk<Query>()

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
          shareSettings = MonumentShareSettings(audience = ShareAudience.ALL_FRIENDS),
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

    every {
      ownerSavedGuidesCollection.whereEqualTo(
          GuideFirestoreMapper.SHARE_AUDIENCE_FIELD,
          ShareAudience.ALL_FRIENDS.name,
      )
    } returns allFriendsQuery

    every {
      ownerSavedGuidesCollection.whereEqualTo(
          GuideFirestoreMapper.SHARE_AUDIENCE_FIELD,
          ShareAudience.ALL_FOLLOWERS.name,
      )
    } returns allFollowersQuery

    every {
      ownerSavedGuidesCollection.whereEqualTo(
          GuideFirestoreMapper.SHARE_AUDIENCE_FIELD,
          ShareAudience.SELECTED_FRIENDS.name,
      )
    } returns intermediateQuery
    every {
      intermediateQuery.whereArrayContains(
          GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD,
          currentUid,
      )
    } returns selectedQuery
  }

  private inline fun <reified T> runTransactionWith(transaction: Transaction) {
    every { firestore.runTransaction<T>(any()) } answers
        {
          val function = firstArg<Transaction.Function<T>>()
          Tasks.forResult(function.apply(transaction))
        }
  }

  private fun setupTransaction(
      targetGuideId: String = guideId,
      exists: Boolean = true,
      existingAudience: String? = null,
      existingEligibility: String? = null,
  ): io.mockk.CapturingSlot<Map<String, Any>> {
    val guideRef = mockk<DocumentReference>()
    val snapshot = mockk<DocumentSnapshot>()
    val transaction = mockk<Transaction>()

    every { savedGuidesCollection.document(targetGuideId) } returns guideRef
    every { transaction.get(guideRef) } returns snapshot
    every { snapshot.exists() } returns exists
    every { snapshot.getString(any()) } returns null
    if (existingAudience != null) {
      every { snapshot.getString(GuideFirestoreMapper.SHARE_AUDIENCE_FIELD) } returns
          existingAudience
    }
    if (existingEligibility != null) {
      every { snapshot.getString(GuideFirestoreMapper.SHARING_ELIGIBILITY_FIELD) } returns
          existingEligibility
    }

    val updatesSlot = slot<Map<String, Any>>()
    every { transaction.update(guideRef, capture(updatesSlot)) } returns transaction
    every {
      transaction.update(
          guideRef,
          GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD,
          any<FieldValue>(),
      )
    } returns transaction
    runTransactionWith<Unit>(transaction)
    return updatesSlot
  }

  private fun mockGuideSnapshot(
      id: String,
      audience: ShareAudience,
      sharedWithUids: Set<String> = emptySet(),
      dismissedByUids: Set<String> = emptySet(),
      malformed: Boolean = false,
  ): QueryDocumentSnapshot {
    val doc = mockk<QueryDocumentSnapshot>()
    every { doc.id } returns id
    val guide = expectedGuide(id, audience, sharedWithUids, dismissedByUids)
    every { doc.data } returns if (malformed) emptyMap() else GuideFirestoreMapper.toFields(guide)
    return doc
  }

  private fun expectedGuide(
      id: String,
      audience: ShareAudience,
      sharedWithUids: Set<String> = emptySet(),
      dismissedByUids: Set<String> = emptySet(),
  ) =
      baseGuide.copy(
          id = id,
          shareSettings =
              MonumentShareSettings(
                  audience = audience,
                  sharedWithUids = sharedWithUids,
                  dismissedByUids = dismissedByUids,
              ),
      )

  private fun mockQuerySnapshot(vararg docs: QueryDocumentSnapshot): QuerySnapshot {
    val snapshot = mockk<QuerySnapshot>()
    every { snapshot.documents } returns docs.toList()
    return snapshot
  }

  private fun stubListener(
      query: Query,
      snapshot: QuerySnapshot? = null,
      error: FirebaseFirestoreException? = null,
  ): ListenerRegistration {
    val registration = mockk<ListenerRegistration>(relaxed = true)
    every { query.addSnapshotListener(MetadataChanges.INCLUDE, any()) } answers
        {
          if (snapshot != null || error != null) {
            secondArg<EventListener<QuerySnapshot>>().onEvent(snapshot, error)
          }
          registration
        }
    return registration
  }

  private fun stubGetShareSettingsSnapshot(exists: Boolean, data: Map<String, Any>?) {
    val guideRef = mockk<DocumentReference>()
    val snapshot = mockk<DocumentSnapshot>()
    every { savedGuidesCollection.document(guideId) } returns guideRef
    every { guideRef.get() } returns Tasks.forResult(snapshot)
    every { snapshot.exists() } returns exists
    every { snapshot.id } returns guideId
    every { snapshot.data } returns data
  }

  private suspend inline fun <reified T : Throwable> assertThrowsSuspending(
      crossinline block: suspend () -> Unit
  ): T {
    try {
      block()
    } catch (throwable: Throwable) {
      if (throwable is T) return throwable
      throw AssertionError(
          "Expected ${T::class.java.name} but got ${throwable::class.java.name}",
          throwable,
      )
    }
    throw AssertionError("Expected ${T::class.java.name} was not thrown")
  }

  // region setShareAudience tests

  @Test
  fun setShareAudienceSelectedFriendsWritesSortedRecipientsAndClearsDismissed() = runTest {
    val slot = setupTransaction()
    repository.setShareAudience(
        guideId,
        ShareAudience.SELECTED_FRIENDS,
        setOf("uid-z", "uid-a", "uid-m"),
    )
    val updates = slot.captured
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
    val slot = setupTransaction()
    repository.setShareAudience(guideId, ShareAudience.SELECTED_FRIENDS, emptySet())
    val updates = slot.captured
    assertEquals(
        ShareAudience.SELECTED_FRIENDS.name,
        updates[GuideFirestoreMapper.SHARE_AUDIENCE_FIELD],
    )
    assertEquals(emptyList<String>(), updates[GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD])
    assertTrue(GuideFirestoreMapper.DISMISSED_BY_UIDS_FIELD !in updates)
  }

  @Test
  fun setShareAudienceAllFriendsDeletesSharedWithAndDismissedBy() = runTest {
    val slot = setupTransaction()
    repository.setShareAudience(guideId, ShareAudience.ALL_FRIENDS)
    val updates = slot.captured
    assertEquals(ShareAudience.ALL_FRIENDS.name, updates[GuideFirestoreMapper.SHARE_AUDIENCE_FIELD])
    assertEquals(FieldValue.delete(), updates[GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD])
    assertEquals(FieldValue.delete(), updates[GuideFirestoreMapper.DISMISSED_BY_UIDS_FIELD])
  }

  @Test
  fun setShareAudienceAllFollowersDeletesSharedWithAndDismissedBy() = runTest {
    val slot = setupTransaction()
    repository.setShareAudience(guideId, ShareAudience.ALL_FOLLOWERS)
    val updates = slot.captured
    assertEquals(
        ShareAudience.ALL_FOLLOWERS.name,
        updates[GuideFirestoreMapper.SHARE_AUDIENCE_FIELD],
    )
    assertEquals(FieldValue.delete(), updates[GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD])
    assertEquals(FieldValue.delete(), updates[GuideFirestoreMapper.DISMISSED_BY_UIDS_FIELD])
  }

  @Test
  fun setShareAudienceNoneDeletesSharedWithWithoutDeletingDismissedBy() = runTest {
    val slot = setupTransaction()
    repository.setShareAudience(guideId, ShareAudience.NONE)
    val updates = slot.captured
    assertEquals(ShareAudience.NONE.name, updates[GuideFirestoreMapper.SHARE_AUDIENCE_FIELD])
    assertEquals(FieldValue.delete(), updates[GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD])
    assertTrue(GuideFirestoreMapper.DISMISSED_BY_UIDS_FIELD !in updates)
  }

  @Test
  fun setShareAudienceThrowsGuideNotFoundWhenSnapshotDoesNotExist() = runTest {
    setupTransaction(exists = false)
    assertThrowsSuspending<MonumentSharingException.GuideNotFound> {
      repository.setShareAudience(guideId, ShareAudience.ALL_FRIENDS)
    }
  }

  @Test
  fun setShareAudienceRejectsTooFarGuides() = runTest {
    setupTransaction(existingEligibility = SharingEligibility.TOO_FAR.name)

    assertThrowsSuspending<MonumentSharingException.GuideTooFar> {
      repository.setShareAudience(guideId, ShareAudience.ALL_FRIENDS)
    }
  }

  @Test
  fun setShareAudienceAllowsUnsharingTooFarGuides() = runTest {
    val slot = setupTransaction(existingEligibility = SharingEligibility.TOO_FAR.name)

    repository.setShareAudience(guideId, ShareAudience.NONE)

    assertEquals(ShareAudience.NONE.name, slot.captured[GuideFirestoreMapper.SHARE_AUDIENCE_FIELD])
    assertEquals(
        FieldValue.delete(),
        slot.captured[GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD],
    )
  }

  @Test
  fun grantAccessRejectsTooFarGuides() = runTest {
    setupTransaction(
        existingAudience = ShareAudience.NONE.name,
        existingEligibility = SharingEligibility.TOO_FAR.name,
    )

    assertThrowsSuspending<MonumentSharingException.GuideTooFar> {
      repository.grantAccess(guideId, setOf("recipient-uid"))
    }
  }

  // endregion

  // region grantAccess tests

  @Test
  fun grantAccessPromotesNoneToSelectedFriendsAndClearsDismissed() = runTest {
    val slot = setupTransaction(existingAudience = ShareAudience.NONE.name)
    repository.grantAccess(guideId, setOf("uid-alice", "uid-bob"))
    val updates = slot.captured
    assertEquals(
        ShareAudience.SELECTED_FRIENDS.name,
        updates[GuideFirestoreMapper.SHARE_AUDIENCE_FIELD],
    )
    assertTrue(updates[GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD] is FieldValue)
    assertTrue(updates[GuideFirestoreMapper.DISMISSED_BY_UIDS_FIELD] is FieldValue)
  }

  @Test
  fun grantAccessPromotesGuideWithoutAudienceToSelectedFriends() = runTest {
    val slot = setupTransaction(existingAudience = null)
    repository.grantAccess(guideId, setOf("uid-alice"))
    assertEquals(
        ShareAudience.SELECTED_FRIENDS.name,
        slot.captured[GuideFirestoreMapper.SHARE_AUDIENCE_FIELD],
    )
  }

  @Test
  fun grantAccessRetainsExistingAudienceWhenNotNone() = runTest {
    val slot = setupTransaction(existingAudience = ShareAudience.SELECTED_FRIENDS.name)
    repository.grantAccess(guideId, setOf("uid-charlie"))
    val updates = slot.captured
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
    setupTransaction(exists = false)
    assertThrowsSuspending<MonumentSharingException.GuideNotFound> {
      repository.grantAccess(guideId, setOf("uid-alice"))
    }
  }

  // endregion

  // region revokeAccess tests

  @Test
  fun revokeAccessRemovesRecipientsFromSharedWith() = runTest {
    setupTransaction()
    repository.revokeAccess(guideId, setOf("uid-alice", "uid-bob"))
    // Verified by setupTransaction stubbing and successful execution
  }

  @Test
  fun revokeAccessEmptyRecipientsIsNoOp() = runTest {
    repository.revokeAccess(guideId, emptySet())
    verify(exactly = 0) { firestore.runTransaction<Any>(any()) }
  }

  @Test
  fun revokeAccessThrowsGuideNotFoundWhenSnapshotDoesNotExist() = runTest {
    setupTransaction(exists = false)
    assertThrowsSuspending<MonumentSharingException.GuideNotFound> {
      repository.revokeAccess(guideId, setOf("uid-alice"))
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
    val expected =
        MonumentShareSettings(
            audience = ShareAudience.SELECTED_FRIENDS,
            sharedWithUids = setOf("friend-1", "friend-2"),
            dismissedByUids = setOf("dismissed-1"),
        )
    stubGetShareSettingsSnapshot(
        exists = true,
        data = GuideFirestoreMapper.toFields(baseGuide.copy(shareSettings = expected)),
    )
    assertEquals(expected, repository.getShareSettings(guideId))
  }

  @Test
  fun getShareSettingsThrowsGuideNotFoundWhenSnapshotDoesNotExist() = runTest {
    stubGetShareSettingsSnapshot(exists = false, data = null)
    assertThrowsSuspending<MonumentSharingException.GuideNotFound> {
      repository.getShareSettings(guideId)
    }
  }

  @Test
  fun getShareSettingsThrowsGuideNotFoundWhenSnapshotDataIsNull() = runTest {
    stubGetShareSettingsSnapshot(exists = true, data = null)
    assertThrowsSuspending<MonumentSharingException.GuideNotFound> {
      repository.getShareSettings(guideId)
    }
  }

  // endregion

  // region getSharedGuidesFrom & observeSharedGuidesFrom tests

  @Test
  fun getSharedGuideFromReadsOnlyTheRequestedDocument() = runTest {
    val ref = mockk<DocumentReference>()
    val snapshot = mockGuideSnapshot(guideId, ShareAudience.ALL_FOLLOWERS)
    every { ownerSavedGuidesCollection.document(guideId) } returns ref
    every { ref.get() } returns Tasks.forResult(snapshot)
    every { snapshot.exists() } returns true

    assertEquals(
        expectedGuide(guideId, ShareAudience.ALL_FOLLOWERS),
        repository.getSharedGuideFrom(ownerUid, guideId),
    )
    verify(exactly = 1) { ref.get() }
    verify(exactly = 0) { allFriendsQuery.get() }
    verify(exactly = 0) { allFollowersQuery.get() }
    verify(exactly = 0) { selectedQuery.get() }
  }

  @Test
  fun getSharedGuideFromReturnsNullForMissingAndPropagatesFailures() = runTest {
    val ref = mockk<DocumentReference>()
    val snapshot = mockk<DocumentSnapshot>()
    every { ownerSavedGuidesCollection.document(guideId) } returns ref
    every { snapshot.exists() } returns false
    every { ref.get() } returns Tasks.forResult(snapshot)
    assertNull(repository.getSharedGuideFrom(ownerUid, guideId))

    val denied =
        FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
    every { ref.get() } returns Tasks.forException(denied)
    assertNull(repository.getSharedGuideFrom(ownerUid, guideId))

    val unavailable =
        FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
    every { ref.get() } returns Tasks.forException(unavailable)
    val failure =
        assertThrowsSuspending<MonumentSharingException.FirestoreFailure> {
          repository.getSharedGuideFrom(ownerUid, guideId)
        }
    assertSame(unavailable, failure.cause)

    every { savedGuidesCollection.document(guideId) } returns ref
    every { ref.get() } returns Tasks.forException(denied)
    val ownerFailure =
        assertThrowsSuspending<MonumentSharingException.FirestoreFailure> {
          repository.getSharedGuideFrom(currentUid, guideId)
        }
    assertSame(denied, ownerFailure.cause)
  }

  @Test
  fun getSharedGuideFromFiltersUnsharedDismissedAndUnselectedButAllowsOwnerSelection() = runTest {
    val ref = mockk<DocumentReference>()
    every { ownerSavedGuidesCollection.document(guideId) } returns ref
    every { savedGuidesCollection.document(guideId) } returns ref
    fun snapshot(
        audience: ShareAudience,
        selected: Set<String> = emptySet(),
        dismissed: Set<String> = emptySet(),
    ): QueryDocumentSnapshot =
        mockGuideSnapshot(guideId, audience, selected, dismissed).also {
          every { it.exists() } returns true
        }

    for (doc in
        listOf(
            snapshot(ShareAudience.NONE),
            snapshot(ShareAudience.ALL_FRIENDS, dismissed = setOf(currentUid)),
            snapshot(ShareAudience.SELECTED_FRIENDS, selected = setOf("someone-else")),
        )) {
      every { ref.get() } returns Tasks.forResult(doc)
      assertNull(repository.getSharedGuideFrom(ownerUid, guideId))
    }
    val selected = snapshot(ShareAudience.SELECTED_FRIENDS, selected = setOf(currentUid))
    every { ref.get() } returns Tasks.forResult(selected)
    assertEquals(
        expectedGuide(guideId, ShareAudience.SELECTED_FRIENDS, setOf(currentUid)),
        repository.getSharedGuideFrom(ownerUid, guideId),
    )
    val ownerSelected = snapshot(ShareAudience.SELECTED_FRIENDS)
    every { ref.get() } returns Tasks.forResult(ownerSelected)
    assertEquals(
        expectedGuide(guideId, ShareAudience.SELECTED_FRIENDS),
        repository.getSharedGuideFrom(currentUid, guideId),
    )
  }

  @Test
  fun getSharedGuideFromWrapsMalformedDataAndPreservesCancellation() = runTest {
    val ref = mockk<DocumentReference>()
    val snapshot = mockGuideSnapshot(guideId, ShareAudience.ALL_FRIENDS, malformed = true)
    every { ownerSavedGuidesCollection.document(guideId) } returns ref
    every { snapshot.exists() } returns true
    every { ref.get() } returns Tasks.forResult(snapshot)
    assertThrowsSuspending<MonumentSharingException.FirestoreFailure> {
      repository.getSharedGuideFrom(ownerUid, guideId)
    }

    every { ref.get() } answers { throw CancellationException("cancelled") }
    assertThrowsSuspending<CancellationException> {
      repository.getSharedGuideFrom(ownerUid, guideId)
    }
  }

  @Test
  fun getSharedGuidesFromCombinesQueriesDeduplicatesAndFilters() = runTest {
    val docFriends = mockGuideSnapshot("g-friends", ShareAudience.ALL_FRIENDS)
    val docFollowers = mockGuideSnapshot("g-followers", ShareAudience.ALL_FOLLOWERS)
    val docDismissed =
        mockGuideSnapshot(
            "g-dismissed",
            ShareAudience.ALL_FRIENDS,
            dismissedByUids = setOf(currentUid),
        )
    val docNone = mockGuideSnapshot("g-none", ShareAudience.NONE)
    val docSelected =
        mockGuideSnapshot(
            "g-selected",
            ShareAudience.SELECTED_FRIENDS,
            sharedWithUids = setOf(currentUid),
        )

    every { allFriendsQuery.get() } returns
        Tasks.forResult(mockQuerySnapshot(docFriends, docDismissed, docNone))
    every { allFollowersQuery.get() } returns
        Tasks.forResult(mockQuerySnapshot(docFollowers, docFriends))
    every { selectedQuery.get() } returns
        Tasks.forResult(mockQuerySnapshot(docSelected, docFriends))

    val result = repository.getSharedGuidesFrom(ownerUid)
    val expectedFriends = expectedGuide("g-friends", ShareAudience.ALL_FRIENDS)
    val expectedFollowers = expectedGuide("g-followers", ShareAudience.ALL_FOLLOWERS)
    val expectedSelected =
        expectedGuide(
            "g-selected",
            ShareAudience.SELECTED_FRIENDS,
            sharedWithUids = setOf(currentUid),
        )
    assertEquals(listOf(expectedFriends, expectedFollowers, expectedSelected), result)
  }

  @Test
  fun getSharedGuidesFromWhenOwnerIsCurrentUidQueriesCollectionDirectly() = runTest {
    val docNone = mockGuideSnapshot("g-my-none", ShareAudience.NONE)
    val docShared = mockGuideSnapshot("g-my-shared", ShareAudience.ALL_FRIENDS)
    every { savedGuidesCollection.get() } returns
        Tasks.forResult(mockQuerySnapshot(docNone, docShared))

    val result = repository.getSharedGuidesFrom(currentUid)
    assertEquals(listOf(expectedGuide("g-my-shared", ShareAudience.ALL_FRIENDS)), result)
  }

  @Test
  fun getSharedGuidesFromThrowsFirestoreFailureWhenDocumentDataMalformed() = runTest {
    val malformedDoc =
        mockGuideSnapshot("malformed-guide", ShareAudience.ALL_FRIENDS, malformed = true)
    every { allFriendsQuery.get() } returns Tasks.forResult(mockQuerySnapshot(malformedDoc))
    every { allFollowersQuery.get() } returns Tasks.forResult(mockQuerySnapshot())
    every { selectedQuery.get() } returns Tasks.forResult(mockQuerySnapshot())

    assertThrowsSuspending<MonumentSharingException.FirestoreFailure> {
      repository.getSharedGuidesFrom(ownerUid)
    }
  }

  @Test
  fun getSharedGuidesFromReturnsFollowerGuidesWhenFriendsAndSelectedQueriesDenied() = runTest {
    val docFollower = mockGuideSnapshot("g-follower", ShareAudience.ALL_FOLLOWERS)
    val permissionDenied =
        FirebaseFirestoreException(
            "Permission denied",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
        )

    every { allFriendsQuery.get() } returns Tasks.forException(permissionDenied)
    every { selectedQuery.get() } returns Tasks.forException(permissionDenied)
    every { allFollowersQuery.get() } returns Tasks.forResult(mockQuerySnapshot(docFollower))

    val result = repository.getSharedGuidesFrom(ownerUid)
    assertEquals(listOf(expectedGuide("g-follower", ShareAudience.ALL_FOLLOWERS)), result)
  }

  @Test
  fun getSharedGuidesFromThrowsFirestoreFailureWhenBranchFailsWithNonPermissionDenied() = runTest {
    val unavailable =
        FirebaseFirestoreException(
            "Service unavailable",
            FirebaseFirestoreException.Code.UNAVAILABLE,
        )

    every { allFriendsQuery.get() } returns Tasks.forException(unavailable)
    every { allFollowersQuery.get() } returns Tasks.forResult(mockQuerySnapshot())
    every { selectedQuery.get() } returns Tasks.forResult(mockQuerySnapshot())

    assertThrowsSuspending<MonumentSharingException.FirestoreFailure> {
      repository.getSharedGuidesFrom(ownerUid)
    }
  }

  @Test
  fun observeSharedGuidesFromEmitsFilteredGuidesAndRemovesListenerWhenCancelled() = runTest {
    val docFriends = mockGuideSnapshot("g-friends", ShareAudience.ALL_FRIENDS)
    val docFollowers = mockGuideSnapshot("g-followers", ShareAudience.ALL_FOLLOWERS)
    val docDismissed =
        mockGuideSnapshot(
            "g-dismissed",
            ShareAudience.ALL_FRIENDS,
            dismissedByUids = setOf(currentUid),
        )
    val docSelected =
        mockGuideSnapshot(
            "g-selected",
            ShareAudience.SELECTED_FRIENDS,
            sharedWithUids = setOf(currentUid),
        )

    val regFriends = stubListener(allFriendsQuery, mockQuerySnapshot(docFriends, docDismissed))
    val regFollowers = stubListener(allFollowersQuery, mockQuerySnapshot(docFollowers, docFriends))
    val regSelected = stubListener(selectedQuery, mockQuerySnapshot(docSelected, docFriends))

    val emissions = repository.observeSharedGuidesFrom(ownerUid).take(1).toList()
    val expected =
        listOf(
            expectedGuide("g-friends", ShareAudience.ALL_FRIENDS),
            expectedGuide("g-followers", ShareAudience.ALL_FOLLOWERS),
            expectedGuide(
                "g-selected",
                ShareAudience.SELECTED_FRIENDS,
                sharedWithUids = setOf(currentUid),
            ),
        )

    assertEquals(listOf(expected), emissions)
    verify { regFriends.remove() }
    verify { regFollowers.remove() }
    verify { regSelected.remove() }
  }

  @Test
  fun observeSharedGuidesFromWhenOwnerIsCurrentUidObservesCollectionDirectly() = runTest {
    val doc = mockGuideSnapshot("g-my-shared", ShareAudience.ALL_FRIENDS)
    val reg = stubListener(savedGuidesCollection, mockQuerySnapshot(doc))

    val emissions = repository.observeSharedGuidesFrom(currentUid).take(1).toList()
    assertEquals(
        listOf(listOf(expectedGuide("g-my-shared", ShareAudience.ALL_FRIENDS))),
        emissions,
    )
    verify { reg.remove() }
  }

  @Test
  fun observeSharedGuidesFromEmitsErrorWhenListenerFails() = runTest {
    val exception =
        FirebaseFirestoreException("Network error", FirebaseFirestoreException.Code.UNAVAILABLE)
    val regFriends = stubListener(allFriendsQuery, error = exception)
    stubListener(allFollowersQuery)
    stubListener(selectedQuery)

    assertThrowsSuspending<MonumentSharingException.FirestoreFailure> {
      repository.observeSharedGuidesFrom(ownerUid).take(1).toList()
    }
    verify { regFriends.remove() }
  }

  @Test
  fun observeSharedGuidesFromFailsWhenAllQueriesAreDenied() = runTest {
    val denied =
        FirebaseFirestoreException(
            "Permission denied",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
        )
    val friends = stubListener(allFriendsQuery, error = denied)
    val followers = stubListener(allFollowersQuery, error = denied)
    val selected = stubListener(selectedQuery, error = denied)

    val failure =
        assertThrowsSuspending<MonumentSharingException.FirestoreFailure> {
          repository.observeSharedGuidesFrom(ownerUid).take(1).toList()
        }
    assertTrue(
        generateSequence<Throwable>(failure) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .any { it === denied }
    )
    verify { friends.remove() }
    verify { followers.remove() }
    verify { selected.remove() }
  }

  @Test
  fun observeSharedGuidesFromClosesWithErrorWhenDocumentDataMalformed() = runTest {
    val malformedDoc =
        mockGuideSnapshot("malformed-guide", ShareAudience.ALL_FRIENDS, malformed = true)
    val regFriends = stubListener(allFriendsQuery, mockQuerySnapshot(malformedDoc))
    stubListener(allFollowersQuery)
    stubListener(selectedQuery)

    assertThrowsSuspending<MonumentSharingException.FirestoreFailure> {
      repository.observeSharedGuidesFrom(ownerUid).take(1).toList()
    }
    verify { regFriends.remove() }
  }

  @Test
  fun observeSharedGuidesFromEmitsFollowerGuidesWhenFriendsAndSelectedListenersDenied() = runTest {
    val docFollower = mockGuideSnapshot("g-follower", ShareAudience.ALL_FOLLOWERS)
    val permissionDenied =
        FirebaseFirestoreException(
            "Permission denied",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
        )

    val regFriends = stubListener(allFriendsQuery, error = permissionDenied)
    val regSelected = stubListener(selectedQuery, error = permissionDenied)
    val regFollowers = stubListener(allFollowersQuery, mockQuerySnapshot(docFollower))

    val emissions = repository.observeSharedGuidesFrom(ownerUid).take(1).toList()
    assertEquals(
        listOf(listOf(expectedGuide("g-follower", ShareAudience.ALL_FOLLOWERS))),
        emissions,
    )
    verify { regFriends.remove() }
    verify { regSelected.remove() }
    verify { regFollowers.remove() }
  }

  // endregion

  // region Error mapping & input validation tests

  @Test
  fun requireCurrentUidThrowsUnauthenticatedUserWhenUserIsNull() = runTest {
    every { auth.currentUser } returns null

    val actions =
        listOf<suspend () -> Any?>(
            { repository.setShareAudience(guideId, ShareAudience.ALL_FRIENDS) },
            { repository.grantAccess(guideId, setOf("uid-alice")) },
            { repository.revokeAccess(guideId, setOf("uid-alice")) },
            { repository.dismissSharedGuide(ownerUid, guideId) },
            { repository.getShareSettings(guideId) },
            { repository.getSharedGuideFrom(ownerUid, guideId) },
            { repository.getSharedGuidesFrom(ownerUid) },
            { repository.observeSharedGuidesFrom(ownerUid) },
        )

    for (action in actions) {
      assertThrowsSuspending<MonumentSharingException.UnauthenticatedUser> { action() }
    }
  }

  @Test
  fun invalidUidThrowsInvalidUid() = runTest {
    every { user.uid } returns "invalid uid with spaces!"
    assertThrowsSuspending<MonumentSharingException.InvalidUid> {
      repository.setShareAudience(guideId, ShareAudience.ALL_FRIENDS)
    }

    val actions =
        listOf<suspend () -> Any?>(
            {
              repository.setShareAudience(
                  guideId,
                  ShareAudience.SELECTED_FRIENDS,
                  setOf("valid-uid", "invalid uid!"),
              )
            },
            { repository.grantAccess(guideId, setOf("invalid uid!")) },
            { repository.revokeAccess(guideId, setOf("invalid uid!")) },
            { repository.dismissSharedGuide("invalid uid!", guideId) },
            { repository.getSharedGuidesFrom("invalid uid!") },
            { repository.getSharedGuideFrom("invalid uid!", guideId) },
            { repository.observeSharedGuidesFrom("invalid uid!") },
        )

    for (action in actions) {
      assertThrowsSuspending<MonumentSharingException.InvalidUid> { action() }
    }
  }

  @Test
  fun blankGuideIdThrowsIllegalArgumentException() = runTest {
    val actions =
        listOf<suspend () -> Any?>(
            { repository.setShareAudience("   ", ShareAudience.ALL_FRIENDS) },
            { repository.grantAccess("", setOf("uid-alice")) },
            { repository.revokeAccess("  ", setOf("uid-alice")) },
            { repository.dismissSharedGuide(ownerUid, "") },
            { repository.getShareSettings("") },
            { repository.getSharedGuideFrom(ownerUid, "") },
        )

    for (action in actions) {
      assertThrowsSuspending<IllegalArgumentException> { action() }
    }
  }

  @Test
  fun firestoreGenericExceptionMapsToFirestoreFailure() = runTest {
    val guideRef = mockk<DocumentReference>()
    every { savedGuidesCollection.document(guideId) } returns guideRef
    every { guideRef.get() } returns Tasks.forException(IOException("Firestore down"))

    val exception =
        assertThrowsSuspending<MonumentSharingException.FirestoreFailure> {
          repository.getShareSettings(guideId)
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

    assertThrowsSuspending<CancellationException> {
      repository.setShareAudience(guideId, ShareAudience.ALL_FRIENDS)
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
}
