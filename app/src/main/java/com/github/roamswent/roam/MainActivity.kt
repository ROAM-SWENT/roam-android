/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 * Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
 * Co-authored-by: Mistral Vibe <vibe@mistral.ai>
 * Co-authored-by: Codex <codex@openai.com>
 * Co-authored-by: Gemini 3.8 Flash <noreply@google.com>
 */

package com.github.roamswent.roam

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.github.roamswent.roam.model.basemap.AssetOfflineBaseMapRepository
import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.guide.DefaultMonumentSharingProvider
import com.github.roamswent.roam.model.guide.FirestoreSavedGuideRepository
import com.github.roamswent.roam.model.guide.MonumentSharingProvider
import com.github.roamswent.roam.model.guide.MonumentSharingRepository
import com.github.roamswent.roam.model.guide.SavedGuideRepository
import com.github.roamswent.roam.model.location.AndroidLocationProvider
import com.github.roamswent.roam.model.location.LocationProvider
import com.github.roamswent.roam.model.notification.FirestoreNotificationInstallationRepository
import com.github.roamswent.roam.model.notification.NotificationInstallationRepository
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
import com.github.roamswent.roam.ui.collection.FriendCollectionRoute
import com.github.roamswent.roam.ui.components.bottomdock.AppBottomDock
import com.github.roamswent.roam.ui.components.bottomdock.DockDestination
import com.github.roamswent.roam.ui.components.bottomdock.dockContentClearance
import com.github.roamswent.roam.ui.explore.ExploreScreen
import com.github.roamswent.roam.ui.explore.ExploreViewModel
import com.github.roamswent.roam.ui.friends.FriendsRoute
import com.github.roamswent.roam.ui.home.HomeScreen
import com.github.roamswent.roam.ui.home.HomeViewModel
import com.github.roamswent.roam.ui.home.rememberInternetConnectivity
import com.github.roamswent.roam.ui.monumentDetail.SavedMonumentDetailRoute
import com.github.roamswent.roam.ui.monumentDetail.SharedMonumentDetailRoute
import com.github.roamswent.roam.ui.navigation.OfflineAction
import com.github.roamswent.roam.ui.navigation.Routes
import com.github.roamswent.roam.ui.navigation.rememberOfflineActionGate
import com.github.roamswent.roam.ui.notifications.FriendRequestNotifications
import com.github.roamswent.roam.ui.notifications.NotificationNavigation
import com.github.roamswent.roam.ui.notifications.NotificationNavigationParser
import com.github.roamswent.roam.ui.notifications.NotificationNavigationRequest
import com.github.roamswent.roam.ui.permission.CameraPermissionDialog
import com.github.roamswent.roam.ui.permission.CameraPermissionState
import com.github.roamswent.roam.ui.permission.LocationPermissionController
import com.github.roamswent.roam.ui.permission.rememberCameraAvailability
import com.github.roamswent.roam.ui.permission.rememberCameraPermissionController
import com.github.roamswent.roam.ui.permission.rememberLocationPermissionController
import com.github.roamswent.roam.ui.permission.rememberNotificationPermissionController
import com.github.roamswent.roam.ui.profile.ProfileRegistrationStatus
import com.github.roamswent.roam.ui.profile.RegisterProfileScreen
import com.github.roamswent.roam.ui.profile.RegisterProfileViewModel
import com.github.roamswent.roam.ui.scan.ScanReviewRoute
import com.github.roamswent.roam.ui.scan.ScanRoute
import com.github.roamswent.roam.ui.scan.rememberGalleryImagePicker
import com.github.roamswent.roam.ui.settings.SettingsScreen
import com.github.roamswent.roam.ui.settings.SettingsViewModel
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import com.github.roamswent.roam.ui.theme.ThemeViewModel
import com.github.roamswent.roam.ui.theme.ThemeViewModelFactory
import com.github.roamswent.roam.ui.theme.screenBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow

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

private const val TAG = "RoamMainActivity"

class MainActivity : AppCompatActivity() {
  private val notificationNavigationEvents =
      MutableSharedFlow<NotificationNavigationRequest>(extraBufferCapacity = 1)
  private val notificationNavigationFlow: Flow<NotificationNavigationRequest> =
      notificationNavigationEvents

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    RoamApplication.snapDefaultLocale()
    setContent {
      val themeRepository = remember { DataStoreThemePreferencesRepository(applicationContext) }
      val themeViewModel: ThemeViewModel =
          viewModel(factory = remember(themeRepository) { ThemeViewModelFactory(themeRepository) })
      val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()

      val selectedTheme = themeMode ?: ThemeMode.AUTO
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
            notificationNavigationOnLaunch =
                NotificationNavigationParser.parse(intent)?.let {
                  NotificationNavigationRequest(it, preserveBackStack = false)
                },
            notificationNavigationEvents = notificationNavigationFlow,
        )
      }
    }
  }

  /**
   * Routes notification taps received by an existing singleTop activity through the app graph.
   *
   * The matching ACTION is private to the app's notification PendingIntent; other intents are
   * ignored so unrelated launches do not affect navigation.
   */
  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    NotificationNavigationParser.parse(intent)?.let {
      notificationNavigationEvents.tryEmit(
          NotificationNavigationRequest(it, preserveBackStack = true)
      )
    }
  }
}

