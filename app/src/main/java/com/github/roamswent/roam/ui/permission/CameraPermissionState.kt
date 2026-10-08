package com.github.roamswent.roam.ui.permission

sealed interface CameraPermissionState {
  data object NotDetermined : CameraPermissionState

  data object Granted : CameraPermissionState

  data object Denied : CameraPermissionState
}
