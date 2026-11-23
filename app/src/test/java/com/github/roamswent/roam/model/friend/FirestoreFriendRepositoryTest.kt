// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Gemini 4 Neon <noreply@google.com>

package com.github.roamswent.roam.model.friend

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Transaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FirestoreFriendRepositoryTest {
  private val firestore = mockk<FirebaseFirestore>()
  private val auth = mockk<FirebaseAuth>()
  private val user = mockk<FirebaseUser>()
  private val profiles = mockk<CollectionReference>()
  private val friendships = mockk<CollectionReference>()
  private val users = mockk<CollectionReference>()
  private val follows = mockk<CollectionReference>()
  private val recipientProfile = mockk<DocumentReference>()
  private val repository = FirestoreFriendRepository(firestore, auth)

  @Before
  fun setUp() {
    every { auth.currentUser } returns user
    every { user.uid } returns "user-a"
    every { firestore.collection("publicProfiles") } returns profiles
    every { firestore.collection("friendships") } returns friendships
    every { firestore.collection("users") } returns users
    every { firestore.collection("follows") } returns follows
    every { profiles.document("user-b") } returns recipientProfile
  }

  @Test
  fun sendFriendRequest_rejectsSelfRequest() = runTest {
    val error = captureFailure { repository.sendFriendRequest("user-a") }

    assertTrue(error is FriendRepositoryException.SelfFriendRequest)
    verify(exactly = 0) { firestore.collection("friendships") }
  }

  @Test
  fun sendFriendRequest_requiresRecipientPublicProfile() = runTest {
    val requestReference = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)
    val requestSnapshot = mockk<DocumentSnapshot>()
    val profileSnapshot = mockk<DocumentSnapshot>()
    every { friendships.document("user-a~user-b") } returns requestReference
    every { transaction.get(requestReference) } returns requestSnapshot
    every { transaction.get(recipientProfile) } returns profileSnapshot
    every { requestSnapshot.exists() } returns false
    every { profileSnapshot.exists() } returns false
    runTransactionWith<FriendRequest>(transaction)

    val error = captureFailure { repository.sendFriendRequest("user-b") }

    assertTrue(error is FriendRepositoryException.RecipientProfileNotFound)
    verify(exactly = 0) { transaction.set(any(), any<Map<String, Any>>()) }
  }

  @Test
  fun sendFriendRequest_createsPendingPairForRegisteredRecipient() = runTest {
    val requestReference = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)
    val requestSnapshot = mockk<DocumentSnapshot>()
    val profileSnapshot = mockk<DocumentSnapshot>()
    every { friendships.document("user-a~user-b") } returns requestReference
    every { requestReference.id } returns "user-a~user-b"
    every { transaction.get(requestReference) } returns requestSnapshot
    every { transaction.get(recipientProfile) } returns profileSnapshot
    every { requestSnapshot.exists() } returns false
    every { profileSnapshot.exists() } returns true
    every { profileSnapshot.getBoolean("allowFriendRequests") } returns null
    runTransactionWith<FriendRequest>(transaction)

    val result = repository.sendFriendRequest("user-b")

    assertEquals(FriendRequest("user-a~user-b", "user-a", "user-b"), result)
    verify {
      transaction.set(
          requestReference,
          mapOf(
              "participantUids" to listOf("user-a", "user-b"),
              "requesterUid" to "user-a",
              "recipientUid" to "user-b",
              "status" to "PENDING",
          ),
      )
    }
  }

  @Test
  fun sendFriendRequest_returnsExistingOutgoingRequestWithoutWritingAgain() = runTest {
    val requestReference = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)
    val requestSnapshot = recordSnapshot("user-a~user-b", "user-a", "user-b", "PENDING")
    val profileSnapshot = mockk<DocumentSnapshot>()
    every { friendships.document("user-a~user-b") } returns requestReference
    every { transaction.get(requestReference) } returns requestSnapshot
    every { transaction.get(recipientProfile) } returns profileSnapshot
    every { profileSnapshot.exists() } returns true
    runTransactionWith<FriendRequest>(transaction)

    val result = repository.sendFriendRequest("user-b")

    assertEquals(FriendRequest("user-a~user-b", "user-a", "user-b"), result)
    verify(exactly = 0) { transaction.set(any(), any<Map<String, Any>>()) }
  }

  @Test
  fun sendFriendRequest_doesNotAutoAcceptReciprocalPendingRequest() = runTest {
    val requestReference = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)
    val requestSnapshot = recordSnapshot("user-a~user-b", "user-b", "user-a", "PENDING")
    val profileSnapshot = mockk<DocumentSnapshot>()
    every { friendships.document("user-a~user-b") } returns requestReference
    every { transaction.get(requestReference) } returns requestSnapshot
    every { transaction.get(recipientProfile) } returns profileSnapshot
    every { profileSnapshot.exists() } returns true
    runTransactionWith<FriendRequest>(transaction)

    val error = captureFailure { repository.sendFriendRequest("user-b") }

    assertTrue(error is FriendRepositoryException.IncomingRequestPending)
    verify(exactly = 0) { transaction.update(any(), any<String>(), any<String>()) }
  }

  @Test
  fun acceptFriendRequest_allowsOnlyTheRecipient() = runTest {
    val requestReference = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)
    every { auth.currentUser } returns user
    every { user.uid } returns "user-b"
    every { friendships.document("user-a~user-b") } returns requestReference
    every { transaction.get(requestReference) } returns
        recordSnapshot("user-a~user-b", "user-a", "user-b", "PENDING")
    runTransactionWith<Unit>(transaction)

    repository.acceptFriendRequest("user-a~user-b")

    verify { transaction.update(requestReference, "status", "ACCEPTED") }
  }

  @Test
  fun acceptFriendRequest_rejectsTheRequester() = runTest {
    val requestReference = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)
    every { friendships.document("user-a~user-b") } returns requestReference
    every { transaction.get(requestReference) } returns
        recordSnapshot("user-a~user-b", "user-a", "user-b", "PENDING")
    runTransactionWith<Unit>(transaction)

    val error = captureFailure { repository.acceptFriendRequest("user-a~user-b") }

    assertTrue(error is FriendRepositoryException.NotRequestRecipient)
    verify(exactly = 0) { transaction.update(any(), any<String>(), any<String>()) }
  }

  @Test
  fun rejectAndCancel_requireTheCorrectParticipant() = runTest {
    val requestReference = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)
    every { friendships.document("user-a~user-b") } returns requestReference
    every { transaction.get(requestReference) } returns
        recordSnapshot("user-a~user-b", "user-a", "user-b", "PENDING")
    runTransactionWith<Unit>(transaction)

    val rejectError = captureFailure { repository.rejectFriendRequest("user-a~user-b") }

    assertTrue(rejectError is FriendRepositoryException.NotRequestRecipient)
    every { auth.currentUser } returns mockk<FirebaseUser> { every { uid } returns "user-b" }

    val cancelError = captureFailure { repository.cancelFriendRequest("user-a~user-b") }

    assertTrue(cancelError is FriendRepositoryException.NotRequestSender)
    verify(exactly = 0) { transaction.delete(any()) }
  }

  @Test
  fun recipientCanRejectAndRequesterCanCancelPendingRequest() = runTest {
    val requestReference = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)
    every { friendships.document("user-a~user-b") } returns requestReference
    every { transaction.get(requestReference) } returns
        recordSnapshot("user-a~user-b", "user-a", "user-b", "PENDING")
    runTransactionWith<Unit>(transaction)

    every { auth.currentUser } returns mockk<FirebaseUser> { every { uid } returns "user-b" }
    repository.rejectFriendRequest("user-a~user-b")
    verify(exactly = 1) { transaction.delete(requestReference) }

    every { auth.currentUser } returns user
    repository.cancelFriendRequest("user-a~user-b")
    verify(exactly = 2) { transaction.delete(requestReference) }
  }

  @Test
  fun relationshipListsPartitionPendingAndAcceptedPairs() = runTest {
    val query = mockk<Query>()
    val snapshot = mockk<QuerySnapshot>()
    val incoming = queryRecord("user-a~user-b", "user-b", "user-a", "PENDING")
    val outgoing = queryRecord("user-a~user-c", "user-a", "user-c", "PENDING")
    val acceptedFromOther = queryRecord("user-a~user-d", "user-d", "user-a", "ACCEPTED")
    val acceptedFromSelf = queryRecord("user-a~user-e", "user-a", "user-e", "ACCEPTED")
    every { friendships.whereArrayContains("participantUids", "user-a") } returns query
    every { query.get() } returns Tasks.forResult(snapshot)
    every { snapshot.documents } returns
        listOf(incoming, outgoing, acceptedFromOther, acceptedFromSelf)

    assertEquals(
        listOf(FriendRequest("user-a~user-b", "user-b", "user-a")),
        repository.getIncomingRequests(),
    )
    assertEquals(
        listOf(FriendRequest("user-a~user-c", "user-a", "user-c")),
        repository.getOutgoingRequests(),
    )
    assertEquals(
        listOf(Friendship("user-a~user-d", "user-d"), Friendship("user-a~user-e", "user-e")),
        repository.getFriends(),
    )
  }

  @Test
  fun removeFriend_deletesAcceptedFriendship() = runTest {
    val requestReference = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)
    every { friendships.document("user-a~user-b") } returns requestReference
    every { transaction.get(requestReference) } returns
        recordSnapshot("user-a~user-b", "user-a", "user-b", "ACCEPTED")
    runTransactionWith<Unit>(transaction)

    repository.removeFriend("user-b")

    verify { transaction.delete(requestReference) }
  }

  @Test
  fun removeFriend_doesNotDeletePendingRequest() = runTest {
    val requestReference = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)
    every { friendships.document("user-a~user-b") } returns requestReference
    every { transaction.get(requestReference) } returns
        recordSnapshot("user-a~user-b", "user-a", "user-b", "PENDING")
    runTransactionWith<Unit>(transaction)

    val error = captureFailure { repository.removeFriend("user-b") }

    assertTrue(error is FriendRepositoryException.FriendshipNotFound)
    verify(exactly = 0) { transaction.delete(any()) }
  }

  @Test
  fun firestoreFailuresAreDistinguishable() = runTest {
    val failure = IllegalStateException("Firestore unavailable")
    every { firestore.runTransaction<Unit>(any()) } returns Tasks.forException(failure)

    val error = captureFailure { repository.acceptFriendRequest("user-a~user-b") }

    assertTrue(error is FriendRepositoryException.FirestoreFailure)
    assertNotNull(error.cause)
  }

  @Test
  fun sendFriendRequest_withoutAuthenticatedUserReturnsTypedError() = runTest {
    every { auth.currentUser } returns null

    val error = captureFailure { repository.sendFriendRequest("user-b") }

    assertTrue(error is FriendRepositoryException.UnauthenticatedUser)
    verify(exactly = 0) { firestore.collection("friendships") }
  }

  @Test
  fun sendFriendRequest_invalidUidReturnsTypedError() = runTest {
    val error = captureFailure { repository.sendFriendRequest("invalid/uid") }

    assertTrue(error is FriendRepositoryException.InvalidUid)
    verify(exactly = 0) { firestore.collection("friendships") }
  }

  @Test
  fun sendFriendRequest_alreadyFriendsReturnsTypedError() = runTest {
    val requestReference = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)

    every { friendships.document("user-a~user-b") } returns requestReference
    every { transaction.get(requestReference) } returns
        recordSnapshot("user-a~user-b", "user-a", "user-b", "ACCEPTED")
    every { transaction.get(recipientProfile) } returns mockk<DocumentSnapshot>()

    runTransactionWith<FriendRequest>(transaction)

    val error = captureFailure { repository.sendFriendRequest("user-b") }

    assertTrue(error is FriendRepositoryException.AlreadyFriends)
    verify(exactly = 0) { transaction.set(any(), any<Map<String, Any>>()) }
  }

  @Test
  fun acceptFriendRequest_missingRequestReturnsTypedError() = runTest {
    val requestReference = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)
    val snapshot = mockk<DocumentSnapshot>()

    every { friendships.document("user-a~user-b") } returns requestReference
    every { transaction.get(requestReference) } returns snapshot
    every { snapshot.exists() } returns false

    runTransactionWith<Unit>(transaction)

    val error = captureFailure { repository.acceptFriendRequest("user-a~user-b") }

    assertTrue(error is FriendRepositoryException.RequestNotFound)
    verify(exactly = 0) { transaction.update(any(), any<String>(), any<String>()) }
  }

  @Test
  fun acceptFriendRequest_alreadyAcceptedDoesNotUpdateAgain() = runTest {
    val requestReference = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)

    every { auth.currentUser } returns mockk<FirebaseUser> { every { uid } returns "user-b" }
    every { friendships.document("user-a~user-b") } returns requestReference
    every { transaction.get(requestReference) } returns
        recordSnapshot("user-a~user-b", "user-a", "user-b", "ACCEPTED")

    runTransactionWith<Unit>(transaction)

    repository.acceptFriendRequest("user-a~user-b")

    verify(exactly = 0) { transaction.update(any(), any<String>(), any<String>()) }
  }

  @Test
  fun rejectFriendRequest_cannotRejectAcceptedFriendship() = runTest {
    val requestReference = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)

    every { auth.currentUser } returns mockk<FirebaseUser> { every { uid } returns "user-b" }
    every { friendships.document("user-a~user-b") } returns requestReference
    every { transaction.get(requestReference) } returns
        recordSnapshot("user-a~user-b", "user-a", "user-b", "ACCEPTED")

    runTransactionWith<Unit>(transaction)

    val error = captureFailure { repository.rejectFriendRequest("user-a~user-b") }

    assertTrue(error is FriendRepositoryException.RequestNotPending)
    verify(exactly = 0) { transaction.delete(any()) }
  }

  @Test
  fun acceptFriendRequest_invalidRequestIdReturnsTypedError() = runTest {
    for (requestId in listOf("invalid", "user-b~user-a", "a~b~c")) {
      val error = captureFailure { repository.acceptFriendRequest(requestId) }

      assertTrue(
          "Expected RequestNotFound for $requestId",
          error is FriendRepositoryException.RequestNotFound,
      )
    }
  }

  @Test
  fun removeFriend_missingFriendshipReturnsTypedError() = runTest {
    val requestReference = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)
    val snapshot = mockk<DocumentSnapshot>()

    every { friendships.document("user-a~user-b") } returns requestReference
    every { transaction.get(requestReference) } returns snapshot
    every { snapshot.exists() } returns false

    runTransactionWith<Unit>(transaction)

    val error = captureFailure { repository.removeFriend("user-b") }

    assertTrue(error is FriendRepositoryException.FriendshipNotFound)
    verify(exactly = 0) { transaction.delete(any()) }
  }

  @Test
  fun getFriends_missingRequesterUidReturnsFirestoreFailure() = runTest {
    val query = mockk<Query>()
    val snapshot = mockk<QuerySnapshot>()
    val record =
        recordSnapshot(
            "user-a~user-b",
            "user-a",
            "user-b",
            "ACCEPTED",
        )

    every { record.getString("requesterUid") } returns null
    every { friendships.whereArrayContains("participantUids", "user-a") } returns query
    every { query.get() } returns Tasks.forResult(snapshot)
    every { snapshot.documents } returns listOf(record)

    val error = captureFailure { repository.getFriends() }

    assertTrue(error is FriendRepositoryException.FirestoreFailure)
    assertTrue(error.cause is IllegalStateException)
  }

  @Test
  fun getFriends_rejectsMalformedRecords() = runTest {
    val query = mockk<Query>()
    val snapshot = mockk<QuerySnapshot>()

    every { friendships.whereArrayContains("participantUids", "user-a") } returns query
    every { query.get() } returns Tasks.forResult(snapshot)

    fun validRecord() =
        recordSnapshot(
            "user-a~user-b",
            "user-a",
            "user-b",
            "ACCEPTED",
        )

    val invalidRecords =
        listOf(
            "missing recipient" to
                validRecord().apply { every { getString("recipientUid") } returns null },
            "missing status" to validRecord().apply { every { getString("status") } returns null },
            "missing participants" to validRecord().apply { every { data } returns emptyMap() },
            "non-string participant" to
                validRecord().apply {
                  every { data } returns mapOf("participantUids" to listOf("user-a", 123))
                },
            "wrong participant count" to
                validRecord().apply {
                  every { data } returns mapOf("participantUids" to listOf("user-a"))
                },
            "unsorted participants" to
                validRecord().apply {
                  every { data } returns mapOf("participantUids" to listOf("user-b", "user-a"))
                },
            "invalid participant UID" to
                validRecord().apply {
                  every { data } returns mapOf("participantUids" to listOf("user-a", "user/b"))
                },
            "same requester and recipient" to
                recordSnapshot(
                    "user-a~user-b",
                    "user-a",
                    "user-a",
                    "ACCEPTED",
                ),
            "requester not in participants" to
                validRecord().apply {
                  every { data } returns mapOf("participantUids" to listOf("user-b", "user-c"))
                },
            "recipient not in participants" to
                validRecord().apply {
                  every { data } returns mapOf("participantUids" to listOf("user-a", "user-c"))
                },
            "wrong document ID" to
                recordSnapshot(
                    "user-a~user-c",
                    "user-a",
                    "user-b",
                    "ACCEPTED",
                ),
            "unsupported status" to
                recordSnapshot(
                    "user-a~user-b",
                    "user-a",
                    "user-b",
                    "REJECTED",
                ),
        )

    for ((description, record) in invalidRecords) {
      every { snapshot.documents } returns listOf(record)

      val error = captureFailure { repository.getFriends() }

      assertTrue(
          "Failed case: $description",
          error is FriendRepositoryException.FirestoreFailure,
      )
    }
  }

  @Test
  fun sendFriendRequest_throwsFriendRequestsDisabledWhenRecipientDisablesRequests() = runTest {
    val requestReference = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)
    every { friendships.document("user-a~user-b") } returns requestReference
    every { transaction.get(requestReference) } returns existenceSnapshot(false)
    every { transaction.get(recipientProfile) } returns
        recipientProfileSnapshot(exists = true, allowFriendRequests = false)
    runTransactionWith<FriendRequest>(transaction)

    val error = captureFailure { repository.sendFriendRequest("user-b") }

    assertTrue(error is FriendRepositoryException.FriendRequestsDisabled)
    verify(exactly = 0) { transaction.set(any(), any<Map<String, Any>>()) }
  }

  @Test
  fun sendFriendRequest_succeedsWhenRecipientExplicitlyAllowsRequests() = runTest {
    val requestReference = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)
    every { friendships.document("user-a~user-b") } returns requestReference
    every { requestReference.id } returns "user-a~user-b"
    every { transaction.get(requestReference) } returns existenceSnapshot(false)
    every { transaction.get(recipientProfile) } returns
        recipientProfileSnapshot(exists = true, allowFriendRequests = true)
    runTransactionWith<FriendRequest>(transaction)

    val result = repository.sendFriendRequest("user-b")

    assertEquals(FriendRequest("user-a~user-b", "user-a", "user-b"), result)
    verify(exactly = 1) { transaction.set(requestReference, any<Map<String, Any>>()) }
  }

  @Test
  fun sendFriendRequest_mapsPermissionDeniedToBlockedByUser() = runTest {
    every { friendships.document("user-a~user-b") } returns mockk()
    val denied =
        FirebaseFirestoreException(
            "Permission denied",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
        )
    every { firestore.runTransaction<FriendRequest>(any()) } returns Tasks.forException(denied)

    val error = captureFailure { repository.sendFriendRequest("user-b") }

    assertTrue(error is FriendRepositoryException.BlockedByUser)
  }

  @Test
  fun sendFriendRequest_mapsOtherFirestoreAndGenericExceptionsToFirestoreFailure() = runTest {
    every { friendships.document("user-a~user-b") } returns mockk()
    val unavailable =
        FirebaseFirestoreException("Unavailable", FirebaseFirestoreException.Code.UNAVAILABLE)
    every { firestore.runTransaction<FriendRequest>(any()) } returns Tasks.forException(unavailable)

    val firestoreError = captureFailure { repository.sendFriendRequest("user-b") }
    assertTrue(firestoreError is FriendRepositoryException.FirestoreFailure)
    assertTrue(firestoreError.cause === unavailable)

    val generic = IllegalStateException("Unexpected")
    every { firestore.runTransaction<FriendRequest>(any()) } returns Tasks.forException(generic)

    val genericError = captureFailure { repository.sendFriendRequest("user-b") }
    assertTrue(genericError is FriendRepositoryException.FirestoreFailure)
    assertTrue(genericError.cause === generic)
  }

  @Test
  fun sendFriendRequest_propagatesCancellationException() = runTest {
    every { friendships.document("user-a~user-b") } returns mockk()
    every { firestore.runTransaction<FriendRequest>(any()) } returns Tasks.forCanceled()

    val error = captureFailure { repository.sendFriendRequest("user-b") }

    assertTrue(error is CancellationException)
  }

  @Test
  fun rejectFriendRequest_withBlacklistTrueAndIncomingFollowDeletesRequestFollowAndBlocks() =
      runTest {
        every { auth.currentUser } returns mockk<FirebaseUser> { every { uid } returns "user-b" }
        val requestRef = mockk<DocumentReference>()
        val followRef = stubIncomingFollowRef("user-b", "user-a")
        val blockedRef = stubBlockedUserRef("user-b", "user-a")
        val transaction = mockk<Transaction>(relaxed = true)
        every { friendships.document("user-a~user-b") } returns requestRef
        every { transaction.get(requestRef) } returns
            recordSnapshot("user-a~user-b", "user-a", "user-b", "PENDING")
        every { transaction.get(followRef) } returns existenceSnapshot(true)
        runTransactionWith<Unit>(transaction)

        repository.rejectFriendRequest("user-a~user-b", blacklist = true)

        verify { transaction.delete(requestRef) }
        verify { transaction.set(blockedRef, mapOf("blockedUid" to "user-a")) }
        verify { transaction.delete(followRef) }
      }

  @Test
  fun rejectFriendRequest_withBlacklistTrueAndNoIncomingFollowSkipsFollowDelete() = runTest {
    every { auth.currentUser } returns mockk<FirebaseUser> { every { uid } returns "user-b" }
    val requestRef = mockk<DocumentReference>()
    val followRef = stubIncomingFollowRef("user-b", "user-a")
    val blockedRef = stubBlockedUserRef("user-b", "user-a")
    val transaction = mockk<Transaction>(relaxed = true)
    every { friendships.document("user-a~user-b") } returns requestRef
    every { transaction.get(requestRef) } returns
        recordSnapshot("user-a~user-b", "user-a", "user-b", "PENDING")
    every { transaction.get(followRef) } returns existenceSnapshot(false)
    runTransactionWith<Unit>(transaction)

    repository.rejectFriendRequest("user-a~user-b", blacklist = true)

    verify { transaction.delete(requestRef) }
    verify { transaction.set(blockedRef, mapOf("blockedUid" to "user-a")) }
    verify(exactly = 0) { transaction.delete(followRef) }
  }

  @Test
  fun rejectFriendRequest_withDefaultBlacklistFalseDoesNotTouchBlockedUsersOrFollows() = runTest {
    every { auth.currentUser } returns mockk<FirebaseUser> { every { uid } returns "user-b" }
    val requestRef = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)
    every { friendships.document("user-a~user-b") } returns requestRef
    every { transaction.get(requestRef) } returns
        recordSnapshot("user-a~user-b", "user-a", "user-b", "PENDING")
    runTransactionWith<Unit>(transaction)

    repository.rejectFriendRequest("user-a~user-b")

    verify(exactly = 1) { transaction.delete(requestRef) }
    verify(exactly = 0) { transaction.set(any(), any<Map<String, Any>>()) }
    verify(exactly = 0) { firestore.collection("users") }
    verify(exactly = 0) { firestore.collection("follows") }
  }

  @Test
  fun rejectFriendRequest_withBlacklistTrueValidatesRequestExistenceStatusAndRecipient() = runTest {
    val requestRef = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)
    every { friendships.document("user-a~user-b") } returns requestRef
    runTransactionWith<Unit>(transaction)

    every { transaction.get(requestRef) } returns existenceSnapshot(false)
    assertTrue(
        captureFailure { repository.rejectFriendRequest("user-a~user-b", blacklist = true) }
            is FriendRepositoryException.RequestNotFound
    )

    every { transaction.get(requestRef) } returns
        recordSnapshot("user-a~user-b", "user-a", "user-b", "ACCEPTED")
    assertTrue(
        captureFailure { repository.rejectFriendRequest("user-a~user-b", blacklist = true) }
            is FriendRepositoryException.RequestNotPending
    )

    every { transaction.get(requestRef) } returns
        recordSnapshot("user-a~user-b", "user-a", "user-b", "PENDING")
    assertTrue(
        captureFailure { repository.rejectFriendRequest("user-a~user-b", blacklist = true) }
            is FriendRepositoryException.NotRequestRecipient
    )
    verify(exactly = 0) { transaction.delete(any()) }
    verify(exactly = 0) { transaction.set(any(), any<Map<String, Any>>()) }
  }

  @Test
  fun removeFriend_withBlacklistTrueAndIncomingFollowDeletesFriendshipFollowAndBlocks() = runTest {
    val friendshipRef = mockk<DocumentReference>()
    val followRef = stubIncomingFollowRef("user-a", "user-b")
    val blockedRef = stubBlockedUserRef("user-a", "user-b")
    val transaction = mockk<Transaction>(relaxed = true)
    every { friendships.document("user-a~user-b") } returns friendshipRef
    every { transaction.get(friendshipRef) } returns
        recordSnapshot("user-a~user-b", "user-a", "user-b", "ACCEPTED")
    every { transaction.get(followRef) } returns existenceSnapshot(true)
    runTransactionWith<Unit>(transaction)

    repository.removeFriend("user-b", blacklist = true)

    verify { transaction.delete(friendshipRef) }
    verify { transaction.set(blockedRef, mapOf("blockedUid" to "user-b")) }
    verify { transaction.delete(followRef) }
  }

  @Test
  fun removeFriend_withBlacklistTrueAndNoIncomingFollowSkipsFollowDelete() = runTest {
    val friendshipRef = mockk<DocumentReference>()
    val followRef = stubIncomingFollowRef("user-a", "user-b")
    val blockedRef = stubBlockedUserRef("user-a", "user-b")
    val transaction = mockk<Transaction>(relaxed = true)
    every { friendships.document("user-a~user-b") } returns friendshipRef
    every { transaction.get(friendshipRef) } returns
        recordSnapshot("user-a~user-b", "user-a", "user-b", "ACCEPTED")
    every { transaction.get(followRef) } returns existenceSnapshot(false)
    runTransactionWith<Unit>(transaction)

    repository.removeFriend("user-b", blacklist = true)

    verify { transaction.delete(friendshipRef) }
    verify { transaction.set(blockedRef, mapOf("blockedUid" to "user-b")) }
    verify(exactly = 0) { transaction.delete(followRef) }
  }

  @Test
  fun removeFriend_withDefaultBlacklistFalseDoesNotTouchBlockedUsersOrFollows() = runTest {
    val friendshipRef = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)
    every { friendships.document("user-a~user-b") } returns friendshipRef
    every { transaction.get(friendshipRef) } returns
        recordSnapshot("user-a~user-b", "user-a", "user-b", "ACCEPTED")
    runTransactionWith<Unit>(transaction)

    repository.removeFriend("user-b")

    verify(exactly = 1) { transaction.delete(friendshipRef) }
    verify(exactly = 0) { transaction.set(any(), any<Map<String, Any>>()) }
    verify(exactly = 0) { firestore.collection("users") }
    verify(exactly = 0) { firestore.collection("follows") }
  }

  @Test
  fun removeFriend_rejectsSelfUid() = runTest {
    val error = captureFailure { repository.removeFriend("user-a") }

    assertTrue(error is FriendRepositoryException.SelfFriendRequest)
  }

  @Test
  fun blockUser_writesBlockAndDeletesExistingFriendshipAndFollow() = runTest {
    val friendshipRef = mockk<DocumentReference>()
    val followRef = stubIncomingFollowRef("user-a", "user-b")
    val blockedRef = stubBlockedUserRef("user-a", "user-b")
    val transaction = mockk<Transaction>(relaxed = true)
    every { friendships.document("user-a~user-b") } returns friendshipRef
    every { transaction.get(friendshipRef) } returns existenceSnapshot(true)
    every { transaction.get(followRef) } returns existenceSnapshot(true)
    runTransactionWith<Unit>(transaction)

    repository.blockUser("user-b")

    verify { transaction.set(blockedRef, mapOf("blockedUid" to "user-b")) }
    verify { transaction.delete(friendshipRef) }
    verify { transaction.delete(followRef) }
  }

  @Test
  fun blockUser_succeedsCleanlyWhenNeitherFriendshipNorFollowExists() = runTest {
    val friendshipRef = mockk<DocumentReference>()
    val followRef = stubIncomingFollowRef("user-a", "user-b")
    val blockedRef = stubBlockedUserRef("user-a", "user-b")
    val transaction = mockk<Transaction>(relaxed = true)
    every { friendships.document("user-a~user-b") } returns friendshipRef
    every { transaction.get(friendshipRef) } returns existenceSnapshot(false)
    every { transaction.get(followRef) } returns existenceSnapshot(false)
    runTransactionWith<Unit>(transaction)

    repository.blockUser("user-b")

    verify { transaction.set(blockedRef, mapOf("blockedUid" to "user-b")) }
    verify(exactly = 0) { transaction.delete(any()) }
  }

  @Test
  fun blockUser_rejectsSelfInvalidUidAndUnauthenticatedUser() = runTest {
    assertTrue(
        captureFailure { repository.blockUser("user-a") }
            is FriendRepositoryException.SelfFriendRequest
    )
    assertTrue(
        captureFailure { repository.blockUser("bad/uid") } is FriendRepositoryException.InvalidUid
    )
    every { auth.currentUser } returns null
    assertTrue(
        captureFailure { repository.blockUser("user-b") }
            is FriendRepositoryException.UnauthenticatedUser
    )
  }

  @Test
  fun blockUser_wrapsFirestoreFailureAndPropagatesCancellation() = runTest {
    every { friendships.document("user-a~user-b") } returns mockk()
    stubIncomingFollowRef("user-a", "user-b")
    stubBlockedUserRef("user-a", "user-b")

    val failure = IllegalStateException("Firestore unavailable")
    every { firestore.runTransaction<Unit>(any()) } returns Tasks.forException(failure)
    val error = captureFailure { repository.blockUser("user-b") }
    assertTrue(error is FriendRepositoryException.FirestoreFailure)
    assertTrue(error.cause === failure)

    every { firestore.runTransaction<Unit>(any()) } returns Tasks.forCanceled()
    assertTrue(captureFailure { repository.blockUser("user-b") } is CancellationException)
  }

  @Test
  fun unblockUser_deletesBlockedUserDocument() = runTest {
    val blockedRef = stubBlockedUserRef("user-a", "user-b")
    every { blockedRef.delete() } returns Tasks.forResult(null)

    repository.unblockUser("user-b")

    verify { blockedRef.delete() }
  }

  @Test
  fun unblockUser_rejectsSelfInvalidUidAndUnauthenticatedUser() = runTest {
    assertTrue(
        captureFailure { repository.unblockUser("user-a") }
            is FriendRepositoryException.SelfFriendRequest
    )
    assertTrue(
        captureFailure { repository.unblockUser("bad/uid") } is FriendRepositoryException.InvalidUid
    )
    every { auth.currentUser } returns null
    assertTrue(
        captureFailure { repository.unblockUser("user-b") }
            is FriendRepositoryException.UnauthenticatedUser
    )
  }

  @Test
  fun unblockUser_wrapsFirestoreFailureAndPropagatesCancellation() = runTest {
    val blockedRef = stubBlockedUserRef("user-a", "user-b")
    val failure = IllegalStateException("Firestore unavailable")
    every { blockedRef.delete() } returns Tasks.forException(failure)

    val error = captureFailure { repository.unblockUser("user-b") }
    assertTrue(error is FriendRepositoryException.FirestoreFailure)
    assertTrue(error.cause === failure)

    every { blockedRef.delete() } returns Tasks.forCanceled()
    assertTrue(captureFailure { repository.unblockUser("user-b") } is CancellationException)
  }

  @Test
  fun getBlockedUids_returnsEmptySetAndPopulatedSet() = runTest {
    val blockedCollection = stubBlockedUsersCollection("user-a")
    val emptySnapshot = mockk<QuerySnapshot> { every { documents } returns emptyList() }
    every { blockedCollection.get() } returns Tasks.forResult(emptySnapshot)
    assertEquals(emptySet<String>(), repository.getBlockedUids())

    val doc1 = mockk<DocumentSnapshot> { every { id } returns "user-b" }
    val doc2 = mockk<DocumentSnapshot> { every { id } returns "user-c" }
    val populatedSnapshot = mockk<QuerySnapshot> { every { documents } returns listOf(doc1, doc2) }
    every { blockedCollection.get() } returns Tasks.forResult(populatedSnapshot)
    assertEquals(setOf("user-b", "user-c"), repository.getBlockedUids())
  }

  @Test
  fun getBlockedUids_rejectsUnauthenticatedUser() = runTest {
    every { auth.currentUser } returns null

    val error = captureFailure { repository.getBlockedUids() }

    assertTrue(error is FriendRepositoryException.UnauthenticatedUser)
  }

  @Test
  fun getBlockedUids_wrapsFirestoreFailureAndPropagatesCancellation() = runTest {
    val blockedCollection = stubBlockedUsersCollection("user-a")
    val failure = IllegalStateException("Firestore unavailable")
    every { blockedCollection.get() } returns Tasks.forException(failure)

    val error = captureFailure { repository.getBlockedUids() }
    assertTrue(error is FriendRepositoryException.FirestoreFailure)
    assertTrue(error.cause === failure)

    every { blockedCollection.get() } returns Tasks.forCanceled()
    assertTrue(captureFailure { repository.getBlockedUids() } is CancellationException)
  }

  @Test
  fun rejectFriendRequest_withDefaultBlacklistFalseThrowsWhenRequestMissing() = runTest {
    val requestRef = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)
    every { friendships.document("user-a~user-b") } returns requestRef
    every { transaction.get(requestRef) } returns existenceSnapshot(false)
    runTransactionWith<Unit>(transaction)

    val error = captureFailure { repository.rejectFriendRequest("user-a~user-b") }

    assertTrue(error is FriendRepositoryException.RequestNotFound)
  }

  @Test
  fun acceptFriendRequest_sameUidPairThrowsSelfFriendRequest() = runTest {
    val error = captureFailure { repository.acceptFriendRequest("user-a~user-a") }

    assertTrue(error is FriendRepositoryException.SelfFriendRequest)
  }

  private fun stubBlockedUsersCollection(blockerUid: String): CollectionReference {
    val userDoc = mockk<DocumentReference>()
    val blockedCollection = mockk<CollectionReference>()
    every { users.document(blockerUid) } returns userDoc
    every { userDoc.collection("blockedUsers") } returns blockedCollection
    return blockedCollection
  }

  private fun stubBlockedUserRef(blockerUid: String, blockedUid: String): DocumentReference {
    val blockedCollection = stubBlockedUsersCollection(blockerUid)
    val blockedRef = mockk<DocumentReference>()
    every { blockedCollection.document(blockedUid) } returns blockedRef
    return blockedRef
  }

  private fun stubIncomingFollowRef(ownerUid: String, followerUid: String): DocumentReference {
    val followRef = mockk<DocumentReference>()
    every { follows.document("$ownerUid~$followerUid") } returns followRef
    return followRef
  }

  private fun existenceSnapshot(exists: Boolean) =
      mockk<DocumentSnapshot> { every { exists() } returns exists }

  private fun recipientProfileSnapshot(
      exists: Boolean = true,
      allowFriendRequests: Boolean? = null,
  ) =
      mockk<DocumentSnapshot> {
        every { exists() } returns exists
        every { getBoolean("allowFriendRequests") } returns allowFriendRequests
      }

  private fun recordSnapshot(
      id: String,
      requesterUid: String,
      recipientUid: String,
      status: String,
  ) =
      mockk<DocumentSnapshot> {
        every { this@mockk.id } returns id
        every { exists() } returns true
        every { getString("requesterUid") } returns requesterUid
        every { getString("recipientUid") } returns recipientUid
        every { getString("status") } returns status
        every { data } returns
            mapOf("participantUids" to listOf(requesterUid, recipientUid).sorted())
      }

  private fun queryRecord(
      id: String,
      requesterUid: String,
      recipientUid: String,
      status: String,
  ) =
      mockk<QueryDocumentSnapshot> {
        every { this@mockk.id } returns id
        every { getString("requesterUid") } returns requesterUid
        every { getString("recipientUid") } returns recipientUid
        every { getString("status") } returns status
        every { data } returns
            mapOf("participantUids" to listOf(requesterUid, recipientUid).sorted())
      }

  private inline fun <reified T> runTransactionWith(transaction: Transaction) {
    every { firestore.runTransaction<T>(any()) } answers
        {
          val function = firstArg<Transaction.Function<T>>()
          Tasks.forResult(function.apply(transaction))
        }
  }

  private suspend fun captureFailure(action: suspend () -> Any?): Exception {
    try {
      action()
    } catch (exception: Exception) {
      return exception
    }
    throw AssertionError("Expected operation to fail")
  }
}
