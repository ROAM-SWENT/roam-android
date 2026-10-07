package com.github.roamswent.roam.ui.scan

import android.net.Uri
import com.github.roamswent.roam.repository.CapturedImageManager
import com.github.roamswent.roam.ui.camera.CameraController
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ScanViewModelTest {
  private val capturedImageManager = mockk<CapturedImageManager>()
  private val cameraController = mockk<CameraController>(relaxed = true)
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setMainDispatcher() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun resetMainDispatcher() {
    Dispatchers.resetMain()
  }

  @Test
  fun initialStateIsStreamingWithoutNavigationEvents() =
      runTest(dispatcher) {
        val viewModel = viewModel()
        val events = mutableListOf<ScanNavigationEvent>()
        val collection = launch { viewModel.navigationEvents.collect { events += it } }

        advanceUntilIdle()

        assertEquals(ScanUiState.Streaming, viewModel.state.value)
        assertTrue(events.isEmpty())
        collection.cancel()
      }

  @Test
  fun captureNavigatesToReviewAfterImageIsSaved() =
      runTest(dispatcher) {
        val uri = Uri.Builder().scheme("content").authority("example").path("capture.jpg").build()
        lateinit var onSaved: () -> Unit
        coEvery { capturedImageManager.newCaptureUri() } returns uri
        every { cameraController.captureTo(any(), any(), any()) } answers { onSaved = secondArg() }
        val viewModel = viewModel()
        val events = mutableListOf<ScanNavigationEvent>()
        val ordering = mutableListOf<String>()
        val stateCollection = launch {
          viewModel.state.collect { state ->
            if (state == ScanUiState.Captured) ordering += "captured"
          }
        }
        val eventCollection = launch {
          viewModel.navigationEvents.collect {
            ordering += "navigate"
            events += it
          }
        }

        viewModel.onCapture()

        assertEquals(ScanUiState.Capturing, viewModel.state.value)
        coVerify(exactly = 0) { capturedImageManager.newCaptureUri() }
        advanceUntilIdle()

        coVerify(exactly = 1) { capturedImageManager.newCaptureUri() }
        verify(exactly = 1) { cameraController.captureTo(uri, any(), any()) }
        onSaved.invoke()
        advanceUntilIdle()

        assertEquals(listOf(ScanNavigationEvent.NavigateToReview(uri)), events)
        assertEquals(ScanUiState.Captured, viewModel.state.value)
        assertEquals(listOf("captured", "navigate"), ordering)
        stateCollection.cancel()
        eventCollection.cancel()
      }

  @Test
  fun captureErrorReturnsToStreamingWithoutNavigationOrDeletion() =
      runTest(dispatcher) {
        val callbacks = mutableListOf<(Throwable) -> Unit>()
        coEvery { capturedImageManager.newCaptureUri() } returns
            Uri.Builder().scheme("content").authority("example").path("capture.jpg").build()
        every { cameraController.captureTo(any(), any(), any()) } answers
            {
              callbacks += thirdArg<(Throwable) -> Unit>()
            }
        val viewModel = viewModel()
        val events = mutableListOf<ScanNavigationEvent>()
        val collection = launch { viewModel.navigationEvents.collect { events += it } }

        viewModel.onCapture()
        advanceUntilIdle()
        callbacks.single().invoke(IllegalStateException("capture failed"))
        advanceUntilIdle()

        assertEquals(ScanUiState.Streaming, viewModel.state.value)
        assertTrue(events.isEmpty())
        coVerify(exactly = 0) { capturedImageManager.deleteCapture(any()) }
        viewModel.onCapture()
        advanceUntilIdle()
        coVerify(exactly = 2) { capturedImageManager.newCaptureUri() }
        collection.cancel()
      }

  @Test
  fun storageFailureRestoresStreamingWithoutNavigation() =
      runTest(dispatcher) {
        val uri = Uri.Builder().scheme("content").authority("example").path("capture.jpg").build()
        coEvery { capturedImageManager.newCaptureUri() } throws IOException("storage full")
        val viewModel = viewModel()
        val events = mutableListOf<ScanNavigationEvent>()
        val collection = launch { viewModel.navigationEvents.collect { events += it } }

        viewModel.onCapture()
        advanceUntilIdle()

        assertEquals(ScanUiState.Streaming, viewModel.state.value)
        assertTrue(events.isEmpty())
        verify(exactly = 0) { cameraController.captureTo(any(), any(), any()) }
        coEvery { capturedImageManager.newCaptureUri() } returns uri
        viewModel.onCapture()
        advanceUntilIdle()

        verify(exactly = 1) { cameraController.captureTo(uri, any(), any()) }
        collection.cancel()
      }

  @Test
  fun captureAfterSavedIsIgnoredAndDoesNotNavigateAgain() =
      runTest(dispatcher) {
        val uri = Uri.Builder().scheme("content").authority("example").path("capture.jpg").build()
        lateinit var onSaved: () -> Unit
        coEvery { capturedImageManager.newCaptureUri() } returns uri
        every { cameraController.captureTo(any(), any(), any()) } answers { onSaved = secondArg() }
        val viewModel = viewModel()
        val events = mutableListOf<ScanNavigationEvent>()
        val collection = launch { viewModel.navigationEvents.collect { events += it } }

        viewModel.onCapture()
        advanceUntilIdle()
        onSaved.invoke()
        advanceUntilIdle()
        viewModel.onCapture()
        advanceUntilIdle()

        assertEquals(ScanUiState.Captured, viewModel.state.value)
        coVerify(exactly = 1) { capturedImageManager.newCaptureUri() }
        assertEquals(listOf(ScanNavigationEvent.NavigateToReview(uri)), events)
        collection.cancel()
      }

  @Test
  fun cameraActiveRearmsCapturedStateForSecondCapture() =
      runTest(dispatcher) {
        val uris =
            listOf(
                Uri.parse("content://example/first.jpg"),
                Uri.parse("content://example/second.jpg"),
            )
        val savedCallbacks = mutableListOf<() -> Unit>()
        coEvery { capturedImageManager.newCaptureUri() } returnsMany uris
        every { cameraController.captureTo(any(), any(), any()) } answers
            {
              savedCallbacks += secondArg<() -> Unit>()
            }
        val viewModel = viewModel()

        viewModel.onCapture()
        advanceUntilIdle()
        savedCallbacks[0].invoke()
        advanceUntilIdle()
        assertEquals(ScanUiState.Captured, viewModel.state.value)

        viewModel.onCameraActive(cameraController)
        assertEquals(ScanUiState.Streaming, viewModel.state.value)
        viewModel.onCapture()
        advanceUntilIdle()

        coVerify(exactly = 2) { capturedImageManager.newCaptureUri() }
        verify(exactly = 2) { cameraController.captureTo(any(), any(), any()) }
      }

  @Test
  fun cameraActiveDoesNothingWhileStreaming() =
      runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onCameraActive(cameraController)

        assertEquals(ScanUiState.Streaming, viewModel.state.value)
      }

  @Test
  fun cameraActiveDoesNothingWhileCapturing() =
      runTest(dispatcher) {
        every { cameraController.captureTo(any(), any(), any()) } answers {}
        val viewModel = viewModel()

        viewModel.onCapture()
        viewModel.onCameraActive(cameraController)

        assertEquals(ScanUiState.Capturing, viewModel.state.value)
      }

  @Test
  fun cameraActiveDoesNothingWhileRevoked() =
      runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onPermissionRevoked()

        viewModel.onCameraActive(cameraController)

        assertEquals(ScanUiState.Revoked, viewModel.state.value)
      }

  @Test
  fun captureAfterControllerRebindRoutesThroughNewController() =
      runTest(dispatcher) {
        val controllerA = mockk<CameraController>(relaxed = true)
        val controllerB = mockk<CameraController>(relaxed = true)
        val firstUri = Uri.parse("content://example/first.jpg")
        val secondUri = Uri.parse("content://example/second.jpg")
        val callbacksA = mutableListOf<() -> Unit>()
        val callbacksB = mutableListOf<() -> Unit>()
        coEvery { capturedImageManager.newCaptureUri() } returnsMany listOf(firstUri, secondUri)
        every { controllerA.captureTo(any(), any(), any()) } answers
            {
              callbacksA += secondArg<() -> Unit>()
            }
        every { controllerB.captureTo(any(), any(), any()) } answers
            {
              callbacksB += secondArg<() -> Unit>()
            }
        val viewModel = ScanViewModel(capturedImageManager, controllerA, dispatcher)
        val events = mutableListOf<ScanNavigationEvent>()
        val collection = launch { viewModel.navigationEvents.collect { events += it } }

        viewModel.onCapture()
        advanceUntilIdle()
        callbacksA.single().invoke()
        advanceUntilIdle()
        viewModel.onCameraActive(controllerB)
        viewModel.onCapture()
        advanceUntilIdle()

        verify(exactly = 1) { controllerA.captureTo(firstUri, any(), any()) }
        verify(exactly = 1) { controllerB.captureTo(secondUri, any(), any()) }
        callbacksB.single().invoke()
        advanceUntilIdle()

        assertEquals(
            listOf(
                ScanNavigationEvent.NavigateToReview(firstUri),
                ScanNavigationEvent.NavigateToReview(secondUri),
            ),
            events,
        )
        collection.cancel()
      }

  @Test
  fun navigationEventIsBufferedForReenteringCollector() =
      runTest(dispatcher) {
        val uri = Uri.parse("content://example/capture.jpg")
        lateinit var onSaved: () -> Unit
        coEvery { capturedImageManager.newCaptureUri() } returns uri
        every { cameraController.captureTo(any(), any(), any()) } answers { onSaved = secondArg() }
        val viewModel = viewModel()

        viewModel.onCapture()
        advanceUntilIdle()
        onSaved.invoke()
        advanceUntilIdle()

        val events = mutableListOf<ScanNavigationEvent>()
        val collection = launch { viewModel.navigationEvents.collect { events += it } }
        advanceUntilIdle()

        assertEquals(listOf(ScanNavigationEvent.NavigateToReview(uri)), events)
        collection.cancel()
      }

  @Test
  fun captureLoopTwiceDeliversTwoEvents() =
      runTest(dispatcher) {
        val uris =
            listOf(
                Uri.parse("content://example/first.jpg"),
                Uri.parse("content://example/second.jpg"),
            )
        val callbacks = mutableListOf<() -> Unit>()
        coEvery { capturedImageManager.newCaptureUri() } returnsMany uris
        every { cameraController.captureTo(any(), any(), any()) } answers
            {
              callbacks += secondArg<() -> Unit>()
            }
        val viewModel = viewModel()
        val events = mutableListOf<ScanNavigationEvent>()
        val collection = launch { viewModel.navigationEvents.collect { events += it } }

        repeat(2) {
          viewModel.onCapture()
          advanceUntilIdle()
          callbacks[it].invoke()
          advanceUntilIdle()
          if (it == 0) viewModel.onCameraActive(cameraController)
        }

        assertEquals(
            listOf(
                ScanNavigationEvent.NavigateToReview(uris[0]),
                ScanNavigationEvent.NavigateToReview(uris[1]),
            ),
            events,
        )
        coVerify(exactly = 2) { capturedImageManager.newCaptureUri() }
        collection.cancel()
      }

  @Test
  fun captureWhileRevokedIsIgnored() =
      runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onPermissionRevoked()
        viewModel.onCapture()
        advanceUntilIdle()

        assertEquals(ScanUiState.Revoked, viewModel.state.value)
        coVerify(exactly = 0) { capturedImageManager.newCaptureUri() }
      }

  @Test
  fun permissionRevocationStopsCameraBeforeStateBecomesRevoked() =
      runTest(dispatcher) {
        val ordering = mutableListOf<String>()
        every { cameraController.stop() } answers { ordering += "stop" }
        val viewModel = viewModel()
        val collection = launch {
          viewModel.state.collect { state ->
            if (state == ScanUiState.Revoked) ordering += "revoked"
          }
        }
        advanceUntilIdle()
        ordering.clear()

        viewModel.onPermissionRevoked()
        advanceUntilIdle()

        assertEquals(listOf("stop", "revoked"), ordering)
        collection.cancel()
      }

  @Test
  fun permissionRevocationIsIdempotentForCameraStop() =
      runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onPermissionRevoked()
        viewModel.onPermissionRevoked()

        verify(exactly = 1) { cameraController.stop() }
        assertEquals(ScanUiState.Revoked, viewModel.state.value)
      }

  @Test
  fun captureWorkWaitsForInjectedDispatcher() =
      runTest(dispatcher) {
        val uri = Uri.Builder().scheme("content").authority("example").path("capture.jpg").build()
        coEvery { capturedImageManager.newCaptureUri() } returns uri
        every { cameraController.captureTo(any(), any(), any()) } answers {}
        val viewModel = viewModel()

        viewModel.onCapture()

        coVerify(exactly = 0) { capturedImageManager.newCaptureUri() }
        verify(exactly = 0) { cameraController.captureTo(any(), any(), any()) }
        advanceUntilIdle()
        coVerify(exactly = 1) { capturedImageManager.newCaptureUri() }
        verify(exactly = 1) { cameraController.captureTo(uri, any(), any()) }
      }

  @Test
  fun rapidCaptureOnlyCreatesOneCapture() =
      runTest(dispatcher) {
        val uri = Uri.Builder().scheme("content").authority("example").path("capture.jpg").build()
        coEvery { capturedImageManager.newCaptureUri() } returns uri
        every { cameraController.captureTo(any(), any(), any()) } answers {}
        val viewModel = viewModel()

        viewModel.onCapture()
        viewModel.onCapture()
        advanceUntilIdle()

        coVerify(exactly = 1) { capturedImageManager.newCaptureUri() }
      }

  private fun viewModel() = ScanViewModel(capturedImageManager, cameraController, dispatcher)
}
