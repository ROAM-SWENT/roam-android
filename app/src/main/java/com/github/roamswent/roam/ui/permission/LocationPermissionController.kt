/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.roamswent.roam.model.permission.LocationPermissionDecision
import com.github.roamswent.roam.model.permission.LocationPermissionDecisionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface LocationPermissionController {
  val state: StateFlow<LocationPermissionState>

  fun hasPermission(): Boolean

  fun refreshPermissionState()

  fun requestPermission()
}

@Composable
fun rememberLocationPermissionController(
    decisionRepository: LocationPermissionDecisionRepository,
    onPermissionResult: (Boolean) -> Unit = {},
): LocationPermissionController {
  val context = LocalContext.current
  val currentOnPermissionResult by rememberUpdatedState(onPermissionResult)
  val permissionController =
      remember(context, decisionRepository) {
        AndroidLocationPermissionController(context, decisionRepository)
      }
  val permissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
          results ->
        val granted = results.values.any { it }
        permissionController.recordRequestResult(granted)
      }
  val lifecycleOwner = LocalLifecycleOwner.current

  DisposableEffect(lifecycleOwner, permissionController) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) permissionController.refreshPermissionState()
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  permissionController.attachPermissionLauncher(
      launchPermissionRequest = {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        )
      },
      onPermissionResult = { granted -> currentOnPermissionResult(granted) },
  )
  return permissionController
}

private class AndroidLocationPermissionController(
    private val context: Context,
    private val decisionRepository: LocationPermissionDecisionRepository,
) : LocationPermissionController {

  private val permissionState =
      MutableStateFlow(
          LocationPermissionState(
              isGranted = hasPermission(),
              decision = decisionRepository.getDecision(),
          )
      )
  private var launchPermissionRequest: (() -> Unit)? = null

  override val state: StateFlow<LocationPermissionState> = permissionState.asStateFlow()

  override fun hasPermission(): Boolean =
      hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ||
          hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

  private var onPermissionResult: ((Boolean) -> Unit)? = null

  override fun refreshPermissionState() {
    permissionState.value = permissionState.value.copy(isGranted = hasPermission())
  }

  override fun requestPermission() {
    refreshPermissionState()
    if (permissionState.value.isGranted) {
      if (permissionState.value.decision == LocationPermissionDecision.NOT_ASKED) {
        recordRequestResult(granted = true)
      }
      return
    }
    checkNotNull(launchPermissionRequest) { "Location permission launcher is not attached" }()
  }

  fun recordRequestResult(granted: Boolean) {
    val decision =
        if (granted) LocationPermissionDecision.GRANTED else LocationPermissionDecision.DENIED
    decisionRepository.saveDecision(decision)
    permissionState.value =
        LocationPermissionState(isGranted = hasPermission(), decision = decision)
    checkNotNull(onPermissionResult) { "Location permission result callback is not attached" }(
        granted
    )
  }

  fun attachPermissionLauncher(
      launchPermissionRequest: () -> Unit,
      onPermissionResult: (Boolean) -> Unit,
  ) {
    this.launchPermissionRequest = launchPermissionRequest
    this.onPermissionResult = onPermissionResult
  }

  private fun hasPermission(permission: String): Boolean =
      ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
