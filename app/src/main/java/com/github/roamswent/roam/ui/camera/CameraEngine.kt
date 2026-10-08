package com.github.roamswent.roam.ui.camera

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LifecycleOwner

internal interface CameraEngine {
  fun bind(
      context: Context,
      lifecycleOwner: LifecycleOwner,
      previewView: androidx.camera.view.PreviewView,
      onBound: () -> Unit,
      onFailed: (Throwable) -> Unit,
  )

  fun unbindAll()

  fun takePicture(uri: Uri, onSaved: () -> Unit, onError: (Throwable) -> Unit)
}
