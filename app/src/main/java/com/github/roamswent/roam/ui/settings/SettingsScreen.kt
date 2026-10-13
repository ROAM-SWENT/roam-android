/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.roamswent.roam.ui.theme.ScanTeal
import com.github.roamswent.roam.ui.theme.ScreenBackground

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = viewModel(),
    credentialManager: CredentialManager = CredentialManager.create(LocalContext.current),
    onSignedOut: () -> Unit = {},
    onManagePermissionsClick: () -> Unit = {},
) {

  val context = LocalContext.current
  val uiState by settingsViewModel.uiState.collectAsState()

  LaunchedEffect(uiState.signedOut) {
    if (uiState.signedOut) {
      onSignedOut()
      Toast.makeText(context, "Logout successful", Toast.LENGTH_SHORT).show()
    }
  }

  LaunchedEffect(uiState.errorMsg) {
    uiState.errorMsg?.let { message ->
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
      settingsViewModel.clearErrorMsg()
    }
  }

  Scaffold(
      modifier = modifier.fillMaxSize(),
      containerColor = ScreenBackground,
  ) { contentPadding ->
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      Text(
          text = "Settings",
          style = MaterialTheme.typography.headlineLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
      )

      Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          PermissionStatus("Camera permission", uiState.cameraPermissionGranted)
          PermissionStatus("Localisation permission", uiState.locationPermissionGranted)
          Spacer(modifier = Modifier.height(4.dp))
          OutlinedButton(
              onClick = onManagePermissionsClick,
              modifier = Modifier.fillMaxWidth(),
          ) {
            Text(text = "Manage Permissions")
          }
        }
      }

      Button(
          onClick = { settingsViewModel.signOut(credentialManager) },
          modifier = Modifier.fillMaxWidth(),
          colors =
              ButtonDefaults.buttonColors(
                  containerColor = ScanTeal,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
      ) {
        Text(text = "Log Out")
      }
    }
  }
}

@Composable
private fun PermissionStatus(
    name: String,
    isGranted: Boolean,
) {
  Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
        text = name,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )

    Text(
        text = if (isGranted) "Granted" else "Denied",
        style = MaterialTheme.typography.bodyLarge,
        color = if (isGranted) ScanTeal else MaterialTheme.colorScheme.error,
    )
  }
}

// @Preview(showBackground = true)
// @Composable
// fun SettingsScreenPreview() {
//    SampleAppTheme {
//        SettingsScreen()
//    }
// }
