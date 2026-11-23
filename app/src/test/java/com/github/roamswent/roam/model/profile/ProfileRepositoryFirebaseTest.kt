/*
 * Portions of this code were generated with the help of GitHub Copilot.
 */

package com.github.roamswent.roam.model.profile

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserInfo
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ProfileRepositoryFirebaseTest {

  private lateinit var auth: FirebaseAuth
  private lateinit var firestore: FirebaseFirestore
  private lateinit var repository: ProfileRepositoryFirebase

  @Before
  fun setUp() {
    auth = mock()
    firestore = mock()
    repository = ProfileRepositoryFirebase(auth, firestore)
  }

  /** Verifies that Firebase profile data is mapped for a Google-authenticated user. */
  @Test
  fun loadProfile_googleUser_mapsProfileAndProvider() = runTest {
    val user = mock<FirebaseUser>()
    val googleProvider = mock<UserInfo>()
    whenever(googleProvider.providerId).thenReturn(GoogleAuthProvider.PROVIDER_ID)
    whenever(user.providerData).thenReturn(listOf(googleProvider))
    whenever(user.displayName).thenReturn("Victor")
    whenever(user.email).thenReturn("victor@epfl.ch")
    whenever(auth.currentUser).thenReturn(user)

    val result = repository.loadProfile()

    assertTrue(result.isSuccess)
    assertEquals(UserProfile("Victor", "victor@epfl.ch", "Google"), result.getOrNull())
  }

  /** Verifies that Firebase email/password users use the Email provider label. */
  @Test
  fun loadProfile_emailPasswordUser_usesEmailProvider() = runTest {
    val user = mock<FirebaseUser>()
    val provider = mock<UserInfo>()
    whenever(provider.providerId).thenReturn("password")
    whenever(user.providerData).thenReturn(listOf(provider))
    whenever(user.displayName).thenReturn("Traveler")
    whenever(user.email).thenReturn("traveler@example.com")
    whenever(auth.currentUser).thenReturn(user)

    val result = repository.loadProfile()

    assertTrue(result.isSuccess)
    assertEquals(UserProfile("Traveler", "traveler@example.com", "Email"), result.getOrNull())
  }

  @Test
  fun loadProfile_emailUser_mapsEmailProviderAndLocalNameFallback() = runTest {
    val user = mock<FirebaseUser>()
    val provider = mock<UserInfo>()
    whenever(provider.providerId).thenReturn(EmailAuthProvider.PROVIDER_ID)
    whenever(user.providerData).thenReturn(listOf(provider))
    whenever(user.displayName).thenReturn(null)
    whenever(user.email).thenReturn("traveler@example.com")
    whenever(auth.currentUser).thenReturn(user)

    val result = repository.loadProfile()

    assertTrue(result.isSuccess)
    assertEquals(UserProfile("traveler", "traveler@example.com", "Email"), result.getOrNull())
  }

  /** Verifies that absent Firebase profile values are represented by empty strings. */
  @Test
  fun loadProfile_missingValues_returnsEmptyStrings() = runTest {
    val user = mock<FirebaseUser>()
    whenever(user.providerData).thenReturn(emptyList())
    whenever(user.displayName).thenReturn(null)
    whenever(user.email).thenReturn(null)
    whenever(auth.currentUser).thenReturn(user)

    val result = repository.loadProfile()

    assertTrue(result.isSuccess)
    assertEquals(UserProfile("", "", "Account"), result.getOrNull())
  }

  /** Verifies that loading without an authenticated user returns a failure. */
  @Test
  fun loadProfile_withoutAuthenticatedUser_returnsFailure() = runTest {
    whenever(auth.currentUser).thenReturn(null)

    val result = repository.loadProfile()

    assertFalse(result.isSuccess)
    assertEquals(
        "You must be signed in to view your profile.",
        result.exceptionOrNull()?.message,
    )
  }

  @Test
  fun initializePrivateProfile_createsEmailAndTimestampWithoutOverwriting() = runTest {
    val user = mock<FirebaseUser>()
    val users = mock<CollectionReference>()
    val reference = mock<DocumentReference>()
    val snapshot = mock<DocumentSnapshot>()
    whenever(auth.currentUser).thenReturn(user)
    whenever(user.uid).thenReturn("user-id")
    whenever(user.email).thenReturn("traveler@example.com")
    whenever(firestore.collection("users")).thenReturn(users)
    whenever(users.document("user-id")).thenReturn(reference)
    whenever(reference.get()).thenReturn(Tasks.forResult(snapshot))
    whenever(snapshot.exists()).thenReturn(false)
    whenever(reference.set(any<Map<String, Any>>())).thenReturn(Tasks.forResult(null))

    val result = repository.initializePrivateProfile()

    assertTrue(result.isSuccess)
    val fieldsCaptor = argumentCaptor<Map<String, Any>>()
    verify(reference).set(fieldsCaptor.capture())
    assertEquals("traveler@example.com", fieldsCaptor.firstValue["email"])
    assertTrue(fieldsCaptor.firstValue.containsKey("registrationTimestamp"))
  }

  @Test
  fun initializePrivateProfile_existingDocumentIsUnchanged() = runTest {
    val user = mock<FirebaseUser>()
    val users = mock<CollectionReference>()
    val reference = mock<DocumentReference>()
    val snapshot = mock<DocumentSnapshot>()
    whenever(auth.currentUser).thenReturn(user)
    whenever(user.uid).thenReturn("user-id")
    whenever(user.email).thenReturn("traveler@example.com")
    whenever(firestore.collection("users")).thenReturn(users)
    whenever(users.document("user-id")).thenReturn(reference)
    whenever(reference.get()).thenReturn(Tasks.forResult(snapshot))
    whenever(snapshot.exists()).thenReturn(true)

    val result = repository.initializePrivateProfile()

    assertTrue(result.isSuccess)
    verify(reference, org.mockito.kotlin.never()).set(any<Map<String, Any>>())
  }

  @Test
  fun initializePrivateProfile_failsWithoutAuthenticatedUser() = runTest {
    whenever(auth.currentUser).thenReturn(null)

    val result = repository.initializePrivateProfile()

    assertFalse(result.isSuccess)
    assertEquals(
        "You must be signed in to view your profile.",
        result.exceptionOrNull()?.message,
    )
  }

  @Test
  fun initializePrivateProfile_failsWithoutEmail() = runTest {
    val user = mock<FirebaseUser>()
    whenever(auth.currentUser).thenReturn(user)
    whenever(user.email).thenReturn(null)

    val result = repository.initializePrivateProfile()

    assertFalse(result.isSuccess)
    assertEquals(
        "The signed-in user does not have an email.",
        result.exceptionOrNull()?.message,
    )
  }

  @Test
  fun initializePrivateProfile_mapsFirestoreFailure() = runTest {
    val user = mock<FirebaseUser>()
    val users = mock<CollectionReference>()
    whenever(auth.currentUser).thenReturn(user)
    whenever(user.uid).thenReturn("user-id")
    whenever(user.email).thenReturn("traveler@example.com")
    whenever(firestore.collection("users")).thenReturn(users)
    whenever(users.document("user-id")).thenThrow(IllegalStateException("Firestore unavailable"))

    val result = repository.initializePrivateProfile()

    assertFalse(result.isSuccess)
    assertEquals("Firestore unavailable", result.exceptionOrNull()?.message)
  }
}
