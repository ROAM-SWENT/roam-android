/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.settings

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
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
import androidx.core.os.LocaleListCompat
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
import com.github.roamswent.roam.ui.theme.isDark

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
  val logoutSuccessfulMessage = stringResource(R.string.settings_logout_successful)
  val errorMessage = uiState.errorMsg?.let { stringResource(it) }

  LaunchedEffect(Unit) { resolvedSettingsViewModel.loadProfile() }

  LaunchedEffect(uiState.signedOut) {
    if (uiState.signedOut) {
      onSignedOut()
      Toast.makeText(context, logoutSuccessfulMessage, Toast.LENGTH_SHORT).show()
    }
  }

  LaunchedEffect(uiState.errorMsg) {
    errorMessage?.let { message ->
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
      containerColor =
          if (MaterialTheme.isDark) MaterialTheme.colorScheme.background else ScreenBackground,
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
                name =
                    uiState.profile?.displayName.orEmpty().ifBlank {
                      stringResource(R.string.settings_profile_default_name)
                    },
                email = uiState.profile?.email.orEmpty(),
                provider = uiState.profile?.provider.orEmpty(),
            ),
        onManagePermissionsClick = onManagePermissionsClick,
        onSignOutClick = { resolvedSettingsViewModel.signOut(credentialManager) },
        themeMode = themeMode,
        onThemeModeSelected = onThemeModeSelected,
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
                  if (MaterialTheme.isDark) MaterialTheme.colorScheme.background
                  else ScreenBackground
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
      )
      PermissionsCard(
          cameraPermissionGranted = cameraPermissionGranted,
          locationPermissionGranted = locationPermissionGranted,
          notificationPermissionGranted = notificationPermissionGranted,
          onManagePermissionsClick = onManagePermissionsClick,
      )
      InterestsCard(
          interests = interests,
          onRemoveInterest = onRemoveInterest,
          onAddInterestClick = { showAddInterestDialog = true },
      )
      ThemeCard(
          themeMode = themeMode,
          onThemeSelected = onThemeModeSelected,
      )
      LanguageCard()
    }
  }

  if (showAddInterestDialog) {
    AlertDialog(
        onDismissRequest = { showAddInterestDialog = false },
        title = { Text(stringResource(R.string.settings_add_interest)) },
        text = {
          val availableInterests = UserInterestCategories.all.filterNot { it.id in interests }
          if (availableInterests.isEmpty()) {
            Text(stringResource(R.string.settings_all_interests_selected))
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              availableInterests.forEach { category ->
                TextButton(
                    onClick = {
                      onAddInterest(category.id)
                      showAddInterestDialog = false
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                            .testTag(C.Tag.settings_interest_option_prefix + category.id),
                ) {
                  Text(stringResource(category.labelRes), modifier = Modifier.fillMaxWidth())
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
            Text(stringResource(R.string.settings_cancel))
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
) {
  Card(
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.settings_theme_card),
      shape = RoundedCornerShape(20.dp),
      colors =
          CardDefaults.cardColors(
              containerColor =
                  if (MaterialTheme.isDark) MaterialTheme.colorScheme.surfaceVariant
                  else Color.White
          ),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
          text = stringResource(R.string.settings_theme_section_header),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
      )
      val themes =
          listOf(
              Triple(
                  ThemeMode.AUTO,
                  stringResource(R.string.settings_theme_auto),
                  C.Tag.settings_theme_auto,
              ),
              Triple(
                  ThemeMode.LIGHT,
                  stringResource(R.string.settings_theme_light),
                  C.Tag.settings_theme_light,
              ),
              Triple(
                  ThemeMode.DARK,
                  stringResource(R.string.settings_theme_dark),
                  C.Tag.settings_theme_dark,
              ),
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

private data class LanguageOption(
    val languageTag: String,
    @param:StringRes val labelRes: Int,
    val testTag: String,
)

private val languageOptions =
    listOf(
        LanguageOption(
            "en",
            R.string.settings_language_english,
            C.Tag.settings_language_option_en,
        ),
        LanguageOption(
            "fr",
            R.string.settings_language_french,
            C.Tag.settings_language_option_fr,
        ),
    )

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LanguageCard() {
  Card(
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.settings_language_card),
      shape = RoundedCornerShape(20.dp),
      colors =
          CardDefaults.cardColors(
              containerColor =
                  if (MaterialTheme.isDark) MaterialTheme.colorScheme.surfaceVariant
                  else Color.White
          ),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
          text = stringResource(R.string.settings_language_section_header),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
      )
      val currentLocales = AppCompatDelegate.getApplicationLocales()
      val currentTag = if (currentLocales.isEmpty) null else currentLocales[0]?.language
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        languageOptions.forEachIndexed { index, option ->
          SegmentedButton(
              selected = currentTag == option.languageTag,
              onClick = {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(option.languageTag)
                )
              },
              shape =
                  SegmentedButtonDefaults.itemShape(index = index, count = languageOptions.size),
              modifier = Modifier.testTag(option.testTag),
          ) {
            Text(stringResource(option.labelRes))
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
) {
  Card(
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.settings_profile_card),
      shape = RoundedCornerShape(20.dp),
      colors =
          CardDefaults.cardColors(
              containerColor =
                  if (MaterialTheme.isDark) MaterialTheme.colorScheme.surfaceVariant
                  else Color.White
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
            color =
                if (MaterialTheme.isDark) MaterialTheme.colorScheme.surface else ScreenBackground,
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
              color =
                  if (MaterialTheme.isDark) MaterialTheme.colorScheme.surface else ScreenBackground,
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
                      if (MaterialTheme.isDark) MaterialTheme.colorScheme.surfaceVariant
                      else Color(0xFFFFF1F0),
                  contentColor =
                      if (MaterialTheme.isDark) MaterialTheme.colorScheme.error
                      else Color(0xFFDB4038),
              ),
      ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.settings_sign_out))
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
) {
  Card(
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.settings_permissions_card),
      shape = RoundedCornerShape(20.dp),
      colors =
          CardDefaults.cardColors(
              containerColor =
                  if (MaterialTheme.isDark) MaterialTheme.colorScheme.surfaceVariant
                  else Color.White
          ),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
          text = stringResource(R.string.settings_permissions_section_header),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
      )
      PermissionStatus(
          name = stringResource(R.string.settings_notification_permission),
          isGranted = notificationPermissionGranted,
          testTag = C.Tag.settings_notification_permission,
      )
      PermissionStatus(
          stringResource(R.string.settings_camera_permission),
          cameraPermissionGranted,
          C.Tag.settings_camera_permission_tag,
      )
      PermissionStatus(
          stringResource(R.string.settings_location_permission),
          locationPermissionGranted,
          C.Tag.settings_location_permission_tag,
      )
      OutlinedButton(
          onClick = onManagePermissionsClick,
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.settings_manage_permissions_button),
      ) {
        Text(text = stringResource(R.string.settings_manage_permissions))
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
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.settings_interests_card),
      shape = RoundedCornerShape(20.dp),
      colors =
          CardDefaults.cardColors(
              containerColor =
                  if (MaterialTheme.isDark) MaterialTheme.colorScheme.surfaceVariant
                  else Color.White
          ),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
          text = stringResource(R.string.settings_interests_title),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
      )
      Text(
          text = stringResource(R.string.settings_interests_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (interests.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_interests_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onAddInterestClick,
            modifier = Modifier.testTag(C.Tag.settings_add_interest_button),
            shape = RoundedCornerShape(50),
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.size(4.dp))
          Text(stringResource(R.string.settings_add_interest))
        }
      } else {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          interests.forEach { interest ->
            InterestChip(
                interest = interest,
                onRemove = { onRemoveInterest(interest) },
            )
          }
          OutlinedButton(
              onClick = onAddInterestClick,
              modifier = Modifier.testTag(C.Tag.settings_add_interest_button),
              shape = RoundedCornerShape(50),
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(4.dp))
            Text(stringResource(R.string.settings_add_interest))
          }
        }
      }
    }
  }
}

@Composable
private fun InterestChip(interest: String, onRemove: () -> Unit) {
  val category = UserInterestCategories.all.firstOrNull { it.id == interest }
  val label = category?.let { stringResource(it.labelRes) } ?: interest
  Surface(
      shape = RoundedCornerShape(50),
      color = if (MaterialTheme.isDark) MaterialTheme.colorScheme.surface else ScreenBackground,
  ) {
    Row(
        modifier = Modifier.padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
          text = label,
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
            contentDescription = stringResource(R.string.settings_remove_interest, label),
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
        text =
            if (isGranted) stringResource(R.string.settings_permission_granted)
            else stringResource(R.string.settings_permission_denied),
        style = MaterialTheme.typography.bodyLarge,
        color = if (isGranted) ScanTeal else MaterialTheme.colorScheme.error,
    )
  }
}
