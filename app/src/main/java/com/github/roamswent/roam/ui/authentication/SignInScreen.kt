/*
 * This code was adapted from the bootcamp solution provided for SWENT.
 */

package com.github.roamswent.roam.ui.authentication

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SignInScreen(
    authViewModel: SignInViewModel = viewModel(),
    credentialManager: CredentialManager = CredentialManager.create(LocalContext.current),
    onSignedIn: () -> Unit = {},
) {

  val context = LocalContext.current
  val uiState by authViewModel.uiState.collectAsState()

  // Show error message if login fails
  LaunchedEffect(uiState.errorMsg) {
    uiState.errorMsg?.let {
      Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
      authViewModel.clearErrorMsg()
    }
  }

  // Navigate screen on successful login
  LaunchedEffect(uiState.user) {
    uiState.user?.let {
      Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
      onSignedIn()
    }
  }

  // The main container for the screen
  Scaffold(
      modifier = Modifier.fillMaxSize(),
      content = { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
          // Authenticate With Google Button
          if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier)
          } else {
            GoogleSignInButton(onSignInClick = { authViewModel.signIn(context, credentialManager) })
          }
        }
      },
  )
}

@Composable
fun GoogleSignInButton(onSignInClick: () -> Unit) {
  Button(
      onClick = onSignInClick,
      modifier = Modifier,
  ) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
          text = "Sign in with Google",
      )
    }
  }
}
