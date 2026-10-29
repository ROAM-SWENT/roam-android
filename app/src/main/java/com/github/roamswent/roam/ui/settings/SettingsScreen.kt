/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C
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

private val sampleInterests =
    listOf(
        "Architecture",
        "Ancient history",
        "Unusual anecdotes",
        "Art & Sculpture",
        "Local legends",
    )

private val additionalSampleInterests =
    listOf(
        "Museums",
        "Nature",
        "Food & drink",
        "Local culture",
    )

@Composable
fun SettingsScreen(
    cameraPermissionController: CameraPermissionController,
    locationPermissionController: LocationPermissionController,
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel? = null,
    credentialManager: CredentialManager = CredentialManager.create(LocalContext.current),
    onManagePermissionsClick: () -> Unit = {},
    onSignedOut: () -> Unit = {},
    onHomeClick: () -> Unit = {},
) {

  val resolvedSettingsViewModel = settingsViewModel ?: viewModel<SettingsViewModel>()
  val context = LocalContext.current
  val uiState by resolvedSettingsViewModel.uiState.collectAsState()
  val lifecycleOwner = LocalLifecycleOwner.current

  LaunchedEffect(Unit) { resolvedSettingsViewModel.loadProfile() }

  LaunchedEffect(uiState.signedOut) {
    if (uiState.signedOut) {
      onSignedOut()
      Toast.makeText(context, "Logout successful", Toast.LENGTH_SHORT).show()
    }
  }

  LaunchedEffect(uiState.errorMsg) {
    uiState.errorMsg?.let { message ->
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
      resolvedSettingsViewModel.clearErrorMsg()
    }
  }

  DisposableEffect(lifecycleOwner, cameraPermissionController, locationPermissionController) {
    fun refreshPermissionStatus() {
      resolvedSettingsViewModel.setCameraPermissionGranted(
          cameraPermissionController.hasPermission()
      )
      resolvedSettingsViewModel.setLocationPermissionGranted(
          locationPermissionController.hasPermission()
      )
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
        profile =
            ProfileDisplayData(
                initial =
                    uiState.profile?.displayName?.ifBlank { "?" }?.first()?.uppercase() ?: "?",
                name = uiState.profile?.displayName.orEmpty().ifBlank { "Traveler" },
                email = uiState.profile?.email.orEmpty(),
                provider = uiState.profile?.provider.orEmpty(),
            ),
        onManagePermissionsClick = onManagePermissionsClick,
        onSignOutClick = { resolvedSettingsViewModel.signOut(credentialManager) },
        onHomeClick = onHomeClick,
    )
  }
}

@Composable
private fun SettingsContent(
    cameraPermissionGranted: Boolean,
    locationPermissionGranted: Boolean,
    profile: ProfileDisplayData,
    onManagePermissionsClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val interests = remember { mutableStateListOf<String>().apply { addAll(sampleInterests) } }
  var showAddInterestDialog by remember { mutableStateOf(false) }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 72.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      ProfileCard(
          profile = profile,
          onSignOutClick = onSignOutClick,
      )
      PermissionsCard(
          cameraPermissionGranted = cameraPermissionGranted,
          locationPermissionGranted = locationPermissionGranted,
          onManagePermissionsClick = onManagePermissionsClick,
      )
      InterestsCard(
          interests = interests,
          onRemoveInterest = { interests.remove(it) },
          onAddInterestClick = { showAddInterestDialog = true },
      )
    }

    IconButton(
        onClick = onHomeClick,
        modifier =
            Modifier.align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
                .size(44.dp)
                .testTag(C.Tag.settings_home_button),
        colors =
            IconButtonDefaults.iconButtonColors(
                containerColor = Color.Gray.copy(alpha = 0.6f),
                contentColor = Color.White,
            ),
    ) {
      Icon(
          imageVector = Icons.Filled.Home,
          contentDescription = stringResource(R.string.settings_home_description),
      )
    }
  }

  if (showAddInterestDialog) {
    AlertDialog(
        onDismissRequest = { showAddInterestDialog = false },
        title = { Text("Add an interest") },
        text = {
          val availableInterests =
              (sampleInterests + additionalSampleInterests).filterNot { it in interests }
          if (availableInterests.isEmpty()) {
            Text("You have added all the sample interests.")
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              availableInterests.forEach { interest ->
                TextButton(
                    onClick = {
                      interests.add(interest)
                      showAddInterestDialog = false
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                            .testTag(C.Tag.settings_interest_option_prefix + interest),
                ) {
                  Text(interest, modifier = Modifier.fillMaxWidth())
                }
              }
            }
          }
        },
        confirmButton = {
          TextButton(
              onClick = { showAddInterestDialog = false },
              modifier = Modifier.testTag(C.Tag.settings_add_interest_cancel_button),
          ) {
            Text("Cancel")
          }
        },
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
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.settings_sign_out_button),
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
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.settings_manage_permissions_button),
      ) {
        Text(text = "Manage Permissions")
      }
    }
  }
}

@Composable
private fun InterestsCard(
    interests: List<String>,
    onRemoveInterest: (String) -> Unit,
    onAddInterestClick: () -> Unit,
) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
          text = "My Interests (AI Guide)",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
      )
      Text(
          text = "Used by AI to personalize your tourist guides.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        interests.forEach { interest ->
          InterestChip(interest = interest, onRemove = { onRemoveInterest(interest) })
        }
        OutlinedButton(
            onClick = onAddInterestClick,
            modifier = Modifier.testTag(C.Tag.settings_add_interest_button),
            shape = RoundedCornerShape(50),
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.size(4.dp))
          Text("Add an interest")
        }
      }
    }
  }
}

@Composable
private fun InterestChip(interest: String, onRemove: () -> Unit) {
  Surface(
      shape = RoundedCornerShape(50),
      color = ScreenBackground,
  ) {
    Row(
        modifier = Modifier.padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
          text = interest,
          modifier = Modifier.padding(vertical = 10.dp),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurface,
      )
      IconButton(
          onClick = onRemove,
          modifier = Modifier.size(32.dp).testTag(C.Tag.settings_interest_remove_prefix + interest),
      ) {
        Icon(
            Icons.Default.Close,
            contentDescription = "Remove $interest",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
