/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 * Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
 * Co-authored-by: Mistral Vibe <vibe@mistral.ai>
 */

package com.github.roamswent.roam

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.github.roamswent.roam.model.guide.FirestoreSavedGuideRepository
import com.github.roamswent.roam.model.guide.SavedGuideRepository
import com.github.roamswent.roam.model.location.AndroidLocationProvider
import com.github.roamswent.roam.model.permission.LocationPermissionDecision
import com.github.roamswent.roam.model.permission.NotificationPermissionDecision
import com.github.roamswent.roam.model.permission.SharedPreferencesLocationPermissionDecisionRepository
import com.github.roamswent.roam.model.permission.SharedPreferencesNotificationPermissionDecisionRepository
import com.github.roamswent.roam.model.profile.FirestorePublicProfileRepository
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import com.github.roamswent.roam.model.theme.DataStoreThemePreferencesRepository
import com.github.roamswent.roam.model.theme.ThemeMode
import com.github.roamswent.roam.repository.CaptureSender
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.authentication.SignInScreen
import com.github.roamswent.roam.ui.authentication.SignInViewModel
import com.github.roamswent.roam.ui.collection.CollectionRoute
import com.github.roamswent.roam.ui.components.bottomdock.AppBottomDock
import com.github.roamswent.roam.ui.components.bottomdock.DockDestination
import com.github.roamswent.roam.ui.components.bottomdock.dockContentClearance
import com.github.roamswent.roam.ui.explore.ExploreScreen
import com.github.roamswent.roam.ui.explore.ExploreViewModel
import com.github.roamswent.roam.ui.friends.FriendsRoute
import com.github.roamswent.roam.ui.home.HomeScreen
import com.github.roamswent.roam.ui.home.HomeViewModel
import com.github.roamswent.roam.ui.home.rememberInternetConnectivity
import com.github.roamswent.roam.ui.navigation.OfflineAction
import com.github.roamswent.roam.ui.navigation.Routes
import com.github.roamswent.roam.ui.navigation.rememberOfflineActionGate
import com.github.roamswent.roam.ui.permission.CameraPermissionDialog
import com.github.roamswent.roam.ui.permission.CameraPermissionState
import com.github.roamswent.roam.ui.permission.rememberCameraAvailability
import com.github.roamswent.roam.ui.permission.rememberCameraPermissionController
import com.github.roamswent.roam.ui.permission.rememberLocationPermissionController
import com.github.roamswent.roam.ui.permission.rememberNotificationPermissionController
import com.github.roamswent.roam.ui.profile.ProfileRegistrationStatus
import com.github.roamswent.roam.ui.profile.RegisterProfileScreen
import com.github.roamswent.roam.ui.profile.RegisterProfileViewModel
import com.github.roamswent.roam.ui.scan.ScanReviewRoute
import com.github.roamswent.roam.ui.scan.ScanRoute
import com.github.roamswent.roam.ui.settings.SettingsScreen
import com.github.roamswent.roam.ui.settings.SettingsViewModel
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import com.github.roamswent.roam.ui.theme.ScreenBackground
import com.github.roamswent.roam.ui.theme.ThemeViewModel
import com.github.roamswent.roam.ui.theme.ThemeViewModelFactory
import com.github.roamswent.roam.ui.theme.isDark

/**
 * Clears tab and settings back stacks saved during navigation.
 *
 * Keep state restoration available while a user is signed in, but prevent those stacks from being
 * restored after another user signs in.
 */
