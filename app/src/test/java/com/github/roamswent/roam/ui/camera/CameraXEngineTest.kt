package com.github.roamswent.roam.ui.camera

import android.content.Context
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CameraXEngineTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val lifecycleOwner = TestLifecycleOwner()
  private val previewView by lazy { PreviewView(context) }

  @After
  fun tearDown() {
    Thread.interrupted()
  }

  @Test
  fun stopBeforeProviderCompletesDoesNotBind() {
    val future = FakeProviderFuture()
    val provider = mockk<ProcessCameraProvider>(relaxed = true)
    val engine = engine(future)
    var bound = 0
    var failed = 0

    engine.bind(
        context,
        lifecycleOwner,
        previewView,
        onBound = { bound++ },
        onFailed = { failed++ },
    )
    engine.unbindAll()
    future.complete(provider)
    idleMainLooper()

    assertEquals(0, bound)
    assertEquals(0, failed)
    verify(exactly = 0) { provider.bindToLifecycle(any(), any(), any(), any()) }
  }

  @Test
  fun supersededBindIsIgnoredWhenNewerBindSucceeds() {
    val first = FakeProviderFuture()
    val second = FakeProviderFuture()
    val provider = mockk<ProcessCameraProvider>(relaxed = true)
    val pending = ArrayDeque(listOf(first, second))
    val engine = CameraXEngine { pending.removeFirst() }
    var firstBound = 0
    var secondBound = 0

    engine.bind(
        context,
        lifecycleOwner,
        previewView,
        onBound = { firstBound++ },
        onFailed = {},
    )
    engine.bind(
        context,
        lifecycleOwner,
        previewView,
        onBound = { secondBound++ },
        onFailed = {},
    )

    first.complete(provider)
    idleMainLooper()
    verify(exactly = 0) { provider.bindToLifecycle(any(), any(), any(), any()) }
    assertEquals(0, firstBound)
    assertEquals(0, secondBound)

    second.complete(provider)
    idleMainLooper()
    verify(exactly = 1) {
      provider.bindToLifecycle(
          lifecycleOwner,
          CameraSelector.DEFAULT_BACK_CAMERA,
          any<Preview>(),
          any<ImageCapture>(),
      )
    }
    assertEquals(0, firstBound)
    assertEquals(1, secondBound)
  }

  @Test
  fun bindResolvesProviderBindsUseCasesAndReportsBound() {
    val future = FakeProviderFuture()
    val provider = mockk<ProcessCameraProvider>(relaxed = true)
    val engine = engine(future)
    var bound = 0
    var failed = 0

    engine.bind(
        context,
        lifecycleOwner,
        previewView,
        onBound = { bound++ },
        onFailed = { failed++ },
    )
    future.complete(provider)
    idleMainLooper()

    val preview = slot<Preview>()
    val capture = slot<ImageCapture>()
    verifyOrder {
      provider.unbindAll()
      provider.bindToLifecycle(
          lifecycleOwner,
          CameraSelector.DEFAULT_BACK_CAMERA,
          capture(preview),
          capture(capture),
      )
    }
    assertTrue(preview.isCaptured)
    assertTrue(capture.isCaptured)
    assertEquals(1, bound)
    assertEquals(0, failed)
  }

  @Test
  fun bindFailureIsForwardedAndNothingIsBound() {
    val future = FakeProviderFuture()
    val provider = mockk<ProcessCameraProvider>(relaxed = true)
    val engine = engine(future)
    val cause = IllegalStateException("provider failed")
    val received = mutableListOf<Throwable>()
    var bound = 0

    engine.bind(
        context,
        lifecycleOwner,
        previewView,
        onBound = { bound++ },
        onFailed = { received += it },
    )
    future.fail(cause)
    idleMainLooper()

    val forwarded = received.single()
    assertTrue(forwarded is ExecutionException)
    assertSame(cause, (forwarded as ExecutionException).cause)
    assertEquals(0, bound)
    verify(exactly = 0) { provider.bindToLifecycle(any(), any(), any(), any()) }
  }

  @Test
  fun interruptedBindPreservesInterruptFlagAndFails() {
    val future = FakeProviderFuture()
    val provider = mockk<ProcessCameraProvider>(relaxed = true)
    val engine = engine(future)
    val received = mutableListOf<Throwable>()
    val interruptedAtFailure = mutableListOf<Boolean>()
    var bound = 0

    engine.bind(
        context,
        lifecycleOwner,
        previewView,
        onBound = { bound++ },
        onFailed = {
          interruptedAtFailure += Thread.currentThread().isInterrupted
          received += it
        },
    )
    future.interrupt()
    idleMainLooper()
    Thread.interrupted()

    assertTrue(received.single() is InterruptedException)
    assertEquals(listOf(true), interruptedAtFailure)
    assertEquals(0, bound)
    verify(exactly = 0) { provider.bindToLifecycle(any(), any(), any(), any()) }
  }

  private fun engine(future: FakeProviderFuture) = CameraXEngine { future }

  private fun idleMainLooper() {
    shadowOf(Looper.getMainLooper()).idle()
  }

  private class FakeProviderFuture : ListenableFuture<ProcessCameraProvider> {
    private sealed interface State {
      data object Pending : State

      data class Resolved(val provider: ProcessCameraProvider) : State

      data class Failed(val cause: Throwable) : State

      data object Interrupted : State
    }

    private var state: State = State.Pending
    private val listeners = mutableListOf<Pair<Runnable, Executor>>()

    fun complete(provider: ProcessCameraProvider) {
      state = State.Resolved(provider)
      notifyListeners()
    }

    fun fail(cause: Throwable) {
      state = State.Failed(cause)
      notifyListeners()
    }

    fun interrupt() {
      state = State.Interrupted
      notifyListeners()
    }

    private fun notifyListeners() {
      val toNotify = listeners.toList()
      listeners.clear()
      toNotify.forEach { (listener, executor) -> executor.execute(listener) }
    }

    override fun addListener(listener: Runnable, executor: Executor) {
      if (state is State.Pending) {
        listeners += listener to executor
      } else {
        executor.execute(listener)
      }
    }

    override fun get(): ProcessCameraProvider =
        when (val current = state) {
          is State.Resolved -> current.provider
          is State.Failed -> throw ExecutionException(current.cause)
          State.Interrupted -> throw InterruptedException()
          State.Pending -> throw AssertionError("get() called before the future was resolved")
        }

    override fun get(timeout: Long, unit: TimeUnit): ProcessCameraProvider = get()

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false

    override fun isCancelled(): Boolean = false

    override fun isDone(): Boolean = state !is State.Pending
  }

  private class TestLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
      get() = registry
  }
}
