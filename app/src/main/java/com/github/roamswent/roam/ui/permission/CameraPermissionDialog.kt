package com.github.roamswent.roam.ui.permission

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.github.roamswent.roam.resources.C

@Composable
fun CameraPermissionDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("Camera permission required") },
      text = { Text("Allow camera access in Settings to scan monuments.") },
      confirmButton = {
        TextButton(
            onClick = onOpenSettings,
            modifier = Modifier.semantics { testTag = C.Tag.camera_permission_settings_button },
        ) {
          Text("Open Settings")
        }
      },
      dismissButton = {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.semantics { testTag = C.Tag.camera_permission_dismiss_button },
        ) {
          Text("Dismiss")
        }
      },
      modifier = Modifier.semantics { testTag = C.Tag.camera_permission_dialog },
  )
}
