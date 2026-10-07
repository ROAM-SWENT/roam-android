package com.github.roamswent.roam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.home.HomeScreen
import com.github.roamswent.roam.ui.navigation.Routes
import com.github.roamswent.roam.ui.permission.CameraPermissionDialog
import com.github.roamswent.roam.ui.permission.CameraPermissionState
import com.github.roamswent.roam.ui.permission.rememberCameraAvailability
import com.github.roamswent.roam.ui.permission.rememberCameraPermissionController
import com.github.roamswent.roam.ui.scan.ScanRoute
import com.github.roamswent.roam.ui.theme.SampleAppTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      SampleAppTheme {
        val navController = rememberNavController()
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
        Surface(
            modifier = Modifier.fillMaxSize().semantics { testTag = C.Tag.main_screen_container },
            color = MaterialTheme.colorScheme.background,
        ) {
          NavHost(navController = navController, startDestination = Routes.Home) {
            composable(Routes.Home) {
              HomeScreen(
                  onScanClicked = { if (cameraAvailable) permissionController.requestPermission() },
                  cameraAvailable = cameraAvailable,
              )
            }
            composable(Routes.Scan) { ScanRoute(navController) }
            composable("${Routes.ScanReview}?uri={uri}") {
              androidx.compose.foundation.layout.Box(
                  Modifier.fillMaxSize().semantics { testTag = C.Tag.scan_review_placeholder }
              )
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
    }
  }
}
