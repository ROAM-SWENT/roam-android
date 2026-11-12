/*
 * Portions of this code were generated and/or refined with the help of ChatGPT.
 * Portions of this code were generated with the help of GitHub Copilot.
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.authentication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.credentials.CredentialManager
import androidx.test.core.app.ApplicationProvider
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import com.github.roamswent.roam.ui.theme.ScreenBackground
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
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
      SignInScreen(
          authViewModel = viewModel,
          credentialManager = mock<CredentialManager>(),
      )
    }

    composeTestRule.onNodeWithTag(C.Tag.sign_in_google_button).assertIsDisplayed().performClick()
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
            .onAllNodesWithTag(C.Tag.sign_in_google_button)
            .fetchSemanticsNodes()
            .isEmpty()
    )
  }

  @Test
  fun restoringSession_hidesSignInForm() {
    val viewModel = mock<SignInViewModel>()
    whenever(viewModel.uiState).thenReturn(MutableStateFlow(AuthUIState(isRestoringSession = true)))

    composeTestRule.setContent { SignInScreen(authViewModel = viewModel) }

    composeTestRule.onNodeWithTag(C.Tag.sign_in_screen_container).assertIsDisplayed()
    assertTrue(
        composeTestRule
            .onAllNodesWithTag(C.Tag.sign_in_google_button)
            .fetchSemanticsNodes()
            .isEmpty()
    )
    assertTrue(
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.sign_in_title))
            .fetchSemanticsNodes()
            .isEmpty()
    )
  }

  /** Verifies that an authentication error causes the ViewModel's error message to be cleared. */
  @Test
  fun errorState_clearsErrorMessage() {
    val viewModel = mock<SignInViewModel>()
    whenever(viewModel.uiState)
        .thenReturn(
            MutableStateFlow(
                AuthUIState(
                    errorMsg = AuthError(R.string.sign_in_error_unexpected, "Unable to sign in")
                )
            )
        )

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
      SignInScreen(
          authViewModel = viewModel,
          onSignedIn = { callbackCount++ },
      )
    }
    composeTestRule.waitForIdle()

    assertEquals(1, callbackCount)
  }

  @Test
  fun signedInState_hidesSignInFormWhileProfileLoads() {
    val viewModel = mock<SignInViewModel>()
    whenever(viewModel.uiState).thenReturn(MutableStateFlow(AuthUIState(user = mock())))

    composeTestRule.setContent { SignInScreen(authViewModel = viewModel) }

    composeTestRule.onNodeWithTag(C.Tag.sign_in_screen_container).assertIsDisplayed()
    assertTrue(
        composeTestRule
            .onAllNodesWithTag(C.Tag.sign_in_google_button)
            .fetchSemanticsNodes()
            .isEmpty()
    )
    assertTrue(
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.sign_in_title))
            .fetchSemanticsNodes()
            .isEmpty()
    )
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

    state.value =
        AuthUIState(errorMsg = AuthError(R.string.sign_in_error_unexpected, "Unable to sign in"))
    composeTestRule.waitForIdle()
    state.value = AuthUIState(user = mock())
    composeTestRule.waitForIdle()
    state.value = AuthUIState(isLoading = true)
    composeTestRule.waitForIdle()

    verify(viewModel).clearErrorMsg()
    assertEquals(1, navigationCount)
    assertTrue(
        composeTestRule
            .onAllNodesWithTag(C.Tag.sign_in_google_button)
            .fetchSemanticsNodes()
            .isEmpty()
    )
  }

  @Test
  fun lightTheme_preservesExistingBackgroundAndLogoColors() {
    val viewModel = mock<SignInViewModel>()
    whenever(viewModel.uiState).thenReturn(MutableStateFlow(AuthUIState()))

    composeTestRule.setContent {
      SampleAppTheme(darkTheme = false, dynamicColor = false) {
        SignInScreen(authViewModel = viewModel)
      }
    }

    val background =
        composeTestRule
            .onNodeWithTag(C.Tag.sign_in_screen_container)
            .captureToImage()
            .asAndroidBitmap()
    val logo =
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.sign_in_logo_description))
            .captureToImage()
            .asAndroidBitmap()

    assertEquals(ScreenBackground.toArgb(), background.getPixel(0, 0))
    assertTrue(containsColor(logo, AndroidColor.rgb(0x2E, 0x4D, 0x4C)))
  }

  @Test
  fun darkTheme_usesDarkBackgroundAndContrastingLogo() {
    val viewModel = mock<SignInViewModel>()
    whenever(viewModel.uiState).thenReturn(MutableStateFlow(AuthUIState()))
    var expectedBackground = Color.Unspecified
    var expectedLogoColor = Color.Unspecified

    composeTestRule.setContent {
      SampleAppTheme(darkTheme = true, dynamicColor = false) {
        expectedBackground = MaterialTheme.colorScheme.background
        expectedLogoColor = MaterialTheme.colorScheme.onSurface
        SignInScreen(authViewModel = viewModel)
      }
    }

    val background =
        composeTestRule
            .onNodeWithTag(C.Tag.sign_in_screen_container)
            .captureToImage()
            .asAndroidBitmap()
    val logo =
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.sign_in_logo_description))
            .captureToImage()
            .asAndroidBitmap()

    assertEquals(expectedBackground.toArgb(), background.getPixel(0, 0))
    assertTrue(containsColor(logo, expectedLogoColor.toArgb()))
  }

  private fun containsColor(bitmap: Bitmap, color: Int): Boolean {
    for (y in 0 until bitmap.height) {
      for (x in 0 until bitmap.width) {
        if (bitmap.getPixel(x, y) == color) return true
      }
    }
    return false
  }
}
