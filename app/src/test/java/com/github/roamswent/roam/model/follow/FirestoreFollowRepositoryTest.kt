// Co-authored-by: Gemini 4 Neon <noreply@google.com>

package com.github.roamswent.roam.model.follow

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FirestoreFollowRepositoryTest {
  private val firestore = mockk<FirebaseFirestore>()
  private val auth = mockk<FirebaseAuth>()
  private val user = mockk<FirebaseUser>()
  private val follows = mockk<CollectionReference>()
  private val profiles = mockk<CollectionReference>()
  private val friendships = mockk<CollectionReference>()
  private val users = mockk<CollectionReference>()
  private val repository = FirestoreFollowRepository(firestore, auth)

  @Before
  fun setUp() {
    every { auth.currentUser } returns user
    every { user.uid } returns CURRENT_UID
    every { firestore.collection("follows") } returns follows
    every { firestore.collection("publicProfiles") } returns profiles
    every { firestore.collection("friendships") } returns friendships
    every { firestore.collection("users") } returns users
  }

  @Test
  fun follow_createsDocumentAndReturnsFollowForPublicTarget() = runTest {
    val followRef = stubFollowRef(TARGET_UID, CURRENT_UID)
    val profileRef = stubProfileRef(TARGET_UID)
    val transaction = mockk<Transaction>(relaxed = true)
    every { transaction.get(followRef) } returns existenceSnapshot(false)
    every { transaction.get(profileRef) } returns profileSnapshot(exists = true, isPublic = true)
    runTransactionWith<Follow>(transaction)

    val result = repository.follow(TARGET_UID)

    assertEquals(Follow("$TARGET_UID~$CURRENT_UID", TARGET_UID, CURRENT_UID), result)
    verify {
      transaction.set(
          followRef,
          mapOf("ownerUid" to TARGET_UID, "followerUid" to CURRENT_UID),
      )
    }
  }

  @Test
  fun follow_returnsExistingFollowIdempotentlyWithoutWriting() = runTest {
    val followRef = stubFollowRef(TARGET_UID, CURRENT_UID)
    val profileRef = stubProfileRef(TARGET_UID)
    val transaction = mockk<Transaction>(relaxed = true)
    every { transaction.get(followRef) } returns
        followSnapshot("$TARGET_UID~$CURRENT_UID", TARGET_UID, CURRENT_UID)
    every { transaction.get(profileRef) } returns profileSnapshot(exists = true, isPublic = true)
    runTransactionWith<Follow>(transaction)

    val result = repository.follow(TARGET_UID)

    assertEquals(Follow("$TARGET_UID~$CURRENT_UID", TARGET_UID, CURRENT_UID), result)
    verify(exactly = 0) { transaction.set(any(), any<Map<String, Any>>()) }
  }

  @Test
  fun follow_throwsTargetProfileNotFoundWhenProfileMissing() = runTest {
    val followRef = stubFollowRef(TARGET_UID, CURRENT_UID)
    val profileRef = stubProfileRef(TARGET_UID)
    val transaction = mockk<Transaction>(relaxed = true)
    every { transaction.get(followRef) } returns existenceSnapshot(false)
    every { transaction.get(profileRef) } returns profileSnapshot(exists = false, isPublic = null)
    runTransactionWith<Follow>(transaction)

    val error = captureFailure { repository.follow(TARGET_UID) }

    assertTrue(error is FollowRepositoryException.TargetProfileNotFound)
    verify(exactly = 0) { transaction.set(any(), any<Map<String, Any>>()) }
  }

  @Test
  fun follow_throwsAccountNotPublicForPrivateAndLegacyProfiles() = runTest {
    val followRef = stubFollowRef(TARGET_UID, CURRENT_UID)
    val profileRef = stubProfileRef(TARGET_UID)
    val transaction = mockk<Transaction>(relaxed = true)
    every { transaction.get(followRef) } returns existenceSnapshot(false)
    runTransactionWith<Follow>(transaction)

    for (isPublic in listOf(false, null)) {
      every { transaction.get(profileRef) } returns
          profileSnapshot(exists = true, isPublic = isPublic)

      val error = captureFailure { repository.follow(TARGET_UID) }

      assertTrue(
          "Expected AccountNotPublic for isPublic=$isPublic",
          error is FollowRepositoryException.AccountNotPublic,
      )
    }
    verify(exactly = 0) { transaction.set(any(), any<Map<String, Any>>()) }
  }

  @Test
  fun follow_mapsFirestoreErrorsAndPropagatesCancellation() = runTest {
    stubFollowRef(TARGET_UID, CURRENT_UID)
    stubProfileRef(TARGET_UID)

    val denied =
        FirebaseFirestoreException("Denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
    every { firestore.runTransaction<Follow>(any()) } returns Tasks.forException(denied)
    assertTrue(
        captureFailure { repository.follow(TARGET_UID) } is FollowRepositoryException.BlockedByUser
    )

    val unavailable =
        FirebaseFirestoreException("Unavailable", FirebaseFirestoreException.Code.UNAVAILABLE)
    every { firestore.runTransaction<Follow>(any()) } returns Tasks.forException(unavailable)
    val unavailableError = captureFailure { repository.follow(TARGET_UID) }
    assertTrue(unavailableError is FollowRepositoryException.FirestoreFailure)
    assertTrue(unavailableError.cause === unavailable)

    val generic = IllegalStateException("Unexpected")
    every { firestore.runTransaction<Follow>(any()) } returns Tasks.forException(generic)
    val genericError = captureFailure { repository.follow(TARGET_UID) }
    assertTrue(genericError is FollowRepositoryException.FirestoreFailure)
    assertTrue(genericError.cause === generic)

    every { firestore.runTransaction<Follow>(any()) } returns Tasks.forCanceled()
    assertTrue(captureFailure { repository.follow(TARGET_UID) } is CancellationException)
  }

  @Test
  fun follow_rejectsSelfInvalidUidAndUnauthenticatedBeforeTouchingFirestore() = runTest {
    assertValidationGuards { uid -> repository.follow(uid) }
    verify(exactly = 0) { firestore.collection("follows") }
  }

  @Test
  fun follow_wrapsMalformedExistingFollowSnapshotInFirestoreFailure() = runTest {
    val followRef = stubFollowRef(TARGET_UID, CURRENT_UID)
    val profileRef = stubProfileRef(TARGET_UID)
    val transaction = mockk<Transaction>(relaxed = true)
    every { transaction.get(profileRef) } returns profileSnapshot(exists = true, isPublic = true)
    runTransactionWith<Follow>(transaction)

    for ((label, badSnapshot) in malformedFollowSnapshots()) {
      every { transaction.get(followRef) } returns badSnapshot

      val error = captureFailure { repository.follow(TARGET_UID) }

      assertTrue(
          "Expected FirestoreFailure for $label",
          error is FollowRepositoryException.FirestoreFailure,
      )
    }
  }

  @Test
  fun unfollow_deletesExistingFollowAndThrowsWhenMissing() = runTest {
    val followRef = stubFollowRef(TARGET_UID, CURRENT_UID)
    val transaction = mockk<Transaction>(relaxed = true)
    every { transaction.get(followRef) } returns
        followSnapshot("$TARGET_UID~$CURRENT_UID", TARGET_UID, CURRENT_UID)
    runTransactionWith<Unit>(transaction)

    repository.unfollow(TARGET_UID)
    verify(exactly = 1) { transaction.delete(followRef) }

    every { transaction.get(followRef) } returns existenceSnapshot(false)
    val error = captureFailure { repository.unfollow(TARGET_UID) }
    assertTrue(error is FollowRepositoryException.FollowNotFound)
  }

  @Test
  fun unfollow_validatesInputsAndMapsErrors() = runTest {
    assertValidationGuards { uid -> repository.unfollow(uid) }

    every { auth.currentUser } returns user
    stubFollowRef(TARGET_UID, CURRENT_UID)
    val failure = IllegalStateException("Boom")
    every { firestore.runTransaction<Unit>(any()) } returns Tasks.forException(failure)
    val error = captureFailure { repository.unfollow(TARGET_UID) }
    assertTrue(error is FollowRepositoryException.FirestoreFailure)
    assertTrue(error.cause === failure)

    every { firestore.runTransaction<Unit>(any()) } returns Tasks.forCanceled()
    assertTrue(captureFailure { repository.unfollow(TARGET_UID) } is CancellationException)
  }

  @Test
  fun removeFollower_withDefaultBlacklistFalseDeletesOnlyFollow() = runTest {
    val followRef = stubFollowRef(CURRENT_UID, FOLLOWER_UID)
    val transaction = mockk<Transaction>(relaxed = true)
    every { transaction.get(followRef) } returns
        followSnapshot("$CURRENT_UID~$FOLLOWER_UID", CURRENT_UID, FOLLOWER_UID)
    runTransactionWith<Unit>(transaction)

    repository.removeFollower(FOLLOWER_UID)

    verify(exactly = 1) { transaction.delete(followRef) }
    verify(exactly = 0) { transaction.set(any(), any<Map<String, Any>>()) }
    verify(exactly = 0) { firestore.collection("users") }
    verify(exactly = 0) { firestore.collection("friendships") }
  }

  @Test
  fun removeFollower_withBlacklistTrueDeletesFollowAndFriendshipAndWritesBlock() = runTest {
    val followRef = stubFollowRef(CURRENT_UID, FOLLOWER_UID)
    val friendshipRef = stubFriendshipRef(CURRENT_UID, FOLLOWER_UID)
    val blockedRef = stubBlockedUserRef(CURRENT_UID, FOLLOWER_UID)
    val transaction = mockk<Transaction>(relaxed = true)
    every { transaction.get(followRef) } returns
        followSnapshot("$CURRENT_UID~$FOLLOWER_UID", CURRENT_UID, FOLLOWER_UID)
    every { transaction.get(friendshipRef) } returns existenceSnapshot(true)
    every { transaction.get(blockedRef) } returns existenceSnapshot(false)
    runTransactionWith<Unit>(transaction)

    repository.removeFollower(FOLLOWER_UID, blacklist = true)

    verify { transaction.delete(followRef) }
    verify { transaction.set(blockedRef, mapOf("blockedUid" to FOLLOWER_UID)) }
    verify { transaction.delete(friendshipRef) }
  }

  @Test
  fun removeFollower_withBlacklistTrueSkipsAlreadyBlockedAndMissingFriendship() = runTest {
    val followRef = stubFollowRef(CURRENT_UID, FOLLOWER_UID)
    val friendshipRef = stubFriendshipRef(CURRENT_UID, FOLLOWER_UID)
    val blockedRef = stubBlockedUserRef(CURRENT_UID, FOLLOWER_UID)
    val transaction = mockk<Transaction>(relaxed = true)
    every { transaction.get(followRef) } returns
        followSnapshot("$CURRENT_UID~$FOLLOWER_UID", CURRENT_UID, FOLLOWER_UID)
    every { transaction.get(friendshipRef) } returns existenceSnapshot(false)
    every { transaction.get(blockedRef) } returns existenceSnapshot(true)
    runTransactionWith<Unit>(transaction)

    repository.removeFollower(FOLLOWER_UID, blacklist = true)

    verify(exactly = 1) { transaction.delete(followRef) }
    verify(exactly = 0) { transaction.set(blockedRef, any<Map<String, Any>>()) }
    verify(exactly = 0) { transaction.delete(friendshipRef) }
  }

  @Test
  fun removeFollower_throwsFollowNotFoundWithAndWithoutBlacklist() = runTest {
    val followRef = stubFollowRef(CURRENT_UID, FOLLOWER_UID)
    val friendshipRef = stubFriendshipRef(CURRENT_UID, FOLLOWER_UID)
    val blockedRef = stubBlockedUserRef(CURRENT_UID, FOLLOWER_UID)
    val transaction = mockk<Transaction>(relaxed = true)
    every { transaction.get(followRef) } returns existenceSnapshot(false)
    every { transaction.get(friendshipRef) } returns existenceSnapshot(true)
    every { transaction.get(blockedRef) } returns existenceSnapshot(false)
    runTransactionWith<Unit>(transaction)

    assertTrue(
        captureFailure { repository.removeFollower(FOLLOWER_UID) }
            is FollowRepositoryException.FollowNotFound
    )
    assertTrue(
        captureFailure { repository.removeFollower(FOLLOWER_UID, blacklist = true) }
            is FollowRepositoryException.FollowNotFound
    )
    verify(exactly = 0) { transaction.delete(any()) }
    verify(exactly = 0) { transaction.set(any(), any<Map<String, Any>>()) }
  }

  @Test
  fun removeFollower_validatesInputsAndMapsErrors() = runTest {
    assertValidationGuards { uid -> repository.removeFollower(uid) }

    every { auth.currentUser } returns user
    stubFollowRef(CURRENT_UID, FOLLOWER_UID)
    val failure = IllegalStateException("Boom")
    every { firestore.runTransaction<Unit>(any()) } returns Tasks.forException(failure)
    val error = captureFailure { repository.removeFollower(FOLLOWER_UID) }
    assertTrue(error is FollowRepositoryException.FirestoreFailure)
    assertTrue(error.cause === failure)

    every { firestore.runTransaction<Unit>(any()) } returns Tasks.forCanceled()
    assertTrue(captureFailure { repository.removeFollower(FOLLOWER_UID) } is CancellationException)
  }

  @Test
  fun getFollowingAndGetFollowers_returnEmptyAndPopulatedLists() = runTest {
    val followingQuery = mockk<Query>()
    val followersQuery = mockk<Query>()
    every { follows.whereEqualTo("followerUid", CURRENT_UID) } returns followingQuery
    every { follows.whereEqualTo("ownerUid", CURRENT_UID) } returns followersQuery

    val emptySnapshot = mockk<QuerySnapshot> { every { documents } returns emptyList() }
    every { followingQuery.get() } returns Tasks.forResult(emptySnapshot)
    every { followersQuery.get() } returns Tasks.forResult(emptySnapshot)
    assertEquals(emptyList<Follow>(), repository.getFollowing())
    assertEquals(emptyList<Follow>(), repository.getFollowers())

    val followingDocs =
        listOf(
            queryFollowSnapshot("target-1~$CURRENT_UID", "target-1", CURRENT_UID),
            queryFollowSnapshot("target-2~$CURRENT_UID", "target-2", CURRENT_UID),
        )
    val followerDocs =
        listOf(
            queryFollowSnapshot("$CURRENT_UID~follower-1", CURRENT_UID, "follower-1"),
            queryFollowSnapshot("$CURRENT_UID~follower-2", CURRENT_UID, "follower-2"),
        )
    every { followingQuery.get() } returns
        Tasks.forResult(mockk { every { documents } returns followingDocs })
    every { followersQuery.get() } returns
        Tasks.forResult(mockk { every { documents } returns followerDocs })

    assertEquals(
        listOf(
            Follow("target-1~$CURRENT_UID", "target-1", CURRENT_UID),
            Follow("target-2~$CURRENT_UID", "target-2", CURRENT_UID),
        ),
        repository.getFollowing(),
    )
    assertEquals(
        listOf(
            Follow("$CURRENT_UID~follower-1", CURRENT_UID, "follower-1"),
            Follow("$CURRENT_UID~follower-2", CURRENT_UID, "follower-2"),
        ),
        repository.getFollowers(),
    )
  }

  @Test
  fun getFollowingAndGetFollowers_rejectMalformedDocumentsAndErrors() = runTest {
    val followingQuery = mockk<Query>()
    val followersQuery = mockk<Query>()
    every { follows.whereEqualTo("followerUid", CURRENT_UID) } returns followingQuery
    every { follows.whereEqualTo("ownerUid", CURRENT_UID) } returns followersQuery

    for ((label, badSnapshot) in malformedFollowSnapshots()) {
      every { followingQuery.get() } returns
          Tasks.forResult(mockk { every { documents } returns listOf(badSnapshot) })
      every { followersQuery.get() } returns
          Tasks.forResult(mockk { every { documents } returns listOf(badSnapshot) })

      assertTrue(
          "getFollowing malformed case: $label",
          captureFailure { repository.getFollowing() }
              is FollowRepositoryException.FirestoreFailure,
      )
      assertTrue(
          "getFollowers malformed case: $label",
          captureFailure { repository.getFollowers() }
              is FollowRepositoryException.FirestoreFailure,
      )
    }

    val failure = IllegalStateException("Query failed")
    every { followingQuery.get() } returns Tasks.forException(failure)
    every { followersQuery.get() } returns Tasks.forException(failure)
    assertTrue(
        captureFailure { repository.getFollowing() } is FollowRepositoryException.FirestoreFailure
    )
    assertTrue(
        captureFailure { repository.getFollowers() } is FollowRepositoryException.FirestoreFailure
    )

    every { followingQuery.get() } returns Tasks.forCanceled()
    every { followersQuery.get() } returns Tasks.forCanceled()
    assertTrue(captureFailure { repository.getFollowing() } is CancellationException)
    assertTrue(captureFailure { repository.getFollowers() } is CancellationException)

    every { auth.currentUser } returns null
    assertTrue(
        captureFailure { repository.getFollowing() }
            is FollowRepositoryException.UnauthenticatedUser
    )
    assertTrue(
        captureFailure { repository.getFollowers() }
            is FollowRepositoryException.UnauthenticatedUser
    )
  }

  @Test
  fun isFollowing_checksExistenceHandlesSelfAndMapsErrors() = runTest {
    assertFalse(repository.isFollowing(CURRENT_UID))
    verify(exactly = 0) { firestore.collection("follows") }

    val followRef = stubFollowRef(TARGET_UID, CURRENT_UID)
    every { followRef.get() } returns Tasks.forResult(existenceSnapshot(true))
    assertTrue(repository.isFollowing(TARGET_UID))

    every { followRef.get() } returns Tasks.forResult(existenceSnapshot(false))
    assertFalse(repository.isFollowing(TARGET_UID))

    val failure = IllegalStateException("Read failed")
    every { followRef.get() } returns Tasks.forException(failure)
    val error = captureFailure { repository.isFollowing(TARGET_UID) }
    assertTrue(error is FollowRepositoryException.FirestoreFailure)
    assertTrue(error.cause === failure)

    every { followRef.get() } returns Tasks.forCanceled()
    assertTrue(captureFailure { repository.isFollowing(TARGET_UID) } is CancellationException)

    assertTrue(
        captureFailure { repository.isFollowing("bad/uid") } is FollowRepositoryException.InvalidUid
    )
    every { auth.currentUser } returns null
    assertTrue(
        captureFailure { repository.isFollowing(TARGET_UID) }
            is FollowRepositoryException.UnauthenticatedUser
    )
  }

  private suspend fun assertValidationGuards(action: suspend (String) -> Any?) {
    assertTrue(captureFailure { action(CURRENT_UID) } is FollowRepositoryException.SelfFollow)
    assertTrue(captureFailure { action("invalid/uid") } is FollowRepositoryException.InvalidUid)
    assertTrue(captureFailure { action("") } is FollowRepositoryException.InvalidUid)
    every { auth.currentUser } returns null
    assertTrue(
        captureFailure { action(TARGET_UID) } is FollowRepositoryException.UnauthenticatedUser
    )
  }

  private fun malformedFollowSnapshots(): List<Pair<String, DocumentSnapshot>> =
      listOf(
          "missing ownerUid" to followSnapshot("$TARGET_UID~$CURRENT_UID", null, CURRENT_UID),
          "missing followerUid" to followSnapshot("$TARGET_UID~$CURRENT_UID", TARGET_UID, null),
          "invalid ownerUid" to followSnapshot("bad/owner~$CURRENT_UID", "bad/owner", CURRENT_UID),
          "invalid followerUid" to
              followSnapshot("$TARGET_UID~bad/follower", TARGET_UID, "bad/follower"),
          "self follow" to followSnapshot("$CURRENT_UID~$CURRENT_UID", CURRENT_UID, CURRENT_UID),
          "mismatched doc id" to followSnapshot("wrong~id", TARGET_UID, CURRENT_UID),
      )

  private fun stubFollowRef(ownerUid: String, followerUid: String): DocumentReference {
    val docId = "$ownerUid~$followerUid"
    val ref = mockk<DocumentReference> { every { id } returns docId }
    every { follows.document(docId) } returns ref
    return ref
  }

  private fun stubProfileRef(uid: String): DocumentReference {
    val ref = mockk<DocumentReference>()
    every { profiles.document(uid) } returns ref
    return ref
  }

  private fun stubFriendshipRef(uid1: String, uid2: String): DocumentReference {
    val (first, second) = listOf(uid1, uid2).sorted()
    val ref = mockk<DocumentReference>()
    every { friendships.document("$first~$second") } returns ref
    return ref
  }

  private fun stubBlockedUserRef(blockerUid: String, blockedUid: String): DocumentReference {
    val userDoc = mockk<DocumentReference>()
    val blockedCollection = mockk<CollectionReference>()
    val blockedRef = mockk<DocumentReference>()
    every { users.document(blockerUid) } returns userDoc
    every { userDoc.collection("blockedUsers") } returns blockedCollection
    every { blockedCollection.document(blockedUid) } returns blockedRef
    return blockedRef
  }

  private fun existenceSnapshot(exists: Boolean): DocumentSnapshot = mockk {
    every { exists() } returns exists
  }

  private fun profileSnapshot(exists: Boolean, isPublic: Boolean?): DocumentSnapshot = mockk {
    every { exists() } returns exists
    every { getBoolean("isPublic") } returns isPublic
  }

  private fun followSnapshot(
      id: String,
      ownerUid: String?,
      followerUid: String?,
  ): DocumentSnapshot = mockk {
    every { this@mockk.id } returns id
    every { exists() } returns true
    every { getString("ownerUid") } returns ownerUid
    every { getString("followerUid") } returns followerUid
  }

  private fun queryFollowSnapshot(
      id: String,
      ownerUid: String,
      followerUid: String,
  ): QueryDocumentSnapshot = mockk {
    every { this@mockk.id } returns id
    every { getString("ownerUid") } returns ownerUid
    every { getString("followerUid") } returns followerUid
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

  private companion object {
    const val CURRENT_UID = "user-a"
    const val TARGET_UID = "user-b"
    const val FOLLOWER_UID = "user-c"
  }
}
