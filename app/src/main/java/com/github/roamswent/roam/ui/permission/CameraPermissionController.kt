package com.github.roamswent.roam.ui.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface CameraPermissionController {
  val state: StateFlow<CameraPermissionState>

  fun hasPermission(): Boolean

  fun requestPermission()

  fun openAppSettings()
}

@Composable
fun rememberCameraPermissionController(
    onPermissionResult: (Boolean) -> Unit
): CameraPermissionController {
  val context = LocalContext.current
  val currentOnPermissionResult by rememberUpdatedState(onPermissionResult)
  val permissionState = remember {
    MutableStateFlow<CameraPermissionState>(CameraPermissionState.NotDetermined)
  }
  val permissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val state =
            if (granted) {
              CameraPermissionState.Granted
            } else {
              CameraPermissionState.Denied(
                  showRationale =
                      ActivityCompat.shouldShowRequestPermissionRationale(
                          context as Activity,
                          Manifest.permission.CAMERA,
                      )
              )
            }
        permissionState.value = state
        currentOnPermissionResult(granted)
      }

  return remember(context) {
    AndroidCameraPermissionController(
        context = context,
        permissionState = permissionState,
        launchPermissionRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        onPermissionResult = { granted -> currentOnPermissionResult(granted) },
    )
  }
}

private class AndroidCameraPermissionController(
    private val context: Context,
    private val permissionState: MutableStateFlow<CameraPermissionState>,
    private val launchPermissionRequest: () -> Unit,
    private val onPermissionResult: (Boolean) -> Unit,
) : CameraPermissionController {
  override val state: StateFlow<CameraPermissionState> = permissionState.asStateFlow()

  override fun hasPermission(): Boolean =
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
          PackageManager.PERMISSION_GRANTED

  override fun requestPermission() {
    if (hasPermission()) {
      permissionState.value = CameraPermissionState.Granted
      onPermissionResult(true)
    } else {
      launchPermissionRequest()
    }
  }

  override fun openAppSettings() {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
    )
  }
}
