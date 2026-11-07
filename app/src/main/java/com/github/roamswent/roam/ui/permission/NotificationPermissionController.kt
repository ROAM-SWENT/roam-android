/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.roamswent.roam.model.permission.NotificationPermissionDecision
import com.github.roamswent.roam.model.permission.NotificationPermissionDecisionRepository
import com.github.roamswent.roam.model.permission.SharedPreferencesNotificationPermissionDecisionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Current notification access and the separately persisted result of the app's last request. */
data class NotificationPermissionState(
    val isGranted: Boolean,
    val decision: NotificationPermissionDecision,
) {
  val hasBeenAsked: Boolean
    get() = decision != NotificationPermissionDecision.NOT_ASKED
}

interface NotificationPermissionController {
  val state: StateFlow<NotificationPermissionState>

  fun hasPermission(): Boolean

  fun refreshPermissionState()

  fun requestPermission()
}

@Composable
fun rememberNotificationPermissionController(
    decisionRepository: NotificationPermissionDecisionRepository? = null,
    onPermissionResult: (Boolean) -> Unit = {},
): NotificationPermissionController {
  val context = LocalContext.current
  val defaultDecisionRepository = remember {
    SharedPreferencesNotificationPermissionDecisionRepository(context.applicationContext)
  }
  val resolvedDecisionRepository = decisionRepository ?: defaultDecisionRepository
  val currentOnPermissionResult by rememberUpdatedState(onPermissionResult)
  val permissionController =
      remember(context, resolvedDecisionRepository) {
        AndroidNotificationPermissionController(context, resolvedDecisionRepository)
      }
  val permissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
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
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      },
      onPermissionResult = { granted -> currentOnPermissionResult(granted) },
  )
  return permissionController
}

internal class AndroidNotificationPermissionController(
    private val context: Context,
    private val decisionRepository: NotificationPermissionDecisionRepository,
) : NotificationPermissionController {

  private val permissionState =
      MutableStateFlow(
          NotificationPermissionState(
              isGranted = hasPermission(),
              decision = decisionRepository.getDecision(),
          )
      )
  private var launchPermissionRequest: (() -> Unit)? = null
  private var onPermissionResult: ((Boolean) -> Unit)? = null

  override val state: StateFlow<NotificationPermissionState> = permissionState.asStateFlow()

  override fun hasPermission(): Boolean =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
      } else {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
      }

  override fun refreshPermissionState() {
    permissionState.value = permissionState.value.copy(isGranted = hasPermission())
  }

  override fun requestPermission() {
    refreshPermissionState()
    if (permissionState.value.isGranted) {
      if (permissionState.value.decision == NotificationPermissionDecision.NOT_ASKED) {
        recordRequestResult(granted = true)
      } else {
        checkNotNull(onPermissionResult) {
          "Notification permission result callback is not attached"
        }(true)
      }
      return
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      recordRequestResult(granted = false)
      return
    }
    checkNotNull(launchPermissionRequest) { "Notification permission launcher is not attached" }()
  }

  fun recordRequestResult(granted: Boolean) {
    val decision =
        if (granted) {
          NotificationPermissionDecision.GRANTED
        } else {
          NotificationPermissionDecision.DENIED
        }
    decisionRepository.saveDecision(decision)
    permissionState.value =
        NotificationPermissionState(isGranted = hasPermission(), decision = decision)
    checkNotNull(onPermissionResult) { "Notification permission result callback is not attached" }(
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
}
