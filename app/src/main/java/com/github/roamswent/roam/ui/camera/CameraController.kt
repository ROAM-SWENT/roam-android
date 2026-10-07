package com.github.roamswent.roam.ui.camera

import android.net.Uri
import androidx.camera.view.PreviewView

interface CameraController {
  fun start(previewView: PreviewView)

  fun stop()

  val isRunning: Boolean

  fun captureTo(uri: Uri, onSaved: () -> Unit, onError: (Throwable) -> Unit)
}
