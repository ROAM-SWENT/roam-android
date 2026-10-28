/*
 * Portions of this code were generated with the help of GitHub Copilot.
 */

package com.github.roamswent.roam.model.profile

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ProfileRepositoryFirebaseTest {

  private lateinit var auth: FirebaseAuth
  private lateinit var repository: ProfileRepositoryFirebase

  @Before
  fun setUp() {
    auth = mock()
    repository = ProfileRepositoryFirebase(auth)
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

  /** Verifies that non-Google authentication providers use the generic account label. */
  @Test
  fun loadProfile_nonGoogleUser_usesAccountProvider() = runTest {
    val user = mock<FirebaseUser>()
    val provider = mock<UserInfo>()
    whenever(provider.providerId).thenReturn("password")
    whenever(user.providerData).thenReturn(listOf(provider))
    whenever(user.displayName).thenReturn("Traveler")
    whenever(user.email).thenReturn("traveler@example.com")
    whenever(auth.currentUser).thenReturn(user)

    val result = repository.loadProfile()

    assertTrue(result.isSuccess)
    assertEquals(UserProfile("Traveler", "traveler@example.com", "Account"), result.getOrNull())
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
}
