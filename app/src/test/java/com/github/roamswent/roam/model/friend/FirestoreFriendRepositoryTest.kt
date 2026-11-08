// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.model.friend

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Transaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
  private val recipientProfile = mockk<DocumentReference>()
  private val repository = FirestoreFriendRepository(firestore, auth)

  @Before
  fun setUp() {
    every { auth.currentUser } returns user
    every { user.uid } returns "user-a"
    every { firestore.collection("publicProfiles") } returns profiles
    every { firestore.collection("friendships") } returns friendships
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
    val accepted = queryRecord("user-a~user-d", "user-d", "user-a", "ACCEPTED")
    every { friendships.whereArrayContains("participantUids", "user-a") } returns query
    every { query.get() } returns Tasks.forResult(snapshot)
    every { snapshot.documents } returns listOf(incoming, outgoing, accepted)

    assertEquals(
        listOf(FriendRequest("user-a~user-b", "user-b", "user-a")),
        repository.getIncomingRequests(),
    )
    assertEquals(
        listOf(FriendRequest("user-a~user-c", "user-a", "user-c")),
        repository.getOutgoingRequests(),
    )
    assertEquals(listOf(Friendship("user-a~user-d", "user-d")), repository.getFriends())
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
