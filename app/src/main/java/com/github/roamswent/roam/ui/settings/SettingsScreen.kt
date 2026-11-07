/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.github.roamswent.roam.model.interests.SharedPreferencesUserInterestsRepository
import com.github.roamswent.roam.model.interests.UserInterestCategories
import com.github.roamswent.roam.model.theme.ThemeMode
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.permission.CameraPermissionController
import com.github.roamswent.roam.ui.permission.LocationPermissionController
import com.github.roamswent.roam.ui.permission.NotificationPermissionController
import com.github.roamswent.roam.ui.permission.rememberNotificationPermissionController
import com.github.roamswent.roam.ui.theme.ScanTeal
import com.github.roamswent.roam.ui.theme.ScreenBackground

private data class ProfileDisplayData(
    val initial: String,
    val name: String,
    val email: String,
    val provider: String,
)

@Composable
fun SettingsScreen(
    cameraPermissionController: CameraPermissionController,
    locationPermissionController: LocationPermissionController,
    notificationPermissionController: NotificationPermissionController =
        rememberNotificationPermissionController(),
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel? = null,
    credentialManager: CredentialManager = CredentialManager.create(LocalContext.current),
    onManagePermissionsClick: () -> Unit = {},
    onSignedOut: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.AUTO,
    onThemeModeSelected: (ThemeMode) -> Unit = {},
    isDarkTheme: Boolean,
) {

  val context = LocalContext.current
  val userInterestsRepository =
      remember(context.applicationContext) {
        SharedPreferencesUserInterestsRepository(context.applicationContext)
      }
  val resolvedSettingsViewModel =
      settingsViewModel
          ?: viewModel { SettingsViewModel(userInterestsRepository = userInterestsRepository) }
  val uiState by resolvedSettingsViewModel.uiState.collectAsState()
  val notificationPermissionState by notificationPermissionController.state.collectAsState()
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

  DisposableEffect(
      lifecycleOwner,
      cameraPermissionController,
      locationPermissionController,
      notificationPermissionController,
  ) {
    fun refreshPermissionStatus() {
      resolvedSettingsViewModel.setCameraPermissionGranted(
          cameraPermissionController.hasPermission()
      )
      resolvedSettingsViewModel.setLocationPermissionGranted(
          locationPermissionController.hasPermission()
      )
      notificationPermissionController.refreshPermissionState()
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

  // The screen applies its own statusBarsPadding, so this inner scaffold must
  // not add the system bar insets again on top of the route scaffold's padding.
  Scaffold(
      modifier = modifier.fillMaxSize().testTag(C.Tag.settings_screen_container),
      containerColor = if (isDarkTheme) MaterialTheme.colorScheme.background else ScreenBackground,
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
  ) { contentPadding ->
    SettingsContent(
        modifier = Modifier.padding(contentPadding),
        cameraPermissionGranted = uiState.cameraPermissionGranted,
        locationPermissionGranted = uiState.locationPermissionGranted,
        notificationPermissionGranted = notificationPermissionState.isGranted,
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
        themeMode = themeMode,
        onThemeModeSelected = onThemeModeSelected,
        isDarkTheme = isDarkTheme,
        interests = uiState.interests,
        onAddInterest = resolvedSettingsViewModel::addInterest,
        onRemoveInterest = resolvedSettingsViewModel::removeInterest,
    )
  }
}

@Composable
private fun SettingsContent(
    cameraPermissionGranted: Boolean,
    locationPermissionGranted: Boolean,
    notificationPermissionGranted: Boolean,
    profile: ProfileDisplayData,
    onManagePermissionsClick: () -> Unit,
    onSignOutClick: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    isDarkTheme: Boolean,
    interests: List<String>,
    onAddInterest: (String) -> Unit,
    onRemoveInterest: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  var showAddInterestDialog by remember { mutableStateOf(false) }

  Box(
      modifier =
          Modifier.fillMaxSize()
              .background(
                  if (isDarkTheme) MaterialTheme.colorScheme.background else ScreenBackground
              )
  ) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
          text = stringResource(R.string.settings_title),
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
      )
      ProfileCard(
          profile = profile,
          onSignOutClick = onSignOutClick,
          isDarkTheme = isDarkTheme,
      )
      PermissionsCard(
          cameraPermissionGranted = cameraPermissionGranted,
          locationPermissionGranted = locationPermissionGranted,
          notificationPermissionGranted = notificationPermissionGranted,
          onManagePermissionsClick = onManagePermissionsClick,
          isDarkTheme = isDarkTheme,
      )
      InterestsCard(
          interests = interests,
          onRemoveInterest = onRemoveInterest,
          onAddInterestClick = { showAddInterestDialog = true },
          isDarkTheme = isDarkTheme,
      )
      ThemeCard(
          themeMode = themeMode,
          onThemeSelected = onThemeModeSelected,
          isDarkTheme = isDarkTheme,
      )
    }
  }

  if (showAddInterestDialog) {
    AlertDialog(
        onDismissRequest = { showAddInterestDialog = false },
        title = { Text("Add an interest") },
        text = {
          val availableInterests = UserInterestCategories.all.filterNot { it in interests }
          if (availableInterests.isEmpty()) {
            Text("All available interests are already selected.")
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              availableInterests.forEach { interest ->
                TextButton(
                    onClick = {
                      onAddInterest(interest)
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
@OptIn(ExperimentalMaterial3Api::class)
private fun ThemeCard(
    themeMode: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    isDarkTheme: Boolean,
) {
  Card(
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.settings_theme_card),
      shape = RoundedCornerShape(20.dp),
      colors =
          CardDefaults.cardColors(
              containerColor =
                  if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant else Color.White
          ),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
          text = "Theme",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
      )
      val themes =
          listOf(
              Triple(ThemeMode.AUTO, "Auto", C.Tag.settings_theme_auto),
              Triple(ThemeMode.LIGHT, "Light", C.Tag.settings_theme_light),
              Triple(ThemeMode.DARK, "Dark", C.Tag.settings_theme_dark),
          )
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        themes.forEachIndexed { index, (mode, label, testTag) ->
          SegmentedButton(
              selected = themeMode == mode,
              onClick = { onThemeSelected(mode) },
              shape = SegmentedButtonDefaults.itemShape(index = index, count = themes.size),
              modifier = Modifier.testTag(testTag),
          ) {
            Text(label)
          }
        }
      }
    }
  }
}

@Composable
private fun ProfileCard(
    profile: ProfileDisplayData,
    onSignOutClick: () -> Unit,
    isDarkTheme: Boolean,
) {
  Card(
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.settings_profile_card),
      shape = RoundedCornerShape(20.dp),
      colors =
          CardDefaults.cardColors(
              containerColor =
                  if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant else Color.White
          ),
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
            color = if (isDarkTheme) MaterialTheme.colorScheme.surface else ScreenBackground,
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
              color = if (isDarkTheme) MaterialTheme.colorScheme.surface else ScreenBackground,
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
                  containerColor =
                      if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant
                      else Color(0xFFFFF1F0),
                  contentColor =
                      if (isDarkTheme) MaterialTheme.colorScheme.error else Color(0xFFDB4038),
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
    notificationPermissionGranted: Boolean,
    onManagePermissionsClick: () -> Unit,
    isDarkTheme: Boolean,
) {
  Card(
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.settings_permissions_card),
      shape = RoundedCornerShape(20.dp),
      colors =
          CardDefaults.cardColors(
              containerColor =
                  if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant else Color.White
          ),
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
      PermissionStatus(
          name = stringResource(R.string.settings_notification_permission),
          isGranted = notificationPermissionGranted,
          testTag = C.Tag.settings_notification_permission,
      )
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
    isDarkTheme: Boolean,
) {
  Card(
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.settings_interests_card),
      shape = RoundedCornerShape(20.dp),
      colors =
          CardDefaults.cardColors(
              containerColor =
                  if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant else Color.White
          ),
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
        if (interests.isEmpty()) {
          Text(
              text = "No interests selected yet.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          interests.forEach { interest ->
            InterestChip(
                interest = interest,
                onRemove = { onRemoveInterest(interest) },
                isDarkTheme = isDarkTheme,
            )
          }
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
private fun InterestChip(interest: String, onRemove: () -> Unit, isDarkTheme: Boolean) {
  Surface(
      shape = RoundedCornerShape(50),
      color = if (isDarkTheme) MaterialTheme.colorScheme.surface else ScreenBackground,
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
    testTag: String = name,
) {
  Column(
      modifier = Modifier.testTag(testTag),
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
