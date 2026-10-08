package com.github.roamswent.roam.ui.camera

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LifecycleOwner

internal class CameraCoordinator(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val engine: CameraEngine,
) : CameraController {
  private var generation = 0
  private var running = false

  override val isRunning: Boolean
    get() = running

  override fun start(previewView: androidx.camera.view.PreviewView) {
    val currentGeneration = ++generation
    running = false
    engine.bind(
        context = context,
        lifecycleOwner = lifecycleOwner,
        previewView = previewView,
        onBound = {
          if (currentGeneration == generation) {
            running = true
          }
        },
        onFailed = {
          if (currentGeneration == generation) {
            running = false
          }
        },
    )
  }

  override fun stop() {
    generation++
    running = false
    engine.unbindAll()
  }

  override fun captureTo(
      uri: Uri,
      onSaved: () -> Unit,
      onError: (Throwable) -> Unit,
  ) {
    if (!running) {
      onError(IllegalStateException("Camera is not running"))
      return
    }
    engine.takePicture(uri, onSaved, onError)
  }
}
