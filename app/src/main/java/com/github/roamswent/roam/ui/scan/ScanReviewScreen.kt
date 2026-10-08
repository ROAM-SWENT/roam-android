package com.github.roamswent.roam.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.scan.components.ExpressiveFloatingButton
import com.github.roamswent.roam.ui.theme.ShutterWhite

@Composable
fun ScanReviewScreen(
    state: ScanReviewUiState,
    onRetake: () -> Unit,
    onRetakeConfirm: () -> Unit,
    onRetakeCancel: () -> Unit,
    onRetryDelete: () -> Unit = {},
    onDismissDeleteError: () -> Unit = {},
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val uri =
      when (state) {
        is ScanReviewUiState.Reviewing -> state.uri
        is ScanReviewUiState.ConfirmRetake -> state.uri
        is ScanReviewUiState.Deleting -> state.uri
        is ScanReviewUiState.DeleteFailed -> state.uri
        is ScanReviewUiState.Sending -> state.uri
      }
  val locked =
      state is ScanReviewUiState.Deleting ||
          state is ScanReviewUiState.DeleteFailed ||
          state is ScanReviewUiState.Sending
  Box(modifier.fillMaxSize().semantics { testTag = C.Tag.scan_review_screen }) {
    AsyncImage(
        model =
            ImageRequest.Builder(context)
                .data(uri)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().semantics { testTag = C.Tag.scan_review_photo },
    )
    Row(
        modifier =
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 56.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      ExpressiveFloatingButton(
          onClick = onRetake,
          enabled = !locked,
          shape = RoundedCornerShape(24.dp),
          modifier = Modifier.size(80.dp).semantics { testTag = C.Tag.scan_retake_button },
      ) {
        Icon(Icons.Default.Refresh, contentDescription = "Retake", tint = ShutterWhite)
      }
      ExpressiveFloatingButton(
          onClick = onSend,
          enabled = !locked,
          shape = RoundedCornerShape(24.dp),
          modifier = Modifier.size(80.dp).semantics { testTag = C.Tag.scan_validate_button },
      ) {
        Icon(Icons.Default.Check, contentDescription = "Validate", tint = ShutterWhite)
      }
    }
  }
  if (state is ScanReviewUiState.ConfirmRetake) {
    RetakeWarningDialog(onConfirm = onRetakeConfirm, onCancel = onRetakeCancel)
  }
  if (state is ScanReviewUiState.DeleteFailed) {
    DeleteFailedDialog(onRetry = onRetryDelete, onStay = onDismissDeleteError)
  }
  if (state is ScanReviewUiState.Sending) {
    SendingDialog()
  }
}

@Composable
private fun DeleteFailedDialog(onRetry: () -> Unit, onStay: () -> Unit) {
  AlertDialog(
      onDismissRequest = onStay,
      title = { Text("Delete failed") },
      text = { Text("The photo could not be deleted and is still on the device.") },
      confirmButton = {
        TextButton(
            onClick = onRetry,
            modifier = Modifier.semantics { testTag = C.Tag.scan_retake_delete_retry },
        ) {
          Text("Retry")
        }
      },
      dismissButton = {
        TextButton(
            onClick = onStay,
            modifier = Modifier.semantics { testTag = C.Tag.scan_retake_delete_stay },
        ) {
          Text("Stay")
        }
      },
      modifier = Modifier.semantics { testTag = C.Tag.scan_retake_delete_failed_dialog },
  )
}

@Composable
private fun RetakeWarningDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
  AlertDialog(
      onDismissRequest = onCancel,
      title = { Text("Retake photo?") },
      text = { Text("The image will be permanently deleted from the device.") },
      confirmButton = {
        TextButton(
            onClick = onConfirm,
            modifier = Modifier.semantics { testTag = C.Tag.scan_retake_confirm },
        ) {
          Text("Agree")
        }
      },
      dismissButton = {
        TextButton(
            onClick = onCancel,
            modifier = Modifier.semantics { testTag = C.Tag.scan_retake_cancel },
        ) {
          Text("Cancel")
        }
      },
      modifier = Modifier.semantics { testTag = C.Tag.scan_retake_dialog },
  )
}

@Composable
private fun SendingDialog() {
  AlertDialog(
      onDismissRequest = {},
      title = { Text("Image being treated...") },
      text = { Text("Please wait.") },
      confirmButton = {},
      modifier = Modifier.semantics { testTag = C.Tag.scan_sending_dialog },
  )
}
