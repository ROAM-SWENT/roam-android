package com.github.se.bootcamp.ui.authentication

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.bootcamp.BootcampApp
import com.github.se.bootcamp.ui.authentication.SignInScreenTestTags.APP_LOGO
import com.github.se.bootcamp.ui.authentication.SignInScreenTestTags.LOGIN_BUTTON
import com.github.se.bootcamp.ui.authentication.SignInScreenTestTags.LOGIN_TITLE
import com.github.se.bootcamp.ui.overview.OverviewScreenTestTags
import com.github.se.bootcamp.utils.BootcampMilestone
import com.github.se.bootcamp.utils.FakeCredentialManager
import com.github.se.bootcamp.utils.FakeJwtGenerator
import com.github.se.bootcamp.utils.FirebaseEmulator
import com.github.se.bootcamp.utils.FirestoreBootcampTest
import com.github.se.bootcamp.utils.UI_WAIT_TIMEOUT
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthenticationTest : FirestoreBootcampTest(BootcampMilestone.B3) {

  @get:Rule val composeTestRule = createComposeRule()

  @Before
  override fun setUp() {
    super.setUp()
    FirebaseEmulator.auth.signOut()
  }

  @Test
  fun google_sign_in_is_configured() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    val resourceId =
        context.resources.getIdentifier("default_web_client_id", "string", context.packageName)

    // Skip test if resource doesn't exist (useful for CI environments)
    assumeTrue("Google Sign-In not configured - skipping test", resourceId != 0)

    val clientId = context.getString(resourceId)
    assertTrue(
        "Invalid Google client ID format: $clientId", clientId.endsWith(".googleusercontent.com"))
  }

  @Test
  fun signInScreen_componentsAreDisplayed() {
    composeTestRule.setContent { SignInScreen() }

    composeTestRule.onNodeWithTag(APP_LOGO).assertIsDisplayed()
    composeTestRule.onNodeWithTag(LOGIN_TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(LOGIN_BUTTON).assertIsDisplayed()
  }

  @Test
  fun loggedOut_showsSignInScreen() {
    // Ensure we are logged out
    assert(FirebaseEmulator.auth.currentUser == null)

    // Set the content to the full app
    composeTestRule.setContent { BootcampApp() }

    // Check that the sign-in screen is displayed
    composeTestRule.onNodeWithTag(LOGIN_BUTTON).assertIsDisplayed()
  }

  @Test
  fun canSignInWithGoogle() {
    val fakeGoogleIdToken =
        FakeJwtGenerator.createFakeGoogleIdToken("12345", email = "test@example.com")

    val fakeCredentialManager = FakeCredentialManager.create(fakeGoogleIdToken)

    composeTestRule.setContent { BootcampApp(credentialManager = fakeCredentialManager) }
    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.LOGIN_BUTTON)
        .assertIsDisplayed()
        .performClick()

    composeTestRule.waitUntil(UI_WAIT_TIMEOUT) {
      composeTestRule
          .onAllNodesWithTag(OverviewScreenTestTags.TODO_LIST)
          .fetchSemanticsNodes()
          .isNotEmpty() ||
          composeTestRule
              .onAllNodesWithTag(OverviewScreenTestTags.EMPTY_TODO_LIST_MSG)
              .fetchSemanticsNodes()
              .isNotEmpty()
    }

    composeTestRule.checkOverviewScreenIsDisplayed()
  }

  @Test
  fun canSignInWithExistingAccount() {
    val email = "existing@test.com"
    val fakeIdToken =
        FakeJwtGenerator.createFakeGoogleIdToken(name = "Existing User", email = email)
    val firebaseCred = GoogleAuthProvider.getCredential(fakeIdToken, null)
    runTest {
      val user = FirebaseEmulator.auth.signInWithCredential(firebaseCred).await().user

      assertNotNull(user)
    }

    runBlocking {
      repository.addTodo(todo1)
      repository.addTodo(todo2)
    }

    FirebaseEmulator.auth.signOut()

    composeTestRule.setContent {
      BootcampApp(credentialManager = FakeCredentialManager.create(fakeIdToken))
    }

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.LOGIN_BUTTON)
        .assertIsDisplayed()
        .performClick()

    composeTestRule.waitUntil(UI_WAIT_TIMEOUT) { FirebaseEmulator.auth.currentUser != null }

    assertEquals(email, FirebaseEmulator.auth.currentUser!!.email)
    composeTestRule.waitUntil(UI_WAIT_TIMEOUT) {
      composeTestRule
          .onAllNodesWithTag(OverviewScreenTestTags.TODO_LIST)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeTestRule
        .onNodeWithTag(OverviewScreenTestTags.getTestTagForTodoItem(todo1))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(OverviewScreenTestTags.getTestTagForTodoItem(todo2))
        .assertIsDisplayed()
  }
}
