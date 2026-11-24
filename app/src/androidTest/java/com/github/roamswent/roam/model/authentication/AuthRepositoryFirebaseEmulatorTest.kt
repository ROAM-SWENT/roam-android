// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.model.authentication

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests using Firebase Authentication Emulator.
 *
 * Requirements:
 * - Running Android emulator
 * - Firebase Auth Emulator running on port 9099
 *
 * To run you may need to follow the run the following commands:
 * 1. firebase emulators:start --project demo-roam-rules --only auth
 * 2. adb reverse tcp:9099 tcp:9099
 * 3. ./gradlew :app:connectedDebugAndroidTest
 *
 * Tests use a local Firebase project and do not affect production.
 */
@RunWith(AndroidJUnit4::class)
class AuthRepositoryFirebaseEmulatorTest {

  private val password = "SecurePassword123!"

  @Test
  fun registration_createsUnverifiedUser() = runBlocking {
    withClient { client ->
      val email = uniqueEmail()

      val user = client.repository.signUpWithEmail(email, password).getOrThrow()

      assertEquals(email, user.email)
      assertEquals(user.uid, client.repository.currentUser()?.uid)
      assertFalse(user.isEmailVerified)
      assertFalse(client.repository.isEmailVerified())

      // Check that Firebase generates the verification email.
      client.repository.sendEmailVerification().getOrThrow()

      assertTrue(verificationCodeFor(email).isNotBlank())
    }
  }

  @Test
  fun emailVerification_updatesStatusAfterVerification() = runBlocking {
    withClient { client ->
      val email = uniqueEmail()

      client.repository.signUpWithEmail(email, password).getOrThrow()

      // Initially, the email must not be verified.
      assertFalse(client.repository.reloadEmailVerificationStatus().getOrThrow())

      client.repository.sendEmailVerification().getOrThrow()

      // Retrieve the verification code from Firebase Auth Emulator.
      val code = verificationCodeFor(email)
      assertNotNull(code)

      // Simulate the user confirming their email.
      client.auth.applyActionCode(code).await()

      // Reload Firebase user state.
      assertTrue(client.repository.reloadEmailVerificationStatus().getOrThrow())
      assertTrue(client.repository.isEmailVerified())
    }
  }

  @Test
  fun signIn_withVerifiedAccountSucceeds() = runBlocking {
    withClient { client ->
      val email = uniqueEmail()

      val registeredUser = client.repository.signUpWithEmail(email, password).getOrThrow()

      client.repository.sendEmailVerification().getOrThrow()

      val code = verificationCodeFor(email)
      client.auth.applyActionCode(code).await()

      assertTrue(client.repository.reloadEmailVerificationStatus().getOrThrow())

      // Sign out before testing sign-in.
      client.repository.signOut().getOrThrow()

      assertNull(client.repository.currentUser())

      // Sign back in using email and password.
      val signedInUser = client.repository.signInWithEmail(email, password).getOrThrow()

      assertEquals(registeredUser.uid, signedInUser.uid)
      assertEquals(email, signedInUser.email)
      assertTrue(client.repository.reloadEmailVerificationStatus().getOrThrow())
    }
  }

  @Test
  fun signIn_withWrongPasswordFails() = runBlocking {
    withClient { client ->
      val email = uniqueEmail()

      client.repository.signUpWithEmail(email, password).getOrThrow()
      client.repository.signOut().getOrThrow()

      val result = client.repository.signInWithEmail(email, "WrongPassword123!")

      assertTrue(result.isFailure)
      assertNull(client.repository.currentUser())
    }
  }

  private suspend fun withClient(
      block: suspend (EmulatorClient) -> Unit,
  ) {
    val client = emulatorClient()

    try {
      block(client)
    } finally {
      client.auth.signOut()
    }
  }

  private fun emulatorClient(): EmulatorClient {
    val options =
        FirebaseOptions.Builder()
            .setApplicationId("1:123456789:android:1234567890abcdef")
            .setApiKey("emulator-key")
            .setProjectId("demo-roam-rules")
            .build()

    val app =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            options,
            "email-auth-${UUID.randomUUID()}",
        )

    val auth = FirebaseAuth.getInstance(app).apply { useEmulator("127.0.0.1", 9099) }

    val repository = AuthRepositoryFirebase(auth)

    return EmulatorClient(app, auth, repository)
  }

  private fun verificationCodeFor(email: String): String {
    val endpoint = "http://127.0.0.1:9099/emulator/v1/projects/demo-roam-rules/oobCodes"

    repeat(10) {
      val connection =
          (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3000
            readTimeout = 3000
          }

      try {
        check(connection.responseCode == 200) {
          "Firebase Auth Emulator returned HTTP ${connection.responseCode}"
        }

        val response = connection.inputStream.bufferedReader().use { it.readText() }

        val codes = JSONObject(response).getJSONArray("oobCodes")

        for (i in 0 until codes.length()) {
          val item = codes.getJSONObject(i)

          if (item.optString("email") == email && item.optString("requestType") == "VERIFY_EMAIL") {
            return item.getString("oobCode")
          }
        }
      } finally {
        connection.disconnect()
      }

      Thread.sleep(200)
    }

    error("No verification code found for $email")
  }

  private fun uniqueEmail(): String =
      "roam-test-${UUID.randomUUID().toString().replace("-", "")}@example.com"

  private data class EmulatorClient(
      val app: FirebaseApp,
      val auth: FirebaseAuth,
      val repository: AuthRepositoryFirebase,
  )
}
