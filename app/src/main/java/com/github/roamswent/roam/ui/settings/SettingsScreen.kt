/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.roamswent.roam.ui.permission.CameraPermissionController
import com.github.roamswent.roam.ui.permission.LocationPermissionController
import com.github.roamswent.roam.ui.theme.ScanTeal
import com.github.roamswent.roam.ui.theme.ScreenBackground

private data class ProfileDisplayData(
    val initial: String,
    val name: String,
    val email: String,
    val provider: String,
)

private val sampleProfile =
    ProfileDisplayData(
        initial = "V",
        name = "Victor",
        email = "victor@epfl.ch",
        provider = "Google",
    )

@Composable
fun SettingsScreen(
    cameraPermissionController: CameraPermissionController,
    locationPermissionController: LocationPermissionController,
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = viewModel(),
    credentialManager: CredentialManager = CredentialManager.create(LocalContext.current),
    onManagePermissionsClick: () -> Unit = {},
    onSignedOut: () -> Unit = {},
) {

  val context = LocalContext.current
  val uiState by settingsViewModel.uiState.collectAsState()
  val lifecycleOwner = LocalLifecycleOwner.current

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

  DisposableEffect(lifecycleOwner, cameraPermissionController, locationPermissionController) {
    fun refreshPermissionStatus() {
      settingsViewModel.setCameraPermissionGranted(cameraPermissionController.hasPermission())
      settingsViewModel.setLocationPermissionGranted(locationPermissionController.hasPermission())
    }

    refreshPermissionStatus()
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        refreshPermissionStatus()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  Scaffold(
      modifier = modifier.fillMaxSize(),
      containerColor = ScreenBackground,
  ) { contentPadding ->
    SettingsContent(
        modifier = Modifier.padding(contentPadding),
        cameraPermissionGranted = uiState.cameraPermissionGranted,
        locationPermissionGranted = uiState.locationPermissionGranted,
        onManagePermissionsClick = onManagePermissionsClick,
        onSignOutClick = { settingsViewModel.signOut(credentialManager) },
    )
  }
}

@Composable
private fun SettingsContent(
    cameraPermissionGranted: Boolean,
    locationPermissionGranted: Boolean,
    onManagePermissionsClick: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    ProfileCard(
        profile = sampleProfile,
        onSignOutClick = onSignOutClick,
    )
    PermissionsCard(
        cameraPermissionGranted = cameraPermissionGranted,
        locationPermissionGranted = locationPermissionGranted,
        onManagePermissionsClick = onManagePermissionsClick,
    )
  }
}

@Composable
private fun ProfileCard(
    profile: ProfileDisplayData,
    onSignOutClick: () -> Unit,
) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = ScreenBackground,
        ) {
          androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
            Text(
                text = profile.initial,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ScanTeal,
            )
          }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
              text = profile.name,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
          )
          Text(
              text = profile.email,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Surface(
              shape = RoundedCornerShape(50),
              color = ScreenBackground,
          ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold)
              Text(
                  text = profile.provider,
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }
      Button(
          onClick = onSignOutClick,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors =
              ButtonDefaults.buttonColors(
                  containerColor = Color(0xFFFFF1F0),
                  contentColor = Color(0xFFDB4038),
              ),
      ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text("Sign Out")
      }
    }
  }
}

@Composable
private fun PermissionsCard(
    cameraPermissionGranted: Boolean,
    locationPermissionGranted: Boolean,
    onManagePermissionsClick: () -> Unit,
) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
          text = "App Permissions",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
      )
      PermissionStatus("Camera permission", cameraPermissionGranted)
      PermissionStatus("Location permission", locationPermissionGranted)
      OutlinedButton(
          onClick = onManagePermissionsClick,
          modifier = Modifier.fillMaxWidth(),
      ) {
        Text(text = "Manage Permissions")
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
      modifier = Modifier.testTag(name),
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

@Preview(showBackground = true)
@Composable
private fun SettingsContentPreview() {
  MaterialTheme {
    SettingsContent(
        cameraPermissionGranted = true,
        locationPermissionGranted = false,
        onManagePermissionsClick = {},
        onSignOutClick = {},
        modifier = Modifier.background(ScreenBackground),
    )
  }
}
