// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.leaderboard

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
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
  private val profileReference = mockk<DocumentReference>()
  private val transaction = mockk<Transaction>()
  private val repository = FirestoreLeaderboardRepository(firestore, auth)

  private val eiffelTower = MonumentKey(48858, 2294)
  private val arcDeTriomphe = MonumentKey(48874, 2295)

  // In-memory state standing in for the database, committed only when a transaction succeeds.
  private val monumentReferences = mutableMapOf<String, DocumentReference>()
  private val countedMonumentIds = mutableSetOf<String>()
  private var profileExists = true
  private var storedCount: Long? = null
  private val operations = mutableListOf<String>()
  private val pendingMonumentIds = mutableListOf<String>()
  private var pendingCount: Long? = null

  @Before
  fun setUp() {
    every { auth.currentUser } returns user
    every { user.uid } returns "user-id"
    every { firestore.collection("publicProfiles") } returns profiles
    every { firestore.collection("users") } returns users
    every { profiles.document("user-id") } returns profileReference
    every { users.document("user-id") } returns userDocument
    every { userDocument.collection("verifiedMonuments") } returns monuments
    every { monuments.document(any()) } answers
        {
          monumentReferences.getOrPut(firstArg()) { mockk(firstArg()) }
        }
    every { transaction.get(any()) } answers { snapshotOf(firstArg()) }
    every { transaction.set(any(), any<Map<String, Any>>()) } answers
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
  }

  @Test
  fun transactionReadsProfileAndNeighbourhoodBeforeWriting() = runTest {
    repository.recordVerifiedMonument(eiffelTower)

    val expectedReads = eiffelTower.neighbourhood().map { "get ${it.id}" }.sorted()
    assertEquals(
        listOf("get profile") + expectedReads + listOf("set 48858_2294 {}", "update count 1"),
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
    discardPendingWrites()
  }

  private fun discardPendingWrites() {
    pendingMonumentIds.clear()
    pendingCount = null
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