/** Optional dependencies and callbacks used to isolate app journeys from external services. */
internal data class RoamAppTestOverrides(
    val captureSender: CaptureSender? = null,
    val galleryImageUri: Uri? = null,
    val onSignInClick: ((() -> Unit) -> Unit)? = null,
    val onSignedIn: ((LocationPermissionController) -> Unit)? = null,
    val settingsViewModel: SettingsViewModel? = null,
    val isOnline: Boolean? = null,
    val savedGuideRepository: SavedGuideRepository? = null,
    val publicProfileRepository: PublicProfileRepository? = null,
    val friendRepository: FriendRepository? = null,
    val monumentSharingRepository: MonumentSharingRepository? = null,
    val monumentSharingProvider: MonumentSharingProvider? = null,
    val exploreLocationProvider: LocationProvider? = null,
    val exploreLocationPermissionController: LocationPermissionController? = null,
    val notificationInstallationRepository: NotificationInstallationRepository? = null,
)

@Composable
internal fun RoamApp(
    authViewModel: SignInViewModel,
    navController: NavHostController = rememberNavController(),
    themeMode: ThemeMode = ThemeMode.AUTO,
    onThemeModeSelected: (ThemeMode) -> Unit = {},
    testOverrides: RoamAppTestOverrides? = null,
    notificationNavigationOnLaunch: NotificationNavigationRequest? = null,
    notificationNavigationEvents: Flow<NotificationNavigationRequest> = emptyFlow(),
) {
  val context = LocalContext.current
  val profileCheckErrorMessage = stringResource(R.string.register_profile_check_error)
  // Configure the shared Firestore instance before profile lookups can start it.
  val monumentSharingRepository = remember {
    testOverrides?.monumentSharingRepository
        ?: (testOverrides?.monumentSharingProvider ?: DefaultMonumentSharingProvider()).repository()
  }
  val monumentSharingProvider =
      remember(monumentSharingRepository) { MonumentSharingProvider { monumentSharingRepository } }
  val publicProfileRepository = remember {
    testOverrides?.publicProfileRepository ?: FirestorePublicProfileRepository()
  }
  val registerProfileViewModel: RegisterProfileViewModel = viewModel {
    RegisterProfileViewModel(publicProfileRepository)
  }
  val profileUiState by registerProfileViewModel.uiState.collectAsStateWithLifecycle()
  val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
  var pendingNotificationNavigation by remember { mutableStateOf(notificationNavigationOnLaunch) }
  LaunchedEffect(notificationNavigationEvents) {
    notificationNavigationEvents.collect { pendingNotificationNavigation = it }
  }
  LaunchedEffect(authUiState.user) {
    if (authUiState.user == null) registerProfileViewModel.reset()
  }
  val isOnline = testOverrides?.isOnline ?: rememberInternetConnectivity()
  val runOfflineAction = rememberOfflineActionGate(isOnline)
  // One instance shared by the collection, scan flow and Explore, so they see the same data.
  // It is created lazily, when the first of those screens needs it.
  val savedGuideRepository =
      remember(testOverrides?.savedGuideRepository) {
        lazy { testOverrides?.savedGuideRepository ?: FirestoreSavedGuideRepository() }
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
  val notificationPermissionState by notificationPermissionController.state.collectAsState()
  val notificationInstallationRepository =
      remember(testOverrides?.notificationInstallationRepository) {
        testOverrides?.notificationInstallationRepository
            ?: FirestoreNotificationInstallationRepository()
      }
  LaunchedEffect(
      profileUiState.status,
      notificationPermissionState.isGranted,
      authUiState.user?.uid,
      notificationInstallationRepository,
  ) {
    if (profileUiState.status == ProfileRegistrationStatus.REGISTERED) {
      try {
        if (notificationPermissionState.isGranted) {
          notificationInstallationRepository.synchronizeCurrentInstallation()
        } else {
          notificationInstallationRepository.removeCurrentInstallation()
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Log.e(TAG, "Could not register the notification installation.", exception)
      }
    }
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
  LaunchedEffect(
      profileUiState.status,
      pendingNotificationNavigation,
      currentBackStackEntry?.destination?.route,
  ) {
    val route = currentBackStackEntry?.destination?.route
    if (
        profileUiState.status == ProfileRegistrationStatus.REGISTERED &&
            route != null &&
            route !in setOf(Routes.SignIn, Routes.RegisterProfile) &&
            pendingNotificationNavigation != null
    ) {
      val notification = checkNotNull(pendingNotificationNavigation)
      when (val navigation = notification.navigation) {
        NotificationNavigation.Friends -> {
          if (route != Routes.Friends) {
            navController.navigate(Routes.Friends) {
              if (!notification.preserveBackStack) {
                popUpTo(Routes.Home) { inclusive = false }
              }
              launchSingleTop = true
              restoreState = true
            }
          }
        }
        is NotificationNavigation.SharedMonument -> {
          navController.navigate(
              Routes.sharedMonumentDetail(navigation.ownerUid, navigation.guideId)
          ) {
            if (!notification.preserveBackStack) {
              popUpTo(Routes.Home) { inclusive = false }
            }
            launchSingleTop = true
          }
        }
      }
      pendingNotificationNavigation = null
    }
  }
  val currentDestination =
      when (currentBackStackEntry?.destination?.route) {
        Routes.Friends -> DockDestination.FRIENDS
        Routes.Settings -> null
        Routes.FriendCollectionPattern -> null
        Routes.Collection -> DockDestination.COLLECTION
        Routes.Explore -> DockDestination.EXPLORE
        else -> DockDestination.HOME
      }
  val dockedScreenBackground = MaterialTheme.screenBackground
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
  val onProfileClicked = { navController.navigate(Routes.Settings) { launchSingleTop = true } }
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
            onSignedIn =
                testOverrides?.onSignedIn?.let { onSignedIn ->
                  { onSignedIn(locationPermissionController) }
                } ?: registerProfileViewModel::checkCurrentProfile,
            onSignInClickOverride = testOverrides?.onSignInClick,
        )
      }
      composable(Routes.RegisterProfile) {
        RegisterProfileScreen(registerProfileViewModel, onCancel = cancelProfileRegistration)
      }
      composable(Routes.Home) {
        val homeViewModel: HomeViewModel = viewModel {
          HomeViewModel(publicProfileRepository = publicProfileRepository)
        }
        val userName by homeViewModel.userName.collectAsState()
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, homeViewModel) {
          val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) homeViewModel.refreshUserName()
          }
          lifecycleOwner.lifecycle.addObserver(observer)
          onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        // HomeScreen and the dock apply their own status/nav bar insets, so the
        // scaffold only contributes the ScreenBackground behind the status bar.
        Scaffold(
            containerColor = MaterialTheme.screenBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { innerPadding ->
          HomeScreen(
              modifier = Modifier.padding(innerPadding).background(MaterialTheme.screenBackground),
              onScanClicked = onScanClick,
              cameraAvailable = cameraAvailable,
              userName = userName,
              onDockDestinationSelected = onDockDestinationSelected,
              onProfileClicked = onProfileClicked,
              isConnectedToInternet = isOnline,
          )
        }
      }
      composable(Routes.Scan) {
        ScanRoute(navController, galleryImageUriOverride = testOverrides?.galleryImageUri)
      }
      composable(
          "${Routes.ScanReview}?uri={uri}&gallery={gallery}&fromCollection={fromCollection}",
          arguments =
              listOf(
                  navArgument("gallery") {
                    type = NavType.BoolType
                    defaultValue = false
                  },
                  navArgument("fromCollection") {
                    type = NavType.BoolType
                    defaultValue = false
                  },
              ),
      ) { backStackEntry ->
        val uri = backStackEntry.arguments?.getString("uri")?.let(Uri::parse)
        if (uri != null) {
          ScanReviewRoute(
              navController,
              uri,
              testOverrides?.captureSender,
              savedGuideRepository = savedGuideRepository.value,
              onStartDiscovery = { proceed ->
                runOfflineAction(OfflineAction.START_DISCOVERY, proceed)
              },
              isGalleryImage = backStackEntry.arguments?.getBoolean("gallery") ?: false,
              fromCollection = backStackEntry.arguments?.getBoolean("fromCollection") ?: false,
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
              settingsViewModel = testOverrides?.settingsViewModel,
              publicProfileRepository = publicProfileRepository,
              cameraPermissionController = permissionController,
              locationPermissionController = locationPermissionController,
              notificationPermissionController = notificationPermissionController,
              notificationInstallationRepository = notificationInstallationRepository,
              themeMode = themeMode,
              onThemeModeSelected = onThemeModeSelected,
              onManagePermissionsClick = { permissionController.openAppSettings() },
              onSignedOut = {
                FriendRequestNotifications.cancelAllFriendRequests(context)
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
        val homeViewModel: HomeViewModel = viewModel()
        val userName by homeViewModel.userName.collectAsState()
        val dockClearance = dockContentClearance()
        val onGalleryImagePicked: (Uri?) -> Unit = { uri ->
          uri?.let {
            navController.navigate(
                "${Routes.ScanReview}?uri=${Uri.encode(it.toString())}&gallery=true&fromCollection=true",
            )
          }
        }
        val pickFromGallery = rememberGalleryImagePicker(onGalleryImagePicked)
        DockedScaffold(containerColor = dockedScreenBackground) { innerPadding ->
          CollectionRoute(
              repository = savedGuideRepository.value,
              modifier =
                  Modifier.padding(
                      top = innerPadding.calculateTopPadding(),
                      bottom = dockClearance,
                  ),
              onGuideClick = { guideId -> navController.navigate(Routes.monumentDetail(guideId)) },
              friendRepository = testOverrides?.friendRepository,
              publicProfileRepository = publicProfileRepository,
              monumentSharingProvider = monumentSharingProvider,
              profileName = userName,
              onProfileClicked = onProfileClicked,
              onPickFromGallery = pickFromGallery,
          )
        }
      }
      composable(
          Routes.MonumentDetailPattern,
          arguments =
              listOf(navArgument(Routes.MonumentDetailGuideIdArg) { type = NavType.StringType }),
      ) { backStackEntry ->
        val guideId = backStackEntry.arguments?.getString(Routes.MonumentDetailGuideIdArg)
        if (guideId != null) {
          SavedMonumentDetailRoute(
              guideId = guideId,
              repository = savedGuideRepository.value,
              onBackClick = { navController.popBackStack() },
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
              onFriendCollectionClicked = { friend ->
                navController.navigate(Routes.friendCollection(friend.id, friend.username))
              },
              friendRepository = testOverrides?.friendRepository,
              publicProfileRepository = publicProfileRepository,
          )
        }
      }

      composable(
          Routes.FriendCollectionPattern,
          arguments =
              listOf(
                  navArgument(Routes.FriendUidArg) { type = NavType.StringType },
                  navArgument(Routes.FriendNameArg) {
                    type = NavType.StringType
                    defaultValue = ""
                  },
              ),
      ) { backStackEntry ->
        val friendUid = backStackEntry.arguments?.getString(Routes.FriendUidArg)
        val friendName = backStackEntry.arguments?.getString(Routes.FriendNameArg).orEmpty()
        if (friendUid != null) {
          val dockClearance = dockContentClearance()
          DockedScaffold(containerColor = dockedScreenBackground) { innerPadding ->
            FriendCollectionRoute(
                ownerUid = friendUid,
                ownerName = friendName.ifBlank { friendUid },
                repository = monumentSharingRepository,
                onBackClick = { navController.popBackStack() },
                modifier =
                    Modifier.padding(
                        top = innerPadding.calculateTopPadding(),
                        bottom = dockClearance,
                    ),
                onGuideClick = { guideId ->
                  navController.navigate(Routes.sharedMonumentDetail(friendUid, guideId))
                },
            )
          }
        }
      }
      composable(
          Routes.SharedMonumentDetailPattern,
          arguments =
              listOf(
                  navArgument(Routes.FriendUidArg) { type = NavType.StringType },
                  navArgument(Routes.MonumentDetailGuideIdArg) { type = NavType.StringType },
              ),
      ) { backStackEntry ->
        val friendUid = backStackEntry.arguments?.getString(Routes.FriendUidArg)
        val guideId = backStackEntry.arguments?.getString(Routes.MonumentDetailGuideIdArg)
        if (friendUid != null && guideId != null) {
          SharedMonumentDetailRoute(
              ownerUid = friendUid,
              guideId = guideId,
              repository = monumentSharingRepository,
              onBackClick = { navController.popBackStack() },
          )
        }
      }

      composable(Routes.Explore) {
        val homeViewModel: HomeViewModel = viewModel()
        val userName by homeViewModel.userName.collectAsState()
        val exploreViewModel: ExploreViewModel = viewModel {
          ExploreViewModel(
              testOverrides?.exploreLocationProvider ?: AndroidLocationProvider(context),
              savedGuideRepository.value,
              AssetOfflineBaseMapRepository(context),
          )
        }
        val explorePermissionController =
            testOverrides?.exploreLocationPermissionController
                ?: rememberLocationPermissionController(
                    remember(context.applicationContext) {
                      SharedPreferencesLocationPermissionDecisionRepository(
                          context.applicationContext
                      )
                    },
                )
        val dockClearance = dockContentClearance()
        DockedScaffold(containerColor = MaterialTheme.screenBackground) { innerPadding ->
          Box(
              modifier =
                  Modifier.padding(
                      top = innerPadding.calculateTopPadding(),
                      bottom = dockClearance,
                  )
          ) {
            ExploreScreen(
                onProfileClicked = onProfileClicked,
                profileName = userName,
                isOnline = isOnline,
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
