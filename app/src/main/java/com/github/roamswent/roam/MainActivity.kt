/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.github.roamswent.roam.model.permission.LocationPermissionDecision
import com.github.roamswent.roam.model.permission.SharedPreferencesLocationPermissionDecisionRepository
import com.github.roamswent.roam.repository.CaptureSender
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.authentication.SignInScreen
import com.github.roamswent.roam.ui.authentication.SignInViewModel
import com.github.roamswent.roam.ui.components.bottomdock.AppBottomDock
import com.github.roamswent.roam.ui.components.bottomdock.DockDestination
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
import com.github.roamswent.roam.ui.theme.ScreenBackground

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
  val currentBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination =
      when (currentBackStackEntry?.destination?.route) {
        Routes.Settings -> DockDestination.SETTINGS
        else -> DockDestination.HOME
      }
  val onScanClick = { if (cameraAvailable) permissionController.requestPermission() }
  val onDockDestinationSelected = { destination: DockDestination ->
    if (destination.route != currentBackStackEntry?.destination?.route) {
      // Home is the navigation root: pop everything above it so system back
      // from Settings always returns to Home (and back on Home exits the app).
      navController.navigate(destination.route) {
        popUpTo(Routes.Home) {
          inclusive = false
          saveState = true
        }
        launchSingleTop = true
        restoreState = true
      }
    }
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
        // HomeScreen and the dock apply their own status/nav bar insets, so the
        // scaffold only contributes the ScreenBackground behind the status bar.
        Scaffold(
            containerColor = ScreenBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { innerPadding ->
          HomeScreen(
              modifier = Modifier.padding(innerPadding).background(ScreenBackground),
              onScanClicked = onScanClick,
              cameraAvailable = cameraAvailable,
              userName = userName,
              onProfileClicked = { navController.navigate(Routes.Settings) },
          )
        }
      }
      composable(Routes.Scan) { ScanRoute(navController) }
      composable("${Routes.ScanReview}?uri={uri}") { backStackEntry ->
        val uri = backStackEntry.arguments?.getString("uri")?.let(Uri::parse)
        if (uri != null) {
          ScanReviewRoute(navController, uri, captureSenderOverride)
        }
      }
      composable(Routes.Settings) {
        // Same as Home: the screens apply their own status bar inset, so the top
        // inset stays 0 and containerColor covers the status bar strip. With a
        // bottomBar present, Scaffold reports the full dock height (116.dp) as
        // innerPadding.bottom, but only the 80.dp bar is opaque: pad the content
        // by 96.dp (bar + 16.dp) so it scrolls underneath the transparent bump.
        Scaffold(
            containerColor = ScreenBackground,
            contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 96.dp),
            bottomBar = {
              AppBottomDock(
                  selected = currentDestination,
                  onDestinationSelected = onDockDestinationSelected,
                  onScanClick = onScanClick,
                  cameraAvailable = cameraAvailable,
              )
            },
        ) { innerPadding ->
          SettingsScreen(
              modifier =
                  Modifier.padding(
                      top = innerPadding.calculateTopPadding(),
                      bottom = 96.dp,
                  ),
              settingsViewModel = settingsViewModel,
              cameraPermissionController = permissionController,
              locationPermissionController = locationPermissionController,
              onManagePermissionsClick = { permissionController.openAppSettings() },
              onSignedOut = {
                authViewModel.clearSignedInUser()
                navController.navigate(Routes.SignIn) {
                  popUpTo(navController.graph.id) { inclusive = true }
                }
              },
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
