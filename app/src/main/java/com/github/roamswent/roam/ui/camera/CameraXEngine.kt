package com.github.roamswent.roam.ui.camera

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor

internal class CameraXEngine : CameraEngine {
  private var cameraProvider: ProcessCameraProvider? = null
  private var imageCapture: ImageCapture? = null
  private var appContext: Context? = null
  private var executor: Executor? = null

  override fun bind(
      context: Context,
      lifecycleOwner: LifecycleOwner,
      previewView: PreviewView,
      onBound: () -> Unit,
      onFailed: (Throwable) -> Unit,
  ) {
    appContext = context
    val mainExecutor = ContextCompat.getMainExecutor(context)
    executor = mainExecutor
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener(
        {
          val provider =
              try {
                providerFuture.get()
              } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                onFailed(exception)
                return@addListener
              } catch (exception: ExecutionException) {
                onFailed(exception)
                return@addListener
              }

          try {
            val preview = Preview.Builder().build()
            preview.surfaceProvider = previewView.surfaceProvider
            val capture = ImageCapture.Builder().build()
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                capture,
            )
            cameraProvider = provider
            imageCapture = capture
            onBound()
          } catch (throwable: Throwable) {
            onFailed(throwable)
          }
        },
        mainExecutor,
    )
  }

  override fun unbindAll() {
    cameraProvider?.unbindAll()
    cameraProvider = null
    imageCapture = null
    appContext = null
    executor = null
  }

  override fun takePicture(uri: Uri, onSaved: () -> Unit, onError: (Throwable) -> Unit) {
    val capture =
        imageCapture ?: return onError(IllegalStateException("Camera engine is not bound"))
    val contentResolver =
        appContext?.contentResolver
            ?: return onError(IllegalStateException("Camera engine is not bound"))
    val mainExecutor =
        executor ?: return onError(IllegalStateException("Camera engine is not bound"))
    capture.takePicture(
        ImageCapture.OutputFileOptions.Builder(contentResolver, uri, ContentValues()).build(),
        mainExecutor,
        object : ImageCapture.OnImageSavedCallback {
          override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            onSaved()
          }

          override fun onError(exception: ImageCaptureException) {
            onError(exception)
          }
        },
    )
  }
}
