// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.ui.scan

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

@Composable
fun rememberGalleryImagePicker(onImagePicked: (Uri?) -> Unit): () -> Unit {
  val launcher =
      rememberLauncherForActivityResult(
          contract = ActivityResultContracts.PickVisualMedia(),
          onResult = onImagePicked,
      )

  return {
    launcher.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
    )
  }
}
