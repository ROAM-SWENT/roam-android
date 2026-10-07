package com.github.roamswent.roam.ui.scan

import android.net.Uri
import com.github.roamswent.roam.repository.CapturedImageManager
import com.github.roamswent.roam.ui.camera.CameraController
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
        val collection = launch { viewModel.navigationEvents.collect { events += it } }

        viewModel.onCapture()

        assertEquals(ScanUiState.Capturing, viewModel.state.value)
        coVerify(exactly = 0) { capturedImageManager.newCaptureUri() }
        advanceUntilIdle()

        coVerify(exactly = 1) { capturedImageManager.newCaptureUri() }
        verify(exactly = 1) { cameraController.captureTo(uri, any(), any()) }
        onSaved.invoke()
        advanceUntilIdle()

        assertEquals(listOf(ScanNavigationEvent.NavigateToReview(uri)), events)
        assertEquals(ScanUiState.Capturing, viewModel.state.value)
        collection.cancel()
      }

  @Test
  fun captureErrorReturnsToStreamingWithoutNavigationOrDeletion() =
      runTest(dispatcher) {
        lateinit var onError: (Throwable) -> Unit
        coEvery { capturedImageManager.newCaptureUri() } returns
            Uri.Builder().scheme("content").authority("example").path("capture.jpg").build()
        every { cameraController.captureTo(any(), any(), any()) } answers { onError = thirdArg() }
        val viewModel = viewModel()
        val events = mutableListOf<ScanNavigationEvent>()
        val collection = launch { viewModel.navigationEvents.collect { events += it } }

        viewModel.onCapture()
        advanceUntilIdle()
        onError.invoke(IllegalStateException("capture failed"))
        advanceUntilIdle()

        assertEquals(ScanUiState.Streaming, viewModel.state.value)
        assertTrue(events.isEmpty())
        coVerify(exactly = 0) { capturedImageManager.deleteCapture(any()) }
        collection.cancel()
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
