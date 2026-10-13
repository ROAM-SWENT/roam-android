package com.github.roamswent.roam

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberAsyncImagePainter
import com.github.roamswent.roam.model.ai.FirebaseGeminiClient
import com.github.roamswent.roam.model.ai.GeminiImageScreen
import com.github.roamswent.roam.model.ai.GuideRepositoryImpl
import com.github.roamswent.roam.model.ai.ImagePreparer
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.ai.MonumentGuideRepository
import com.github.roamswent.roam.model.location.LocationProvider
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.home.HomeScreen
import com.github.roamswent.roam.ui.loading.LoadingRoute
import com.github.roamswent.roam.ui.monumentDetail.MonumentDetailScreen
import com.github.roamswent.roam.ui.navigation.Routes
import com.github.roamswent.roam.ui.permission.CameraPermissionDialog
import com.github.roamswent.roam.ui.permission.CameraPermissionState
import com.github.roamswent.roam.ui.permission.rememberCameraAvailability
import com.github.roamswent.roam.ui.permission.rememberCameraPermissionController
import com.github.roamswent.roam.ui.scan.ScanReviewScreen
import com.github.roamswent.roam.ui.scan.ScanRoute
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      SampleAppTheme {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()
        val guideRepository = remember { createGuideRepository() }
        val cameraAvailable = rememberCameraAvailability()
        var discoveryResult by remember { mutableStateOf<DiscoveryResult?>(null) }
        var showPermissionDialog by remember { mutableStateOf(false) }
        val permissionController = rememberCameraPermissionController { granted ->
          if (granted) {
            navController.navigate(Routes.Scan)
          } else {
            showPermissionDialog = true
          }
        }
        val permissionState by permissionController.state.collectAsState()
        Surface(
            modifier = Modifier.fillMaxSize().semantics { testTag = C.Tag.main_screen_container },
            color = MaterialTheme.colorScheme.background,
        ) {
          Box(Modifier.fillMaxSize()) {
            NavHost(navController = navController, startDestination = Routes.Home) {
              composable(Routes.Home) {
                HomeScreen(
                    onScanClicked = {
                      if (cameraAvailable) permissionController.requestPermission()
                    },
                    cameraAvailable = cameraAvailable,
                )
              }
              composable(Routes.Scan) { ScanRoute(navController) }
              composable("${Routes.ScanReview}?uri={uri}") { ScanReviewScreen() }
              composable("${Routes.Loading}?uri={uri}") { backStackEntry ->
                val imageUri =
                    requireNotNull(backStackEntry.arguments?.getString("uri")) {
                      "The loading destination requires a captured image URI."
                    }
                LoadingRoute(
                    imageUri = Uri.parse(imageUri),
                    onCancel = { navController.popBackStack(Routes.Home, inclusive = false) },
                    repository = guideRepository,
                    onDiscoverySuccess = { guide, capturedUri ->
                      discoveryResult = DiscoveryResult(guide, capturedUri)
                      navController.navigate(Routes.MonumentDetail)
                    },
                    onDiscoveryError = { message ->
                      navController.popBackStack(Routes.Home, inclusive = false)
                      coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                    },
                )
              }
              composable(Routes.MonumentDetail) {
                BackHandler { navController.popBackStack(Routes.Home, inclusive = false) }
                val result =
                    requireNotNull(discoveryResult) {
                      "The monument detail destination requires a discovery result."
                    }
                MonumentDetailScreen(
                    image = rememberAsyncImagePainter(result.imageUri),
                    text = result.guide.toDisplayText(),
                    onBackClick = { navController.popBackStack(Routes.Home, inclusive = false) },
                )
              }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier.align(Alignment.BottomCenter).semantics {
                      testTag = C.Tag.home_error_message
                    },
            )
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
    }
  }

  private fun createGuideRepository(): MonumentGuideRepository {
    val geminiClient = FirebaseGeminiClient(applicationContext)
    return GuideRepositoryImpl(
        imagePreparer = ImagePreparer(),
        imageScreen = GeminiImageScreen(geminiClient),
        geminiClient = geminiClient,
        locationProvider =
            object : LocationProvider {
              override suspend fun currentCoordinates() = null
            },
    )
  }
}

private data class DiscoveryResult(val guide: MonumentGuide, val imageUri: Uri)

private fun MonumentGuide.toDisplayText(): String = buildString {
  appendLine(monumentName)
  appendLine()
  appendLine(history)
  if (facts.isNotEmpty()) {
    appendLine()
    appendLine("Interesting facts")
    facts.forEach { appendLine("- $it") }
  }
  if (culture.isNotBlank()) {
    appendLine()
    appendLine("Cultural significance")
    appendLine(culture)
  }
}
