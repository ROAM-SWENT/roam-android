package com.github.roamswent.roam.ui.scan

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.github.roamswent.roam.data.FileCapturedImageManager
import com.github.roamswent.roam.ui.camera.CameraCoordinator
import com.github.roamswent.roam.ui.camera.CameraXEngine
import com.github.roamswent.roam.ui.navigation.Routes
import com.github.roamswent.roam.ui.permission.rememberCameraPermissionController
import kotlinx.coroutines.Dispatchers

@Composable
fun ScanRoute(navController: NavHostController, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val activity = context as ComponentActivity
  val lifecycleOwner = LocalLifecycleOwner.current
  val permissionController = rememberCameraPermissionController {}
  val capturedImageManager = remember(context) { FileCapturedImageManager(context, Dispatchers.IO) }
  val cameraController =
      remember(activity) { CameraCoordinator(context, activity, CameraXEngine()) }
  val previewView = remember(context) { PreviewView(context) }
  val viewModel: ScanViewModel = viewModel {
    ScanViewModel(capturedImageManager, cameraController, Dispatchers.Main.immediate)
  }
  val state by viewModel.state.collectAsState()

  LaunchedEffect(viewModel, navController) {
    viewModel.navigationEvents.collect { event ->
      when (event) {
        is ScanNavigationEvent.NavigateToReview -> {
          navController.navigate("${Routes.ScanReview}?uri=${Uri.encode(event.uri.toString())}")
        }
      }
    }
  }

  DisposableEffect(lifecycleOwner, permissionController, cameraController) {
    val observer =
        object : DefaultLifecycleObserver {
          override fun onResume(owner: LifecycleOwner) {
            if (permissionController.hasPermission()) {
              cameraController.start(previewView)
            } else {
              viewModel.onPermissionRevoked()
            }
          }
        }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      cameraController.stop()
    }
  }

  BackHandler {
    cameraController.stop()
    navController.popBackStack()
  }
  ScanScreen(
      state = state,
      onCapture = viewModel::onCapture,
      onGoBack = {
        cameraController.stop()
        navController.popBackStack()
      },
      modifier = modifier,
      previewView = previewView,
  )
}
