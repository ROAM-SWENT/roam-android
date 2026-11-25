// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.leaderboard

import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.friend.FriendRepositoryException
import com.github.roamswent.roam.model.friend.Friendship
import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.model.profile.PublicProfileException
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FirestoreLeaderboardRepositoryTest {
  private val firestore = mockk<FirebaseFirestore>()
  private val auth = mockk<FirebaseAuth>()
  private val user = mockk<FirebaseUser>()
  private val profiles = mockk<CollectionReference>()
  private val users = mockk<CollectionReference>()
  private val userDocument = mockk<DocumentReference>()
  private val monuments = mockk<CollectionReference>()
  private val leaderboard = mockk<CollectionReference>()
  private val stateReference = mockk<DocumentReference>()
  private val profileReference = mockk<DocumentReference>()
  private val transaction = mockk<Transaction>()
  private val friendRepository = mockk<FriendRepository>()
  private val publicProfileRepository = mockk<PublicProfileRepository>()
  private val repository =
      FirestoreLeaderboardRepository(firestore, auth, friendRepository, publicProfileRepository)

  private val eiffelTower = MonumentKey(48858, 2294)
  private val arcDeTriomphe = MonumentKey(48874, 2295)

  // In-memory state standing in for the database, committed only when a transaction succeeds.
  private val monumentReferences = mutableMapOf<String, DocumentReference>()
  private val countedMonumentIds = mutableSetOf<String>()
  private var profileExists = true
  private var storedCount: Long? = null
  private var storedPointer: String? = null
  private val operations = mutableListOf<String>()
  private val pendingMonumentIds = mutableListOf<String>()
  private var pendingCount: Long? = null
  private var pendingPointer: String? = null

  @Before
  fun setUp() {
    every { auth.currentUser } returns user
    every { user.uid } returns "user-id"
    every { firestore.collection("publicProfiles") } returns profiles
    every { firestore.collection("users") } returns users
    every { profiles.document("user-id") } returns profileReference
    every { users.document("user-id") } returns userDocument
    every { userDocument.collection("verifiedMonuments") } returns monuments
    every { userDocument.collection("leaderboard") } returns leaderboard
    every { leaderboard.document("state") } returns stateReference
    every { monuments.document(any()) } answers
        {
          monumentReferences.getOrPut(firstArg()) { mockk(firstArg()) }
        }
    every { transaction.get(any()) } answers { snapshotOf(firstArg()) }
    every { transaction.set(stateReference, any<Map<String, Any>>()) } answers
        {
          val data = secondArg<Map<String, Any>>()
          operations += "set state $data"
          pendingPointer = data["lastVerifiedMonumentId"] as String
          transaction
        }
    every { transaction.set(not(stateReference), any<Map<String, Any>>()) } answers
        {
          val id = monumentIdOf(firstArg())
          operations += "set $id ${secondArg<Map<String, Any>>()}"
          pendingMonumentIds += id
          transaction
        }
    every { transaction.update(profileReference, "verifiedMonumentCount", any()) } answers
        {
          val count = (thirdArg<Any>() as Number).toLong()
          operations += "update count $count"
          pendingCount = count
          transaction
        }
    runTransactionsAndCommit()
  }

  @Test
  fun firstMonumentIsRecordedAndCountGoesFromZeroToOne() = runTest {
    val result = repository.recordVerifiedMonument(eiffelTower)

    assertEquals(VerifiedMonumentResult.Counted(1), result)
    assertEquals(1L, storedCount)
    assertEquals(setOf("48858_2294"), countedMonumentIds)
    assertEquals("48858_2294", storedPointer)
  }

  @Test
  fun transactionReadsProfileAndNeighbourhoodBeforeWriting() = runTest {
    repository.recordVerifiedMonument(eiffelTower)

    val expectedReads = eiffelTower.neighbourhood().map { "get ${it.id}" }.sorted()
    assertEquals(
        listOf("get profile") +
            expectedReads +
            listOf(
                "set 48858_2294 {}",
                "set state {lastVerifiedMonumentId=48858_2294}",
                "update count 1",
            ),
        operations,
    )
  }

  @Test
  fun reRecordingTheSameMonumentDoesNotIncrementOrWrite() = runTest {
    repository.recordVerifiedMonument(eiffelTower)
    operations.clear()

    val result = repository.recordVerifiedMonument(eiffelTower)

    assertEquals(VerifiedMonumentResult.AlreadyCounted(1), result)
    assertEquals(1L, storedCount)
    assertTrue(operations.all { it.startsWith("get ") })
  }

  @Test
  fun neighbouringKeyOfACountedMonumentDoesNotIncrement() = runTest {
    repository.recordVerifiedMonument(eiffelTower)

    val result = repository.recordVerifiedMonument(MonumentKey(48859, 2295))

    assertEquals(VerifiedMonumentResult.AlreadyCounted(1), result)
    assertEquals(1L, storedCount)
    assertEquals(setOf("48858_2294"), countedMonumentIds)
  }

  @Test
  fun distinctMonumentIncrements() = runTest {
    repository.recordVerifiedMonument(eiffelTower)

    val result = repository.recordVerifiedMonument(arcDeTriomphe)

    assertEquals(VerifiedMonumentResult.Counted(2), result)
    assertEquals(2L, storedCount)
    assertEquals(setOf("48858_2294", "48874_2295"), countedMonumentIds)
    assertEquals("48874_2295", storedPointer)
  }

  @Test
  fun existingCountIsIncrementedAndReturned() = runTest {
    storedCount = 41

    assertEquals(
        VerifiedMonumentResult.Counted(42),
        repository.recordVerifiedMonument(eiffelTower),
    )
    assertEquals(
        VerifiedMonumentResult.AlreadyCounted(42),
        repository.recordVerifiedMonument(eiffelTower),
    )
  }

  @Test
  fun alreadyCountedReturnsTheStoredCountWithoutWriting() = runTest {
    storedCount = 7
    countedMonumentIds += eiffelTower.id

    val result = repository.recordVerifiedMonument(eiffelTower)

    assertEquals(VerifiedMonumentResult.AlreadyCounted(7), result)
    verify(exactly = 0) { transaction.set(any(), any<Map<String, Any>>()) }
    verify(exactly = 0) { transaction.update(any(), any<String>(), any()) }
  }

  @Test
  fun concurrentRecordingOfTheSameMonumentCountsOnce() = runTest {
    // Two devices record the same monument. Both first read an empty neighbourhood; the other
    // device commits first, so Firestore rejects this attempt and runs the function again.
    var attempts = 0
    every { firestore.runTransaction<VerifiedMonumentResult>(any()) } answers
        {
          val function = firstArg<Transaction.Function<VerifiedMonumentResult>>()
          attempts++
          function.apply(transaction)
          discardPendingWrites()
          countedMonumentIds += eiffelTower.id
          storedCount = 1
          attempts++
          val result = function.apply(transaction)
          commitPendingWrites()
          Tasks.forResult(result)
        }

    val result = repository.recordVerifiedMonument(eiffelTower)

    assertEquals(2, attempts)
    assertEquals(VerifiedMonumentResult.AlreadyCounted(1), result)
    assertEquals(1L, storedCount)
    assertEquals(setOf("48858_2294"), countedMonumentIds)
  }

  @Test
  fun unauthenticatedUserFailsWithoutStartingATransaction() = runTest {
    every { auth.currentUser } returns null

    val error = captureFailure { repository.recordVerifiedMonument(eiffelTower) }

    assertTrue(error.toString(), error is LeaderboardException.UnauthenticatedUser)
    verify(exactly = 0) { firestore.runTransaction<VerifiedMonumentResult>(any()) }
  }

  @Test
  fun blankUidIsUnauthenticated() = runTest {
    every { user.uid } returns " "

    val error = captureFailure { repository.recordVerifiedMonument(eiffelTower) }

    assertTrue(error.toString(), error is LeaderboardException.UnauthenticatedUser)
  }

  @Test
  fun missingProfileFailsWithoutWriting() = runTest {
    profileExists = false

    val error = captureFailure { repository.recordVerifiedMonument(eiffelTower) }

    assertTrue(error.toString(), error is LeaderboardException.ProfileNotRegistered)
    assertEquals(listOf("get profile"), operations)
    assertTrue(countedMonumentIds.isEmpty())
  }

  @Test
  fun transactionFailureIsWrappedAsFirestoreFailure() = runTest {
    val failure = IllegalStateException("Firestore unavailable")
    every { firestore.runTransaction<VerifiedMonumentResult>(any()) } returns
        Tasks.forException(failure)

    val error = captureFailure { repository.recordVerifiedMonument(eiffelTower) }

    assertTrue(error.toString(), error is LeaderboardException.FirestoreFailure)
    assertTrue(error.cause === failure)
  }

  @Test
  fun writeFailureInsideTheTransactionIsWrappedAsFirestoreFailure() = runTest {
    val failure = IllegalStateException("Write rejected")
    every { transaction.set(any(), any<Map<String, Any>>()) } throws failure

    val error = captureFailure { repository.recordVerifiedMonument(eiffelTower) }

    assertTrue(error.toString(), error is LeaderboardException.FirestoreFailure)
    assertTrue(error.cause === failure)
    assertEquals(null, storedCount)
  }

  @Test
  fun leaderboardWithoutFriendsHasOnlyTheCurrentUser() = runTest {
    givenFriends()
    givenProfiles(PublicProfile("user-id", "Me", 3))

    assertEquals(
        listOf(LeaderboardEntry("user-id", "Me", 3, rank = 1)),
        repository.getFriendsLeaderboard(),
    )
  }

  @Test
  fun friendsAreSortedByDescendingCountWithTheCurrentUserInPlace() = runTest {
    givenFriends("ann", "bob", "cal")
    givenProfiles(
        PublicProfile("user-id", "Me", 4),
        PublicProfile("ann", "Ann", 2),
        PublicProfile("bob", "Bob", 9),
        PublicProfile("cal", "Cal", 6),
    )

    val leaderboard = repository.getFriendsLeaderboard()

    assertEquals(
        listOf(
            LeaderboardEntry("bob", "Bob", 9, 1),
            LeaderboardEntry("cal", "Cal", 6, 2),
            LeaderboardEntry("user-id", "Me", 4, 3),
            LeaderboardEntry("ann", "Ann", 2, 4),
        ),
        leaderboard,
    )
  }

  @Test
  fun equalCountsShareARankAndAreOrderedByUsername() = runTest {
    givenFriends("zed", "amy", "low")
    givenProfiles(
        PublicProfile("user-id", "me", 5),
        PublicProfile("zed", "Zed", 5),
        PublicProfile("amy", "Amy", 5),
        PublicProfile("low", "Low", 1),
    )

    val leaderboard = repository.getFriendsLeaderboard()

    assertEquals(listOf("Amy", "me", "Zed", "Low"), leaderboard.map { it.username })
    assertEquals(listOf(1, 1, 1, 4), leaderboard.map { it.rank })
  }

  @Test
  fun friendWithoutStoredCountRanksWithZero() = runTest {
    givenFriends("new")
    givenProfiles(PublicProfile("user-id", "Me", 1), PublicProfile("new", "Newbie"))

    assertEquals(
        LeaderboardEntry("new", "Newbie", 0, 2),
        repository.getFriendsLeaderboard().last(),
    )
  }

  @Test
  fun onlyAcceptedFriendsFromFriendRepositoryAreLoaded() = runTest {
    givenFriends("ann", "ann", "user-id")
    givenProfiles(PublicProfile("user-id", "Me", 0), PublicProfile("ann", "Ann", 1))

    val leaderboard = repository.getFriendsLeaderboard()

    assertEquals(listOf("ann", "user-id"), leaderboard.map { it.uid })
    // Pending requests are never consulted: only the accepted friendships are.
    coVerify(exactly = 1) { friendRepository.getFriends() }
    confirmVerified(friendRepository)
    coVerify(exactly = 1) { publicProfileRepository.getProfile("user-id") }
    coVerify(exactly = 1) { publicProfileRepository.getProfile("ann") }
    confirmVerified(publicProfileRepository)
  }

  @Test
  fun friendWithoutProfileIsLeftOut() = runTest {
    givenFriends("ghost", "ann")
    givenProfiles(PublicProfile("user-id", "Me", 0), PublicProfile("ann", "Ann", 1))

    assertEquals(listOf("ann", "user-id"), repository.getFriendsLeaderboard().map { it.uid })
  }

  @Test
  fun currentUserWithoutProfileFails() = runTest {
    givenFriends("ann")
    givenProfiles(PublicProfile("ann", "Ann", 1))

    val error = captureFailure { repository.getFriendsLeaderboard() }

    assertTrue(error.toString(), error is LeaderboardException.ProfileNotRegistered)
  }

  @Test
  fun leaderboardForUnauthenticatedUserFailsWithoutLoading() = runTest {
    every { auth.currentUser } returns null

    val error = captureFailure { repository.getFriendsLeaderboard() }

    assertTrue(error.toString(), error is LeaderboardException.UnauthenticatedUser)
    confirmVerified(friendRepository, publicProfileRepository)
  }

  @Test
  fun signOutDuringLoadingIsUnauthenticated() = runTest {
    coEvery { friendRepository.getFriends() } throws FriendRepositoryException.UnauthenticatedUser()

    val error = captureFailure { repository.getFriendsLeaderboard() }

    assertTrue(error.toString(), error is LeaderboardException.UnauthenticatedUser)
  }

  @Test
  fun friendLoadingFailureIsFirestoreFailure() = runTest {
    val failure = FriendRepositoryException.FirestoreFailure(IllegalStateException("offline"))
    coEvery { friendRepository.getFriends() } throws failure

    val error = captureFailure { repository.getFriendsLeaderboard() }

    assertTrue(error.toString(), error is LeaderboardException.FirestoreFailure)
    assertTrue(error.cause === failure)
  }

  @Test
  fun invalidProfileIsFirestoreFailure() = runTest {
    val failure = PublicProfileException.FirestoreFailure(IllegalStateException("bad count"))
    givenFriends("ann")
    coEvery { publicProfileRepository.getProfile("user-id") } returns PublicProfile("user-id", "Me")
    coEvery { publicProfileRepository.getProfile("ann") } throws failure

    val error = captureFailure { repository.getFriendsLeaderboard() }

    assertTrue(error.toString(), error is LeaderboardException.FirestoreFailure)
    // The failure crosses an async boundary, where coroutine debugging may rethrow a copy of it.
    assertTrue(error.cause is PublicProfileException.FirestoreFailure)
    assertEquals(failure.message, error.cause?.message)
  }

  private fun givenFriends(vararg friendUids: String) {
    coEvery { friendRepository.getFriends() } returns
        friendUids.mapIndexed { index, uid -> Friendship("friendship-$index", uid) }
  }

  private fun givenProfiles(vararg profiles: PublicProfile) {
    coEvery { publicProfileRepository.getProfile(any()) } returns null
    profiles.forEach { coEvery { publicProfileRepository.getProfile(it.uid) } returns it }
  }

  private fun runTransactionsAndCommit() {
    every { firestore.runTransaction<VerifiedMonumentResult>(any()) } answers
        {
          val function = firstArg<Transaction.Function<VerifiedMonumentResult>>()
          try {
            val result = function.apply(transaction)
            commitPendingWrites()
            Tasks.forResult(result)
          } finally {
            discardPendingWrites()
          }
        }
  }

  private fun commitPendingWrites() {
    countedMonumentIds += pendingMonumentIds
    pendingCount?.let { storedCount = it }
    pendingPointer?.let { storedPointer = it }
    discardPendingWrites()
  }

  private fun discardPendingWrites() {
    pendingMonumentIds.clear()
    pendingCount = null
    pendingPointer = null
  }

  private fun snapshotOf(reference: DocumentReference): DocumentSnapshot {
    if (reference === profileReference) {
      operations += "get profile"
      return mockk {
        every { exists() } returns profileExists
        every { getLong("verifiedMonumentCount") } returns storedCount
      }
    }
    val id = monumentIdOf(reference)
    operations += "get $id"
    return mockk { every { exists() } returns (id in countedMonumentIds) }
  }

  private fun monumentIdOf(reference: DocumentReference): String =
      monumentReferences.entries.single { it.value === reference }.key

  private suspend fun captureFailure(action: suspend () -> Any?): Exception {
    try {
      action()
    } catch (exception: Exception) {
      return exception
    }
    throw AssertionError("Expected operation to fail")
  }
}
