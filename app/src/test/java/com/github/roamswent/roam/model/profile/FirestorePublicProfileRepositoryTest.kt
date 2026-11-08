// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.model.profile

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Transaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FirestorePublicProfileRepositoryTest {
  private val firestore = mockk<FirebaseFirestore>()
  private val auth = mockk<FirebaseAuth>()
  private val user = mockk<FirebaseUser>()
  private val profiles = mockk<CollectionReference>()
  private val claims = mockk<CollectionReference>()
  private val profileReference = mockk<DocumentReference>()
  private val repository = FirestorePublicProfileRepository(firestore, auth)

  @Before
  fun setUp() {
    every { auth.currentUser } returns user
    every { user.uid } returns "user-id"
    every { firestore.collection("publicProfiles") } returns profiles
    every { firestore.collection("usernameClaims") } returns claims
    every { profiles.document("user-id") } returns profileReference
  }

  @Test
  fun registerUsername_validatesUsernameBeforeWriting() = runTest {
    val error = captureFailure { repository.registerUsername("ab-é") }

    assertTrue(error is PublicProfileException.InvalidUsername)
  }

  @Test
  fun registerUsername_withoutAuthenticatedUserReturnsTypedError() = runTest {
    every { auth.currentUser } returns null

    val error = captureFailure { repository.registerUsername("traveler") }

    assertTrue(error is PublicProfileException.UnauthenticatedUser)
  }

  @Test
  fun registerUsername_claimedNameReturnsTypedError() = runTest {
    val claim = mockk<DocumentReference>()
    val transaction = mockk<Transaction>()
    val profileSnapshot = mockk<DocumentSnapshot>()
    val claimSnapshot = mockk<DocumentSnapshot>()
    every { claims.document("traveler") } returns claim
    every { transaction.get(profileReference) } returns profileSnapshot
    every { transaction.get(claim) } returns claimSnapshot
    every { profileSnapshot.exists() } returns false
    every { claimSnapshot.exists() } returns true
    runTransactionWith(transaction)

    val error = captureFailure { repository.registerUsername("TRAVELER") }

    assertTrue(error.toString(), error is PublicProfileException.UsernameTaken)
  }

  @Test
  fun registerUsername_createsProfileAndNormalizedClaimInOneTransaction() = runTest {
    val claim = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)
    val profileSnapshot = mockk<DocumentSnapshot>()
    val claimSnapshot = mockk<DocumentSnapshot>()
    every { claims.document("traveler_1") } returns claim
    every { transaction.get(profileReference) } returns profileSnapshot
    every { transaction.get(claim) } returns claimSnapshot
    every { profileSnapshot.exists() } returns false
    every { claimSnapshot.exists() } returns false
    runTransactionWith(transaction)

    val result = repository.registerUsername("Traveler_1")

    assertEquals(PublicProfile("user-id", "Traveler_1"), result)
    verify {
      transaction.set(
          profileReference,
          mapOf("uid" to "user-id", "username" to "Traveler_1", "usernameLower" to "traveler_1"),
      )
    }
    verify { transaction.set(claim, mapOf("uid" to "user-id")) }
  }

  @Test
  fun registerUsername_firestoreFailureIsDistinguishable() = runTest {
    val failure = IllegalStateException("Firestore unavailable")
    every { profiles.document("user-id").get() } returns Tasks.forException(failure)

    val error = captureFailure { repository.getCurrentProfile() }

    assertTrue(error is PublicProfileException.FirestoreFailure)
    assertTrue(error.cause === failure)
  }

  @Test
  fun renameUsername_releasesOldClaimAndCreatesNewClaimAtomically() = runTest {
    val oldClaim = mockk<DocumentReference>()
    val newClaim = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)
    val profileSnapshot = profileSnapshot("user-id", "OldName", "oldname")
    val oldClaimSnapshot = mockk<DocumentSnapshot>()
    val newClaimSnapshot = mockk<DocumentSnapshot>()
    every { claims.document("newname") } returns newClaim
    every { claims.document("oldname") } returns oldClaim
    every { transaction.get(profileReference) } returns profileSnapshot
    every { transaction.get(newClaim) } returns newClaimSnapshot
    every { transaction.get(oldClaim) } returns oldClaimSnapshot
    every { newClaimSnapshot.exists() } returns false
    every { oldClaimSnapshot.getString("uid") } returns "user-id"
    runTransactionWith(transaction)

    val result = repository.renameUsername("NewName")

    assertEquals(PublicProfile("user-id", "NewName"), result)
    verify { transaction.delete(oldClaim) }
    verify { transaction.set(newClaim, mapOf("uid" to "user-id")) }
    verify {
      transaction.update(
          profileReference,
          mapOf("uid" to "user-id", "username" to "NewName", "usernameLower" to "newname"),
      )
    }
  }

  @Test
  fun renameUsername_claimedNameReturnsTypedErrorWithoutReleasingCurrentClaim() = runTest {
    val newClaim = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)
    val profileSnapshot = profileSnapshot("user-id", "OldName", "oldname")
    val newClaimSnapshot = mockk<DocumentSnapshot>()
    every { claims.document("newname") } returns newClaim
    every { transaction.get(profileReference) } returns profileSnapshot
    every { transaction.get(newClaim) } returns newClaimSnapshot
    every { newClaimSnapshot.exists() } returns true
    runTransactionWith(transaction)

    val error = captureFailure { repository.renameUsername("NewName") }

    assertTrue(error.toString(), error is PublicProfileException.UsernameTaken)
    verify(exactly = 0) { transaction.delete(any()) }
  }

  @Test
  fun renameUsername_sameNameWithDifferentCapitalizationUpdatesOnlyProfile() = runTest {
    val transaction = mockk<Transaction>(relaxed = true)
    every { transaction.get(profileReference) } returns
        profileSnapshot("user-id", "Traveler", "traveler")
    runTransactionWith(transaction)

    val result = repository.renameUsername("TRAVELER")

    assertEquals(PublicProfile("user-id", "TRAVELER"), result)
    verify { transaction.update(profileReference, "username", "TRAVELER") }
    verify(exactly = 0) { transaction.delete(any()) }
    verify(exactly = 0) { transaction.set(any(), any<Map<String, Any>>()) }
  }

  @Test
  fun searchProfiles_normalizesPrefixAndLimitsQueryToTwenty() = runTest {
    val query = mockk<Query>()
    val snapshot = mockk<QuerySnapshot>()
    every { profiles.orderBy("usernameLower") } returns query
    every { query.startAt("trav") } returns query
    every { query.endAt("trav\uf8ff") } returns query
    every { query.limit(20) } returns query
    every { query.get() } returns Tasks.forResult(snapshot)
    every { snapshot.documents } returns emptyList()

    val result = repository.searchProfiles("TRAV")

    assertTrue(result.isEmpty())
    verify { query.limit(20) }
  }

  @Test
  fun getCurrentProfile_returnsNullWhenNotRegistered() = runTest {
    val snapshot = mockk<DocumentSnapshot>()
    every { profileReference.get() } returns Tasks.forResult(snapshot)
    every { snapshot.exists() } returns false

    assertNull(repository.getCurrentProfile())
  }

  @Test
  fun registerUsername_existingProfileRejectsNewUsername() = runTest {
    val claim = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)
    val profileSnapshot = profileSnapshot("user-id", "Staross123", "staross123")
    val claimSnapshot = mockk<DocumentSnapshot>()

    every { claims.document("alex") } returns claim
    every { transaction.get(profileReference) } returns profileSnapshot
    every { transaction.get(claim) } returns claimSnapshot
    runTransactionWith(transaction)

    val error = captureFailure { repository.registerUsername("Alex") }

    assertTrue(error is PublicProfileException.UsernameAlreadyRegistered)
    verify(exactly = 0) { transaction.set(any(), any<Map<String, Any>>()) }
  }

  @Test
  fun searchProfiles_returnsMatchingPublicProfiles() = runTest {
    val query = mockk<Query>()
    val snapshot = mockk<QuerySnapshot>()

    val firstProfile = profileSnapshot("user-1", "Staross123", "staross123")
    val secondProfile = profileSnapshot("user-2", "StarKing", "starking")

    every { profiles.orderBy("usernameLower") } returns query
    every { query.startAt("star") } returns query
    every { query.endAt("star\uf8ff") } returns query
    every { query.limit(20) } returns query
    every { query.get() } returns Tasks.forResult(snapshot)
    every { snapshot.documents } returns listOf(firstProfile, secondProfile)

    val result = repository.searchProfiles("STAR")

    assertEquals(
        listOf(
            PublicProfile("user-1", "Staross123"),
            PublicProfile("user-2", "StarKing"),
        ),
        result,
    )
  }

  @Test
  fun registerUsername_sameUsernameReturnsExistingProfile() = runTest {
    val claim = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)

    every { claims.document("traveler") } returns claim
    every { transaction.get(profileReference) } returns
        profileSnapshot("user-id", "Traveler", "traveler")
    every { transaction.get(claim) } returns mockk<DocumentSnapshot>()
    runTransactionWith(transaction)

    val result = repository.registerUsername("TRAVELER")

    assertEquals(PublicProfile("user-id", "Traveler"), result)
    verify(exactly = 0) { transaction.set(any(), any<Map<String, Any>>()) }
  }

  @Test
  fun renameUsername_withoutProfileReturnsTypedError() = runTest {
    val transaction = mockk<Transaction>(relaxed = true)
    val snapshot = mockk<DocumentSnapshot>()

    every { transaction.get(profileReference) } returns snapshot
    every { snapshot.exists() } returns false
    runTransactionWith(transaction)

    val error = captureFailure { repository.renameUsername("NewName") }

    assertTrue(error is PublicProfileException.ProfileNotRegistered)
    verify(exactly = 0) { transaction.set(any(), any<Map<String, Any>>()) }
  }

  @Test
  fun renameUsername_sameUsernameKeepsExistingProfile() = runTest {
    val transaction = mockk<Transaction>(relaxed = true)

    every { transaction.get(profileReference) } returns
        profileSnapshot("user-id", "Traveler", "traveler")
    runTransactionWith(transaction)

    val result = repository.renameUsername("Traveler")

    assertEquals(PublicProfile("user-id", "Traveler"), result)
    verify(exactly = 0) { transaction.delete(any()) }
    verify(exactly = 0) { transaction.set(any(), any<Map<String, Any>>()) }
    verify(exactly = 0) { transaction.update(profileReference, "username", "Traveler") }
  }

  @Test
  fun getCurrentProfile_returnsExistingProfile() = runTest {
    val snapshot = profileSnapshot("user-id", "Traveler", "traveler")
    every { profileReference.get() } returns Tasks.forResult(snapshot)

    val result = repository.getCurrentProfile()

    assertEquals(PublicProfile("user-id", "Traveler"), result)
  }

  @Test
  fun getCurrentProfile_rejectsMalformedProfile() = runTest {
    val snapshot = profileSnapshot("user-id", "Traveler", "wrongname")
    every { profileReference.get() } returns Tasks.forResult(snapshot)

    val error = captureFailure { repository.getCurrentProfile() }

    assertTrue(error is PublicProfileException.FirestoreFailure)
    assertTrue(error.cause is IllegalStateException)
  }

  @Test
  fun renameUsername_missingOldClaimFailsWithoutWriting() = runTest {
    val transaction = mockk<Transaction>(relaxed = true)
    val newClaim = mockk<DocumentReference>()
    val oldClaim = mockk<DocumentReference>()
    val newClaimSnapshot = mockk<DocumentSnapshot>()
    val oldClaimSnapshot = mockk<DocumentSnapshot>()

    every { claims.document("newname") } returns newClaim
    every { claims.document("oldname") } returns oldClaim

    every { transaction.get(profileReference) } returns
        profileSnapshot("user-id", "OldName", "oldname")
    every { transaction.get(newClaim) } returns newClaimSnapshot
    every { transaction.get(oldClaim) } returns oldClaimSnapshot

    every { newClaimSnapshot.exists() } returns false
    every { oldClaimSnapshot.getString("uid") } returns null

    runTransactionWith(transaction)

    val error = captureFailure { repository.renameUsername("NewName") }

    assertTrue(error is PublicProfileException.FirestoreFailure)
    verify(exactly = 0) { transaction.delete(any()) }
    verify(exactly = 0) { transaction.set(any(), any<Map<String, Any>>()) }
  }

  private fun profileSnapshot(uid: String, username: String, usernameLower: String) =
      mockk<DocumentSnapshot> {
        every { exists() } returns true
        every { getString("uid") } returns uid
        every { getString("username") } returns username
        every { getString("usernameLower") } returns usernameLower
      }

  private fun runTransactionWith(transaction: Transaction) {
    every { firestore.runTransaction<PublicProfile>(any()) } answers
        {
          val function = firstArg<Transaction.Function<PublicProfile>>()
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
