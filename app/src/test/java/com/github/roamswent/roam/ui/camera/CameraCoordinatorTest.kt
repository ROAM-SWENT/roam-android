package com.github.roamswent.roam.ui.camera

import android.content.Context
import android.net.Uri
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CameraCoordinatorTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val lifecycleOwner = TestLifecycleOwner()
  private val previewView by lazy { PreviewView(context) }

  @Test
  fun captureToBeforeStartReportsNotRunningWithoutCallingEngine() {
    val engine = FakeCameraEngine()
    val coordinator = coordinator(engine)
    val error = AtomicReference<Throwable>()

    coordinator.captureTo(targetUri(), {}, error::set)

    assertFalse(coordinator.isRunning)
    assertTrue(error.get() is IllegalStateException)
    assertEquals("Camera is not running", error.get()?.message)
    assertFalse(engine.takePictureCalled)
  }

  @Test
  fun boundCallbackMarksCoordinatorRunning() {
    val engine = FakeCameraEngine()
    val coordinator = coordinator(engine)

    coordinator.start(previewView)
    assertEquals(1, engine.binds.size)

    engine.deliverBound()

    assertTrue(coordinator.isRunning)
  }

  @Test
  fun failedBindLeavesCoordinatorStopped() {
    val engine = FakeCameraEngine()
    val coordinator = coordinator(engine)

    coordinator.start(previewView)
    engine.deliverFailed(IllegalStateException("bind failed"))

    assertFalse(coordinator.isRunning)
  }

  @Test
  fun staleBoundCallbackAfterRestartCannotMarkCoordinatorRunning() {
    val engine = FakeCameraEngine()
    val coordinator = coordinator(engine)

    coordinator.start(previewView)
    val firstBind = engine.binds.single()
    coordinator.stop()
    coordinator.start(previewView)
    val secondBind = engine.binds[1]

    firstBind.onBound()
    assertFalse(coordinator.isRunning)

    secondBind.onBound()
    assertTrue(coordinator.isRunning)
  }

  @Test
  fun staleFailedCallbackAfterRestartCannotStopCurrentGeneration() {
    val engine = FakeCameraEngine()
    val coordinator = coordinator(engine)

    coordinator.start(previewView)
    val firstBind = engine.binds.single()
    coordinator.stop()
    coordinator.start(previewView)
    val secondBind = engine.binds[1]
    secondBind.onBound()

    firstBind.onFailed(IllegalStateException("stale failure"))

    assertTrue(coordinator.isRunning)
  }

  @Test
  fun stopUnbindsEngineClearsStateAndIsIdempotent() {
    val engine = FakeCameraEngine()
    val coordinator = coordinator(engine)

    coordinator.start(previewView)
    engine.deliverBound()
    coordinator.stop()
    coordinator.stop()

    assertFalse(coordinator.isRunning)
    assertEquals(2, engine.unbindAllCallCount)
  }

  @Test
  fun stopBeforeLateBoundCallbackKeepsCoordinatorStopped() {
    val engine = FakeCameraEngine()
    val coordinator = coordinator(engine)

    coordinator.start(previewView)
    coordinator.stop()
    engine.deliverBound()

    assertFalse(coordinator.isRunning)
  }

  @Test
  fun runningCaptureForwardsFileAndSavedCallback() {
    val engine = FakeCameraEngine()
    val coordinator = coordinator(engine)
    val target = targetUri()
    var saved = false

    coordinator.start(previewView)
    engine.deliverBound()
    coordinator.captureTo(target, { saved = true }, {})
    engine.deliverSaved()

    assertSame(target, engine.takePictureUri)
    assertTrue(saved)
  }

  @Test
  fun runningCaptureForwardsErrorCallback() {
    val engine = FakeCameraEngine()
    val coordinator = coordinator(engine)
    val failure = IllegalStateException("capture failed")
    val received = AtomicReference<Throwable>()

    coordinator.start(previewView)
    engine.deliverBound()
    coordinator.captureTo(targetUri(), {}, received::set)
    engine.deliverCaptureError(failure)

    assertSame(failure, received.get())
  }

  private fun coordinator(engine: FakeCameraEngine) =
      CameraCoordinator(context, lifecycleOwner, engine)

  private fun targetUri() = Uri.parse("content://test/captures/capture.jpg")

  private class FakeCameraEngine : CameraEngine {
    data class BindCall(
        val context: Context,
        val lifecycleOwner: LifecycleOwner,
        val previewView: PreviewView,
        val onBound: () -> Unit,
        val onFailed: (Throwable) -> Unit,
    )

    val binds = mutableListOf<BindCall>()
    var unbindAllCallCount = 0
      private set

    var takePictureCalled = false
      private set

    var takePictureUri: Uri? = null
      private set

    private var savedCallback: (() -> Unit)? = null
    private var errorCallback: ((Throwable) -> Unit)? = null

    override fun bind(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onBound: () -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
      binds += BindCall(context, lifecycleOwner, previewView, onBound, onFailed)
    }

    override fun unbindAll() {
      unbindAllCallCount++
    }

    override fun takePicture(
        uri: Uri,
        onSaved: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
      takePictureCalled = true
      takePictureUri = uri
      savedCallback = onSaved
      errorCallback = onError
    }

    fun deliverBound() {
      binds.last().onBound()
    }

    fun deliverFailed(throwable: Throwable) {
      binds.last().onFailed(throwable)
    }

    fun deliverSaved() {
      savedCallback!!.invoke()
    }

    fun deliverCaptureError(throwable: Throwable) {
      errorCallback!!.invoke(throwable)
    }
  }

  private class TestLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
      get() = registry
  }
}
