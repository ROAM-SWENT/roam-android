/*
 * Portions of this code were generated and/or refined with the help of ChatGPT.
 * Portions of this code were generated with the help of GitHub Copilot.
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.authentication

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.credentials.CredentialManager
import androidx.test.core.app.ApplicationProvider
import com.github.roamswent.roam.R
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SignInScreenRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  /**
   * Verifies that the Google sign-in button is displayed in the initial state and that clicking it
   * triggers the ViewModel's sign-in function.
   */
  @Test
  fun initialState_displaysAndInvokesGoogleSignIn() {
    val viewModel = mock<SignInViewModel>()
    whenever(viewModel.uiState).thenReturn(MutableStateFlow(AuthUIState()))

    composeTestRule.setContent {
      SignInScreen(authViewModel = viewModel, credentialManager = mock<CredentialManager>())
    }

    composeTestRule
        .onNodeWithText(context.getString(R.string.sign_in_google_button))
        .assertIsDisplayed()
        .performClick()
    verify(viewModel).signIn(any(), any())
  }

  /** Verifies that the sign-in screen displays its branding and supporting copy. */
  @Test
  fun initialState_displaysBrandingAndTerms() {
    val viewModel = mock<SignInViewModel>()
    whenever(viewModel.uiState).thenReturn(MutableStateFlow(AuthUIState()))

    composeTestRule.setContent { SignInScreen(authViewModel = viewModel) }

    composeTestRule
        .onNodeWithContentDescription(context.getString(R.string.sign_in_logo_description))
        .assertIsDisplayed()
    composeTestRule.onNodeWithText(context.getString(R.string.sign_in_title)).assertIsDisplayed()
    composeTestRule.onNodeWithText(context.getString(R.string.sign_in_tagline)).assertIsDisplayed()
    composeTestRule.onNodeWithText(context.getString(R.string.sign_in_terms)).assertIsDisplayed()
  }

  /**
   * Verifies that the Google sign-in button is hidden while the authentication process is loading.
   */
  @Test
  fun loadingState_hidesSignInButton() {
    val viewModel = mock<SignInViewModel>()
    whenever(viewModel.uiState).thenReturn(MutableStateFlow(AuthUIState(isLoading = true)))

    composeTestRule.setContent { SignInScreen(authViewModel = viewModel) }

    assertTrue(
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.sign_in_google_button))
            .fetchSemanticsNodes()
            .isEmpty()
    )
  }

  /** Verifies that an authentication error causes the ViewModel's error message to be cleared. */
  @Test
  fun errorState_clearsErrorMessage() {
    val viewModel = mock<SignInViewModel>()
    whenever(viewModel.uiState)
        .thenReturn(MutableStateFlow(AuthUIState(errorMsg = "Unable to sign in")))

    composeTestRule.setContent { SignInScreen(authViewModel = viewModel) }
    composeTestRule.waitForIdle()

    verify(viewModel).clearErrorMsg()
  }

  /**
   * Verifies that the signed-in callback is invoked when the authentication state contains an
   * authenticated user.
   */
  @Test
  fun signedInState_invokesCallback() {
    val viewModel = mock<SignInViewModel>()
    whenever(viewModel.uiState).thenReturn(MutableStateFlow(AuthUIState(user = mock())))
    var callbackCount = 0

    composeTestRule.setContent {
      SignInScreen(authViewModel = viewModel, onSignedIn = { callbackCount++ })
    }
    composeTestRule.waitForIdle()

    assertEquals(1, callbackCount)
  }

  /**
   * Verifies that the screen responds correctly to authentication state transitions by clearing
   * errors, invoking the signed-in callback, and hiding the sign-in button while loading.
   */
  @Test
  fun stateTransitions_runConditionalEffects() {
    val viewModel = mock<SignInViewModel>()
    val state = MutableStateFlow(AuthUIState())
    whenever(viewModel.uiState).thenReturn(state)
    var navigationCount = 0

    composeTestRule.setContent {
      SignInScreen(
          authViewModel = viewModel,
          onSignedIn = { navigationCount++ },
      )
    }
    composeTestRule.waitForIdle()

    state.value = AuthUIState(errorMsg = "Unable to sign in")
    composeTestRule.waitForIdle()
    state.value = AuthUIState(user = mock())
    composeTestRule.waitForIdle()
    state.value = AuthUIState(isLoading = true)
    composeTestRule.waitForIdle()

    verify(viewModel).clearErrorMsg()
    assertEquals(1, navigationCount)
    assertTrue(
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.sign_in_google_button))
            .fetchSemanticsNodes()
            .isEmpty()
    )
  }
}
