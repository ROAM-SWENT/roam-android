/*
 * This code was adapted from the bootcamp solution provided for SWENT.
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.authentication

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.roamswent.roam.R
import com.github.roamswent.roam.ui.theme.ScanTeal
import com.github.roamswent.roam.ui.theme.ScreenBackground

@Composable
fun SignInScreen(
    authViewModel: SignInViewModel = viewModel(),
    credentialManager: CredentialManager = CredentialManager.create(LocalContext.current),
    onSignedIn: () -> Unit = {},
) {

  val context = LocalContext.current
  val uiState by authViewModel.uiState.collectAsState()
  val signInSuccessMessage = stringResource(R.string.sign_in_success)

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
      Toast.makeText(context, signInSuccessMessage, Toast.LENGTH_SHORT).show()
      onSignedIn()
    }
  }

  Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = ScreenBackground,
      bottomBar = {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(
              modifier = Modifier.fillMaxWidth().height(60.dp),
              contentAlignment = Alignment.Center,
          ) {
            if (uiState.isLoading) {
              CircularProgressIndicator(color = ScanTeal)
            } else {
              GoogleSignInButton(
                  onSignInClick = { authViewModel.signIn(context, credentialManager) }
              )
            }
          }
          Spacer(modifier = Modifier.height(16.dp))
          Text(
              text = stringResource(R.string.sign_in_terms),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.bodySmall,
              textAlign = TextAlign.Center,
          )
        }
      },
      content = { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
          Column(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Image(
                painter = painterResource(id = R.drawable.roam_logo),
                contentDescription = stringResource(R.string.sign_in_logo_description),
                modifier = Modifier.size(180.dp),
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.sign_in_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.sign_in_tagline),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
          }
        }
      },
  )
}

@Composable
fun GoogleSignInButton(onSignInClick: () -> Unit) {
  Button(
      onClick = onSignInClick,
      modifier = Modifier.fillMaxSize(),
      shape = RoundedCornerShape(20.dp),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = ScanTeal,
              contentColor = Color.White,
          ),
  ) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
          text = stringResource(R.string.sign_in_google_button),
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
      )
    }
  }
}
