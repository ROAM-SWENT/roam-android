package com.github.roamswent.roam.ui.permission

import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberCameraAvailability(hasCameraFeature: (() -> Boolean)? = null): Boolean {
  val context = LocalContext.current
  return remember {
    hasCameraFeature?.invoke()
        ?: context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
  }
}
