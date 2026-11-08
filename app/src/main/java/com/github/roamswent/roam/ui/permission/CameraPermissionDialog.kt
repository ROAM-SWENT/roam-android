package com.github.roamswent.roam.ui.permission

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C

@Composable
fun CameraPermissionDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.camera_permission_title)) },
      text = { Text(stringResource(R.string.camera_permission_message)) },
      confirmButton = {
        TextButton(
            onClick = onOpenSettings,
            modifier = Modifier.semantics { testTag = C.Tag.camera_permission_settings_button },
        ) {
          Text(stringResource(R.string.camera_permission_open_settings))
        }
      },
      dismissButton = {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.semantics { testTag = C.Tag.camera_permission_dismiss_button },
        ) {
          Text(stringResource(R.string.camera_permission_dismiss))
        }
      },
      modifier = Modifier.semantics { testTag = C.Tag.camera_permission_dialog },
  )
}
