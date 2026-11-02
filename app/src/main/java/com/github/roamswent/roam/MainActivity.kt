/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 * Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
 */

package com.github.roamswent.roam

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.roamswent.roam.model.guide.FirestoreSavedGuideRepository
import com.github.roamswent.roam.model.guide.SavedGuideRepository
import com.github.roamswent.roam.model.permission.LocationPermissionDecision
import com.github.roamswent.roam.model.permission.SharedPreferencesLocationPermissionDecisionRepository
import com.github.roamswent.roam.repository.CaptureSender
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.authentication.SignInScreen
import com.github.roamswent.roam.ui.authentication.SignInViewModel
import com.github.roamswent.roam.ui.collection.CollectionRoute
import com.github.roamswent.roam.ui.collection.GuideDetailRoute
import com.github.roamswent.roam.ui.home.HomeScreen
import com.github.roamswent.roam.ui.home.HomeViewModel
import com.github.roamswent.roam.ui.navigation.Routes
import com.github.roamswent.roam.ui.permission.CameraPermissionDialog
import com.github.roamswent.roam.ui.permission.CameraPermissionState
import com.github.roamswent.roam.ui.permission.rememberCameraAvailability
import com.github.roamswent.roam.ui.permission.rememberCameraPermissionController
import com.github.roamswent.roam.ui.permission.rememberLocationPermissionController
import com.github.roamswent.roam.ui.scan.ScanReviewRoute
import com.github.roamswent.roam.ui.scan.ScanRoute
import com.github.roamswent.roam.ui.settings.SettingsScreen
import com.github.roamswent.roam.ui.settings.SettingsViewModel
import com.github.roamswent.roam.ui.theme.SampleAppTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      SampleAppTheme {
        val authViewModel: SignInViewModel = viewModel()
        RoamApp(authViewModel)
      }
    }
  }
}

@Composable
internal fun RoamApp(
    authViewModel: SignInViewModel,
    navController: NavHostController = rememberNavController(),
    captureSenderOverride: CaptureSender? = null,
    onSignInClickOverride: (() -> Unit)? = null,
    settingsViewModel: SettingsViewModel? = null,
    savedGuideRepositoryOverride: SavedGuideRepository? = null,
) {
  val context = LocalContext.current
  val locationPermissionDecisionRepository =
      remember(context.applicationContext) {
        SharedPreferencesLocationPermissionDecisionRepository(context.applicationContext)
      }
  val locationPermissionController =
      rememberLocationPermissionController(locationPermissionDecisionRepository) {
        navController.navigate(Routes.Home) { popUpTo(Routes.SignIn) { inclusive = true } }
      }
  val cameraAvailable = rememberCameraAvailability()
  var showPermissionDialog by remember { mutableStateOf(false) }
  val permissionController = rememberCameraPermissionController { granted ->
    if (granted) {
      navController.navigate(Routes.Scan)
    } else {
      showPermissionDialog = true
    }
  }
  val permissionState by permissionController.state.collectAsState()
  // Lazy, so Firebase is only touched once a saved-guide screen is opened.
  val savedGuideRepository =
      remember(savedGuideRepositoryOverride) {
        lazy { savedGuideRepositoryOverride ?: FirestoreSavedGuideRepository() }
      }
  Surface(
      modifier = Modifier.fillMaxSize().semantics { testTag = C.Tag.main_screen_container },
      color = MaterialTheme.colorScheme.background,
  ) {
    NavHost(navController = navController, startDestination = Routes.SignIn) {
      composable(Routes.SignIn) {
        val onSignedIn = {
          if (
              locationPermissionController.state.value.decision ==
                  LocationPermissionDecision.NOT_ASKED
          ) {
            locationPermissionController.requestPermission()
          } else {
            navController.navigate(Routes.Home) { popUpTo(Routes.SignIn) { inclusive = true } }
          }
        }
        SignInScreen(
            authViewModel = authViewModel,
            onSignedIn = onSignedIn,
            onSignInClickOverride =
                onSignInClickOverride?.let { signIn ->
                  {
                    signIn()
                    onSignedIn()
                  }
                },
        )
      }
      composable(Routes.Home) {
        val homeViewModel: HomeViewModel = viewModel()
        val userName by homeViewModel.userName.collectAsState()
        HomeScreen(
            onScanClicked = { if (cameraAvailable) permissionController.requestPermission() },
            cameraAvailable = cameraAvailable,
            userName = userName,
            onProfileClicked = { navController.navigate(Routes.Settings) },
        )
      }
      composable(Routes.Scan) { ScanRoute(navController) }
      composable("${Routes.ScanReview}?uri={uri}") { backStackEntry ->
        val uri = backStackEntry.arguments?.getString("uri")?.let(Uri::parse)
        if (uri != null) {
          ScanReviewRoute(navController, uri, captureSenderOverride)
        }
      }
      composable(Routes.Settings) {
        SettingsScreen(
            settingsViewModel = settingsViewModel,
            cameraPermissionController = permissionController,
            locationPermissionController = locationPermissionController,
            onManagePermissionsClick = { permissionController.openAppSettings() },
            onHomeClick = { navController.popBackStack(Routes.Home, inclusive = false) },
            onSignedOut = {
              authViewModel.clearSignedInUser()
              navController.navigate(Routes.SignIn) {
                popUpTo(navController.graph.id) { inclusive = true }
              }
            },
        )
      }
      composable(Routes.Collection) {
        CollectionRoute(
            repository = savedGuideRepository.value,
            onGuideClick = { guideId -> navController.navigate(Routes.guideDetail(guideId)) },
        )
      }
      composable(Routes.GuideDetail) { backStackEntry ->
        val guideId = backStackEntry.arguments?.getString(Routes.GuideIdArg)
        if (guideId != null) {
          GuideDetailRoute(
              guideId = guideId,
              repository = savedGuideRepository.value,
              onBackClick = { navController.popBackStack() },
          )
        }
      }
    }
    if (showPermissionDialog && permissionState == CameraPermissionState.Denied) {
      CameraPermissionDialog(
          onOpenSettings = {
            showPermissionDialog = false
            permissionController.openAppSettings()
          },
          onDismiss = { showPermissionDialog = false },
      )
    }
  }
}
