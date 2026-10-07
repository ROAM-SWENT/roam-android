/*
 * Portions of this code were generated and/or refined with the help of ChatGPT.
 * Portions of this code were generated with the help of GitHub Copilot.
 */

package com.github.roamswent.roam.model.authentication

import android.net.Uri
import android.os.Bundle
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GoogleSignInHelperTest {

  private val helper = DefaultGoogleSignInHelper()

  /**
   * Verifies that a Google ID token credential can be correctly extracted from the credential data
   * and that its main properties are preserved.
   */
  @Test
  fun extractIdTokenCredential_readsGoogleCredentialData() {
    val idToken =
        listOf(
                "eyJhbGciOiJub25lIn0",
                "eyJzdWIiOiJ1c2VyLWlkIiwiZW1haWwiOiJ1c2VyQGV4YW1wbGUuY29tIiwibmFtZSI6IkRpc3BsYXkgTm" +
                    "FtZSIsImdpdmVuX25hbWUiOiJHaXZlbiIsImZhbWlseV9uYW1lIjoiRmFtaWx5In0",
                "",
            )
            .joinToString(".")
    val original =
        GoogleIdTokenCredential(
            "user-id",
            idToken,
            "Display Name",
            "Family",
            "Given",
            Uri.parse("https://example.com/photo"),
            "+123",
        )

    val extracted = helper.extractIdTokenCredential(original.data)

    assertEquals(idToken, extracted.idToken)
    assertEquals("Display Name", extracted.displayName)
  }

  /**
   * Verifies that a Google ID token is converted into a Firebase authentication credential using
   * the Google authentication provider.
   */
  @Test
  fun toFirebaseCredential_createsGoogleCredential() {
    val credential = helper.toFirebaseCredential("token")

    assertEquals(GoogleAuthProvider.PROVIDER_ID, credential.provider)
    assertEquals("google.com", credential.signInMethod)
  }

  /**
   * Verifies that extracting a Google ID token credential fails when the provided credential data
   * does not contain a token.
   */
  @Test(expected = Exception::class)
  fun extractIdTokenCredential_rejectsMissingToken() {
    helper.extractIdTokenCredential(Bundle())
  }
}
