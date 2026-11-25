/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.authentication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C

@Composable
fun SignUpScreen(
    authViewModel: SignInViewModel,
    onBack: () -> Unit,
    onEmailVerification: () -> Unit,
) {
  val uiState by authViewModel.uiState.collectAsState()
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var confirmation by remember { mutableStateOf("") }

  LaunchedEffect(uiState.user) {
    if (isUnverifiedEmailPasswordUser(uiState.user)) onEmailVerification()
  }

  Scaffold(
      modifier = Modifier.fillMaxSize().testTag(C.Tag.sign_up_screen_container),
  ) { padding ->
    Column(
        modifier = Modifier.fillMaxSize().imePadding().padding(padding).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
      Text(stringResource(R.string.sign_up_title), style = MaterialTheme.typography.headlineSmall)
      Spacer(Modifier.height(24.dp))
      OutlinedTextField(
          value = email,
          onValueChange = { email = it },
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.sign_up_email),
          label = { Text(stringResource(R.string.sign_in_email_label)) },
          singleLine = true,
          enabled = !uiState.isLoading,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
      )
      Spacer(Modifier.height(8.dp))
      OutlinedTextField(
          value = password,
          onValueChange = { password = it },
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.sign_up_password),
          label = { Text(stringResource(R.string.sign_in_password_label)) },
          singleLine = true,
          enabled = !uiState.isLoading,
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
      )
      Spacer(Modifier.height(8.dp))
      OutlinedTextField(
          value = confirmation,
          onValueChange = { confirmation = it },
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.sign_up_password_confirmation),
          label = { Text(stringResource(R.string.sign_up_password_confirmation_label)) },
          singleLine = true,
          enabled = !uiState.isLoading,
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
      )
      uiState.errorMsg?.let { error ->
        Spacer(Modifier.height(8.dp))
        Text(
            text =
                error.formatArg?.let { stringResource(error.messageRes, it) }
                    ?: stringResource(error.messageRes),
            color = MaterialTheme.colorScheme.error,
        )
      }
      Spacer(Modifier.height(16.dp))
      Button(
          onClick = { authViewModel.signUpWithEmail(email, password, confirmation) },
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.sign_up_submit),
          enabled = !uiState.isLoading,
      ) {
        if (uiState.isLoading) {
          CircularProgressIndicator()
        } else {
          Text(stringResource(R.string.sign_up_submit))
        }
      }
      TextButton(onClick = onBack) { Text(stringResource(R.string.sign_up_back)) }
    }
  }
}

@Composable
fun EmailVerificationScreen(
    authViewModel: SignInViewModel,
    onVerified: () -> Unit,
    onBack: () -> Unit,
) {
  val uiState by authViewModel.uiState.collectAsState()

  Scaffold(
      modifier = Modifier.fillMaxSize().testTag(C.Tag.email_verification_screen_container),
  ) { padding ->
    Column(
        modifier = Modifier.fillMaxSize().imePadding().padding(padding).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
      Text(
          text = stringResource(R.string.email_verification_title),
          style = MaterialTheme.typography.headlineSmall,
      )
      Spacer(Modifier.height(16.dp))
      Text(
          text = stringResource(R.string.email_verification_message),
          style = MaterialTheme.typography.bodyLarge,
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
      )
      if (uiState.verificationEmailSent) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.email_verification_sent))
      }
      uiState.errorMsg?.let { error ->
        Spacer(Modifier.height(8.dp))
        Text(
            text =
                error.formatArg?.let { stringResource(error.messageRes, it) }
                    ?: stringResource(error.messageRes),
            color = MaterialTheme.colorScheme.error,
        )
      }
      Spacer(Modifier.height(24.dp))
      Button(
          onClick = { authViewModel.resendEmailVerification() },
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.email_verification_resend),
          enabled = !uiState.isLoading,
      ) {
        Text(stringResource(R.string.email_verification_resend))
      }
      Spacer(Modifier.height(8.dp))
      Button(
          onClick = { authViewModel.checkEmailVerification(onVerified) },
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.email_verification_check),
          enabled = !uiState.isLoading,
      ) {
        if (uiState.isLoading) {
          CircularProgressIndicator()
        } else {
          Text(stringResource(R.string.email_verification_check))
        }
      }
      TextButton(onClick = onBack) { Text(stringResource(R.string.email_verification_back)) }
    }
  }
}
