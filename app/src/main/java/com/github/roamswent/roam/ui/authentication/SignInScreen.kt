// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.OnBrand
import com.github.roamswent.roam.ui.theme.ScanTeal
import com.github.roamswent.roam.ui.theme.ScreenBackground
import com.github.roamswent.roam.ui.theme.isDark

@Composable
fun SignInScreen(
    authViewModel: SignInViewModel = viewModel(),
    credentialManager: CredentialManager = CredentialManager.create(LocalContext.current),
    onSignedIn: () -> Unit = {},
    onSignInClickOverride: ((() -> Unit) -> Unit)? = null,
) {

  val context = LocalContext.current
  val uiState by authViewModel.uiState.collectAsState()
  val signInSuccessMessage = stringResource(R.string.sign_in_success)
  val errorMessage =
      uiState.errorMsg?.let { error ->
        error.formatArg?.let { stringResource(error.messageRes, it) }
            ?: stringResource(error.messageRes)
      }
  var isRestoredUser by remember { mutableStateOf(uiState.user != null) }
  var isRestoringInitialSession by remember { mutableStateOf(uiState.isRestoringSession) }

  // Show error message if login fails
  LaunchedEffect(uiState.errorMsg) {
    errorMessage?.let {
      Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
      authViewModel.clearErrorMsg()
    }
  }

  // Navigate screen on successful login
  LaunchedEffect(uiState.user, uiState.isRestoringSession) {
    if (uiState.isRestoringSession) return@LaunchedEffect

    uiState.user?.let {
      if (!isRestoringInitialSession && !isRestoredUser) {
        Toast.makeText(context, signInSuccessMessage, Toast.LENGTH_SHORT).show()
      }
      isRestoredUser = true
      onSignedIn()
    } ?: run { isRestoredUser = false }
    isRestoringInitialSession = false
  }

  if (uiState.isRestoringSession || uiState.user != null) {
    Box(
        modifier = Modifier.fillMaxSize().testTag(C.Tag.sign_in_screen_container),
        contentAlignment = Alignment.Center,
    ) {
      CircularProgressIndicator(color = ScanTeal)
    }
    return
  }

  Scaffold(
      modifier = Modifier.fillMaxSize().testTag(C.Tag.sign_in_screen_container),
      containerColor =
          if (MaterialTheme.isDark) MaterialTheme.colorScheme.background else ScreenBackground,
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
              modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).height(IntrinsicSize.Min),
              contentAlignment = Alignment.Center,
          ) {
            if (uiState.isLoading) {
              CircularProgressIndicator(color = ScanTeal)
            } else {
              GoogleSignInButton(
                  onSignInClick =
                      onSignInClickOverride?.let { signInOverride ->
                        { signInOverride(onSignedIn) }
                      } ?: { authViewModel.signIn(context, credentialManager) }
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
                colorFilter =
                    if (MaterialTheme.isDark) ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                    else null,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.sign_in_title),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.displaySmall,
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
      modifier = Modifier.fillMaxSize().testTag(C.Tag.sign_in_google_button),
      shape = RoundedCornerShape(20.dp),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = ScanTeal,
              contentColor = OnBrand,
          ),
  ) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
          text = stringResource(R.string.sign_in_google_button),
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.SemiBold,
      )
    }
  }
}