private fun NavHostController.clearSavedUserNavigation() {
  listOf(Routes.Settings, Routes.Friends, Routes.Collection, Routes.Explore).forEach {
    clearBackStack(it)
  }
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      val themeRepository = remember { DataStoreThemePreferencesRepository(applicationContext) }
      val themeViewModel: ThemeViewModel =
          viewModel(factory = remember(themeRepository) { ThemeViewModelFactory(themeRepository) })
      val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()

      if (themeMode != null) {
        val selectedTheme = requireNotNull(themeMode)
        val darkTheme =
            when (selectedTheme) {
              ThemeMode.AUTO -> isSystemInDarkTheme()
              ThemeMode.LIGHT -> false
              ThemeMode.DARK -> true
            }
        SampleAppTheme(darkTheme = darkTheme) {
          val authViewModel: SignInViewModel = viewModel()
          RoamApp(
              authViewModel = authViewModel,
              themeMode = selectedTheme,
              onThemeModeSelected = themeViewModel::setThemeMode,
          )
        }
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
    themeMode: ThemeMode = ThemeMode.AUTO,
    onThemeModeSelected: (ThemeMode) -> Unit = {},
    isOnlineOverride: Boolean? = null,
    savedGuideRepositoryOverride: SavedGuideRepository? = null,
    publicProfileRepositoryOverride: PublicProfileRepository? = null,
) {
  val context = LocalContext.current
  val profileCheckErrorMessage = stringResource(R.string.register_profile_check_error)
  // Configure the shared Firestore instance before profile lookups can start it.
  val publicProfileRepository = remember {
    publicProfileRepositoryOverride ?: FirestorePublicProfileRepository()
  }
  val registerProfileViewModel: RegisterProfileViewModel = viewModel {
    RegisterProfileViewModel(publicProfileRepository)
  }
  val profileUiState by registerProfileViewModel.uiState.collectAsStateWithLifecycle()
  val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
  LaunchedEffect(authUiState.user) {
    if (authUiState.user == null) registerProfileViewModel.reset()
  }
  val isOnline = isOnlineOverride ?: rememberInternetConnectivity()
  val runOfflineAction = rememberOfflineActionGate(isOnline)
  // One instance shared by the collection, scan flow and Explore, so they see the same data.
  // It is created lazily, when the first of those screens needs it.
  val savedGuideRepository =
      remember(savedGuideRepositoryOverride) {
        lazy { savedGuideRepositoryOverride ?: FirestoreSavedGuideRepository() }
      }
  val locationPermissionDecisionRepository =
      remember(context.applicationContext) {
        SharedPreferencesLocationPermissionDecisionRepository(context.applicationContext)
      }
  val notificationPermissionDecisionRepository =
      remember(context.applicationContext) {
        SharedPreferencesNotificationPermissionDecisionRepository(context.applicationContext)
      }
  val navigateToHome = {
    navController.navigate(Routes.Home) { popUpTo(Routes.SignIn) { inclusive = true } }
  }
  val notificationPermissionController =
      rememberNotificationPermissionController(notificationPermissionDecisionRepository) {
        navigateToHome()
      }
  val requestNotificationPermission = {
    if (
        notificationPermissionController.state.value.decision ==
            NotificationPermissionDecision.NOT_ASKED
    ) {
      notificationPermissionController.requestPermission()
    } else {
      navigateToHome()
    }
  }
  val locationPermissionController =
      rememberLocationPermissionController(locationPermissionDecisionRepository) {
        requestNotificationPermission()
      }
  val cameraAvailable = rememberCameraAvailability()
  var showPermissionDialog by remember { mutableStateOf(false) }
  val permissionController = rememberCameraPermissionController { granted ->
    if (granted) {
      runOfflineAction(OfflineAction.OPEN_SCAN) { navController.navigate(Routes.Scan) }
    } else {
      showPermissionDialog = true
    }
  }
  val permissionState by permissionController.state.collectAsState()
  val currentBackStackEntry by navController.currentBackStackEntryAsState()
  val cancelProfileRegistration: () -> Unit = {
    val failure = authViewModel.signOut().exceptionOrNull()
    if (failure == null) {
      registerProfileViewModel.reset()
      navController.navigate(Routes.SignIn) { popUpTo(navController.graph.id) { inclusive = true } }
    } else {
      Toast.makeText(
              context,
              failure.localizedMessage ?: profileCheckErrorMessage,
              Toast.LENGTH_LONG,
          )
          .show()
    }
  }
  LaunchedEffect(
      profileUiState.status,
      profileUiState.errorMessage,
      currentBackStackEntry?.destination?.route,
  ) {
    when (profileUiState.status) {
      ProfileRegistrationStatus.REGISTRATION_REQUIRED -> {
        if (currentBackStackEntry?.destination?.route != Routes.RegisterProfile) {
          navController.navigate(Routes.RegisterProfile)
        }
      }
      ProfileRegistrationStatus.REGISTERED -> {
        if (
            currentBackStackEntry?.destination?.route == Routes.SignIn ||
                currentBackStackEntry?.destination?.route == Routes.RegisterProfile
        ) {
          if (
              locationPermissionController.state.value.decision ==
                  LocationPermissionDecision.NOT_ASKED
          ) {
            locationPermissionController.requestPermission()
          } else {
            requestNotificationPermission()
          }
        }
      }
      ProfileRegistrationStatus.ERROR -> {
        Toast.makeText(
                context,
                profileUiState.errorMessage ?: profileCheckErrorMessage,
                Toast.LENGTH_LONG,
            )
            .show()
      }
      else -> Unit
    }
  }
  val currentDestination =
      when (currentBackStackEntry?.destination?.route) {
        Routes.Friends -> DockDestination.FRIENDS
        Routes.Settings -> null
        Routes.Collection -> DockDestination.COLLECTION
        Routes.Explore -> DockDestination.EXPLORE
        else -> DockDestination.HOME
      }
  val dockedScreenBackground =
      if (MaterialTheme.isDark) MaterialTheme.colorScheme.background else ScreenBackground
  val onScanClick = {
    if (cameraAvailable) {
      runOfflineAction(OfflineAction.OPEN_SCAN) { permissionController.requestPermission() }
    }
  }
  val onDockDestinationSelected = { destination: DockDestination ->
    val navigate = {
      if (destination.route != currentBackStackEntry?.destination?.route) {
        if (destination.route == Routes.Home) {
          // Home is the navigation root: pop back to it instead of navigating,
          // which launchSingleTop would swallow because popUpTo(Home) already
          // leaves Home as the current destination.
          navController.popBackStack(Routes.Home, inclusive = false)
        } else {
          // Other tabs stack above Home so system back returns to it.
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
    }
    val offlineAction =
        when (destination) {
          DockDestination.EXPLORE -> OfflineAction.OPEN_EXPLORE
          DockDestination.FRIENDS -> OfflineAction.OPEN_FRIENDS
          else -> null
        }
    if (offlineAction == null) navigate() else runOfflineAction(offlineAction, navigate)
  }
  val onProfileClicked = {
    navController.navigate(Routes.Settings) {
      popUpTo(Routes.Home) {
        inclusive = false
        saveState = true
      }
      launchSingleTop = true
      restoreState = true
    }
  }
  // Screens apply their own status bar inset, so the top inset stays 0 and
  // containerColor covers the status bar strip.
  @Composable
  fun DockedScaffold(
      containerColor: androidx.compose.ui.graphics.Color,
      content: @Composable (PaddingValues) -> Unit,
  ) {
    Scaffold(
        containerColor = containerColor,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, dockContentClearance()),
        bottomBar = {
          AppBottomDock(
              selected = currentDestination,
              onDestinationSelected = onDockDestinationSelected,
              onScanClick = onScanClick,
              cameraAvailable = cameraAvailable,
          )
        },
    ) { innerPadding ->
      content(innerPadding)
    }
  }
  Surface(
      modifier = Modifier.fillMaxSize().semantics { testTag = C.Tag.main_screen_container },
      color = MaterialTheme.colorScheme.background,
  ) {
    NavHost(navController = navController, startDestination = Routes.SignIn) {
      composable(Routes.SignIn) {
        SignInScreen(
            authViewModel = authViewModel,
            onSignedIn = registerProfileViewModel::checkCurrentProfile,
            onSignInClickOverride = onSignInClickOverride,
        )
      }
      composable(Routes.RegisterProfile) {
        RegisterProfileScreen(registerProfileViewModel, onCancel = cancelProfileRegistration)
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
              onDockDestinationSelected = onDockDestinationSelected,
              onProfileClicked = onProfileClicked,
              isConnectedToInternet = isOnline,
          )
        }
      }
      composable(Routes.Scan) { ScanRoute(navController) }
      composable(
          "${Routes.ScanReview}?uri={uri}&gallery={gallery}",
          arguments =
              listOf(
                  navArgument("gallery") {
                    type = NavType.BoolType
                    defaultValue = false
                  }
              ),
      ) { backStackEntry ->
        val uri = backStackEntry.arguments?.getString("uri")?.let(Uri::parse)
        if (uri != null) {
          ScanReviewRoute(
              navController,
              uri,
              captureSenderOverride,
              savedGuideRepository = savedGuideRepository.value,
              onStartDiscovery = { proceed ->
                runOfflineAction(OfflineAction.START_DISCOVERY, proceed)
              },
              isGalleryImage = backStackEntry.arguments?.getBoolean("gallery") ?: false,
          )
        }
      }
      composable(Routes.Settings) {
        val dockClearance = dockContentClearance()
        DockedScaffold(containerColor = dockedScreenBackground) { innerPadding ->
          SettingsScreen(
              modifier =
                  Modifier.padding(
                      top = innerPadding.calculateTopPadding(),
                      bottom = dockClearance,
                  ),
              settingsViewModel = settingsViewModel,
              cameraPermissionController = permissionController,
              locationPermissionController = locationPermissionController,
              notificationPermissionController = notificationPermissionController,
              themeMode = themeMode,
              onThemeModeSelected = onThemeModeSelected,
              onManagePermissionsClick = { permissionController.openAppSettings() },
              onSignedOut = {
                authViewModel.clearSignedInUser()
                registerProfileViewModel.reset()
                navController.clearSavedUserNavigation()
                navController.navigate(Routes.SignIn) {
                  popUpTo(navController.graph.id) { inclusive = true }
                }
              },
          )
        }
      }

      composable(Routes.Collection) {
        val dockClearance = dockContentClearance()
        DockedScaffold(containerColor = dockedScreenBackground) { innerPadding ->
          CollectionRoute(
              repository = savedGuideRepository.value,
              modifier =
                  Modifier.padding(
                      top = innerPadding.calculateTopPadding(),
                      bottom = dockClearance,
                  ),
          )
        }
      }

      composable(Routes.Friends) {
        val homeViewModel: HomeViewModel = viewModel()
        val userName by homeViewModel.userName.collectAsState()
        val dockClearance = dockContentClearance()
        DockedScaffold(containerColor = dockedScreenBackground) { innerPadding ->
          FriendsRoute(
              modifier =
                  Modifier.padding(
                      top = innerPadding.calculateTopPadding(),
                      bottom = dockClearance,
                  ),
              profileName = userName,
              onProfileClicked = onProfileClicked,
          )
        }
      }

      composable(Routes.Explore) {
        val exploreViewModel: ExploreViewModel = viewModel {
          ExploreViewModel(AndroidLocationProvider(context), savedGuideRepository.value)
        }
        val explorePermissionController =
            rememberLocationPermissionController(
                remember(context.applicationContext) {
                  SharedPreferencesLocationPermissionDecisionRepository(context.applicationContext)
                },
            )
        val dockClearance = dockContentClearance()
        DockedScaffold(containerColor = ScreenBackground) { innerPadding ->
          Box(
              modifier =
                  Modifier.padding(
                      top = innerPadding.calculateTopPadding(),
                      bottom = dockClearance,
                  )
          ) {
            ExploreScreen(
                onBackClick = { navController.popBackStack() },
                exploreViewModel = exploreViewModel,
                locationPermissionController = explorePermissionController,
            )
          }
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
