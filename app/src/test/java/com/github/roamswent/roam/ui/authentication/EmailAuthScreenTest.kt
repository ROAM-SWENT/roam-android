package com.github.roamswent.roam.ui.authentication

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.github.roamswent.roam.R
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmailAuthScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()

  private val viewModel: SignInViewModel = mock()

  @Test
  fun signUpDisplaysErrorWithoutFormatArgument() {
    val state = AuthUIState(errorMsg = AuthError(messageRes = R.string.sign_up_title))

    whenever(viewModel.uiState).thenReturn(MutableStateFlow(state))

    composeTestRule.setContent {
      MaterialTheme {
        SignUpScreen(
            authViewModel = viewModel,
            onBack = {},
            onEmailVerification = {},
        )
      }
    }

    composeTestRule.onAllNodesWithText(context.getString(R.string.sign_up_title))[1].assertExists()
  }

  @Test
  fun emailVerificationDisplaysErrorWithoutFormatArgument() {
    val state = AuthUIState(errorMsg = AuthError(messageRes = R.string.sign_up_title))

    whenever(viewModel.uiState).thenReturn(MutableStateFlow(state))

    composeTestRule.setContent {
      MaterialTheme {
        EmailVerificationScreen(
            authViewModel = viewModel,
            onVerified = {},
            onBack = {},
        )
      }
    }

    composeTestRule.onNodeWithText(context.getString(R.string.sign_up_title)).assertExists()
  }
}
