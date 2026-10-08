package com.github.roamswent.roam.ui.scan

import android.net.Uri
import com.github.roamswent.roam.repository.CaptureSender
import com.github.roamswent.roam.repository.CapturedImageManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class ScanReviewViewModelTest {
  private val capturedImageManager = mockk<CapturedImageManager>()
  private val captureSender = mockk<CaptureSender>()
  private val dispatcher = StandardTestDispatcher()
  private val uri = Uri.parse("content://example/capture.jpg")

  @Before
  fun setMainDispatcher() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun resetMainDispatcher() {
    Dispatchers.resetMain()
  }

  @Test
  fun setUriStartsReviewingWithoutNavigation() =
      runTest(dispatcher) {
        val viewModel = viewModel()
        val events = collectEvents(viewModel)

        viewModel.setUri(uri)
        advanceUntilIdle()

        assertEquals(ScanReviewUiState.Reviewing(uri), viewModel.state.value)
        assertTrue(events.isEmpty())
      }

  @Test
  fun cancelRetakeReturnsToReviewingWithoutDeleting() =
      runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.setUri(uri)
        viewModel.onRetake()

        assertEquals(ScanReviewUiState.ConfirmRetake(uri), viewModel.state.value)
        viewModel.cancelRetake()

        assertEquals(ScanReviewUiState.Reviewing(uri), viewModel.state.value)
        coVerify(exactly = 0) { capturedImageManager.deleteCapture(any()) }
      }

  @Test
  fun confirmRetakeDeletesAndNavigatesToCapture() =
      runTest(dispatcher) {
        val deleteStarted = CompletableDeferred<Unit>()
        val allowDelete = CompletableDeferred<Boolean>()
        coEvery { capturedImageManager.deleteCapture(uri) } coAnswers
            {
              deleteStarted.complete(Unit)
              allowDelete.await()
            }
        val viewModel = viewModel()
        val events = collectEvents(viewModel)
        viewModel.setUri(uri)
        viewModel.onRetake()

        viewModel.confirmRetake()
        assertEquals(ScanReviewUiState.Deleting(uri), viewModel.state.value)
        runCurrent()
        assertTrue(deleteStarted.isCompleted)
        assertEquals(ScanReviewUiState.Deleting(uri), viewModel.state.value)
        assertTrue(events.isEmpty())

        allowDelete.complete(true)
        advanceUntilIdle()

        coVerify(exactly = 1) { capturedImageManager.deleteCapture(uri) }
        assertEquals(listOf(ScanReviewNavigationEvent.BackToCapture), events)
      }

  @Test
  fun retakeWithFailedDeletionStaysOnReview() =
      runTest(dispatcher) {
        coEvery { capturedImageManager.deleteCapture(uri) } returns false
        val viewModel = viewModel()
        val events = collectEvents(viewModel)
        viewModel.setUri(uri)
        viewModel.onRetake()

        viewModel.confirmRetake()
        advanceUntilIdle()

        coVerify(exactly = 1) { capturedImageManager.deleteCapture(uri) }
        assertEquals(ScanReviewUiState.DeleteFailed(uri), viewModel.state.value)
        assertTrue(events.isEmpty())
      }

  @Test
  fun retakeWithThrowingDeletionStaysOnReview() =
      runTest(dispatcher) {
        coEvery { capturedImageManager.deleteCapture(uri) } throws
            IllegalStateException("delete failed")
        val viewModel = viewModel()
        val events = collectEvents(viewModel)
        viewModel.setUri(uri)
        viewModel.onRetake()

        viewModel.confirmRetake()
        advanceUntilIdle()

        coVerify(exactly = 1) { capturedImageManager.deleteCapture(uri) }
        assertEquals(ScanReviewUiState.DeleteFailed(uri), viewModel.state.value)
        assertTrue(events.isEmpty())
      }

  @Test
  fun deleteErrorRetrySucceedsAndNavigates() =
      runTest(dispatcher) {
        coEvery { capturedImageManager.deleteCapture(uri) } returnsMany listOf(false, true)
        val viewModel = viewModel()
        val events = collectEvents(viewModel)
        viewModel.setUri(uri)
        viewModel.onRetake()

        viewModel.confirmRetake()
        advanceUntilIdle()
        assertEquals(ScanReviewUiState.DeleteFailed(uri), viewModel.state.value)

        viewModel.onRetryDelete()
        assertEquals(ScanReviewUiState.Deleting(uri), viewModel.state.value)
        advanceUntilIdle()

        coVerify(exactly = 2) { capturedImageManager.deleteCapture(uri) }
        assertEquals(listOf(ScanReviewNavigationEvent.BackToCapture), events)
      }

  @Test
  fun deleteErrorDismissReturnsToReviewing() =
      runTest(dispatcher) {
        coEvery { capturedImageManager.deleteCapture(uri) } returns false
        val viewModel = viewModel()
        val events = collectEvents(viewModel)
        viewModel.setUri(uri)
        viewModel.onRetake()

        viewModel.confirmRetake()
        advanceUntilIdle()
        assertEquals(ScanReviewUiState.DeleteFailed(uri), viewModel.state.value)

        viewModel.onDismissDeleteError()
        advanceUntilIdle()

        assertEquals(ScanReviewUiState.Reviewing(uri), viewModel.state.value)
        assertTrue(events.isEmpty())
      }

  @Test
  fun sendSuccessLocksInSendingState() =
      runTest(dispatcher) {
        coEvery { captureSender.send(uri) } returns Result.success(Unit)
        val viewModel = viewModel()
        val events = collectEvents(viewModel)
        viewModel.setUri(uri)

        viewModel.onSend()
        assertEquals(ScanReviewUiState.Sending(uri), viewModel.state.value)
        coVerify(exactly = 0) { captureSender.send(any()) }

        advanceUntilIdle()

        coVerify(exactly = 1) { captureSender.send(uri) }
        assertEquals(ScanReviewUiState.Sending(uri), viewModel.state.value)
        assertTrue(events.isEmpty())
      }

  @Test
  fun sendingLocksAllActionsUntilSenderCompletes() =
      runTest(dispatcher) {
        val sendStarted = CompletableDeferred<Unit>()
        val allowSend = CompletableDeferred<Result<Unit>>()
        coEvery { captureSender.send(uri) } coAnswers
            {
              sendStarted.complete(Unit)
              allowSend.await()
            }
        val viewModel = viewModel()
        val events = collectEvents(viewModel)
        viewModel.setUri(uri)

        viewModel.onSend()
        advanceUntilIdle()
        assertEquals(ScanReviewUiState.Sending(uri), viewModel.state.value)
        assertTrue(sendStarted.isCompleted)

        viewModel.onSend()
        viewModel.onRetake()
        viewModel.confirmRetake()
        advanceUntilIdle()

        coVerify(exactly = 1) { captureSender.send(uri) }
        coVerify(exactly = 0) { capturedImageManager.deleteCapture(any()) }
        assertEquals(ScanReviewUiState.Sending(uri), viewModel.state.value)
        assertTrue(events.isEmpty())

        allowSend.complete(Result.success(Unit))
        advanceUntilIdle()

        assertEquals(ScanReviewUiState.Sending(uri), viewModel.state.value)
        assertTrue(events.isEmpty())
        viewModel.onSend()
        viewModel.onRetake()
        viewModel.confirmRetake()
        advanceUntilIdle()
        coVerify(exactly = 1) { captureSender.send(uri) }
        coVerify(exactly = 0) { capturedImageManager.deleteCapture(any()) }
        assertEquals(ScanReviewUiState.Sending(uri), viewModel.state.value)
        assertTrue(events.isEmpty())
      }

  @Test
  fun sendFailureReturnsToReviewingWithoutNavigation() =
      runTest(dispatcher) {
        coEvery { captureSender.send(uri) } returns
            Result.failure(IllegalStateException("send failed"))
        val viewModel = viewModel()
        val events = collectEvents(viewModel)
        viewModel.setUri(uri)

        viewModel.onSend()
        advanceUntilIdle()

        coVerify(exactly = 1) { captureSender.send(uri) }
        assertEquals(ScanReviewUiState.Reviewing(uri), viewModel.state.value)
        assertTrue(events.isEmpty())
      }

  @Test
  fun sendExceptionRestoresReviewingWithoutNavigation() =
      runTest(dispatcher) {
        coEvery { captureSender.send(uri) } throws IllegalStateException("sender crashed")
        val viewModel = viewModel()
        val events = collectEvents(viewModel)
        viewModel.setUri(uri)

        viewModel.onSend()
        advanceUntilIdle()

        coVerify(exactly = 1) { captureSender.send(uri) }
        assertEquals(ScanReviewUiState.Reviewing(uri), viewModel.state.value)
        assertTrue(events.isEmpty())
        coEvery { captureSender.send(uri) } returns Result.success(Unit)
        viewModel.onSend()
        advanceUntilIdle()

        assertEquals(ScanReviewUiState.Sending(uri), viewModel.state.value)
      }

  @Test
  fun setUriIsIgnoredWhenStateIsAlreadySet() =
      runTest(dispatcher) {
        val otherUri = Uri.parse("content://example/other.jpg")
        val viewModel = viewModel()
        val events = collectEvents(viewModel)
        viewModel.setUri(uri)

        viewModel.setUri(otherUri)
        advanceUntilIdle()

        assertEquals(ScanReviewUiState.Reviewing(uri), viewModel.state.value)
        assertTrue(events.isEmpty())
      }

  @Test
  fun cancelRetakeIsIgnoredWhenNotConfirmingRetake() =
      runTest(dispatcher) {
        val viewModel = viewModel()
        val events = collectEvents(viewModel)
        viewModel.setUri(uri)

        viewModel.cancelRetake()
        advanceUntilIdle()

        assertEquals(ScanReviewUiState.Reviewing(uri), viewModel.state.value)
        assertTrue(events.isEmpty())
      }

  @Test
  fun confirmRetakeIsIdempotent() =
      runTest(dispatcher) {
        coEvery { capturedImageManager.deleteCapture(uri) } returns true
        val viewModel = viewModel()
        val events = collectEvents(viewModel)
        viewModel.setUri(uri)
        viewModel.onRetake()

        viewModel.confirmRetake()
        advanceUntilIdle()
        viewModel.confirmRetake()
        advanceUntilIdle()

        coVerify(exactly = 1) { capturedImageManager.deleteCapture(uri) }
        assertEquals(listOf(ScanReviewNavigationEvent.BackToCapture), events)
      }

  private fun viewModel() = ScanReviewViewModel(capturedImageManager, captureSender, dispatcher)

  private fun TestScope.collectEvents(
      viewModel: ScanReviewViewModel
  ): MutableList<ScanReviewNavigationEvent> {
    val events = mutableListOf<ScanReviewNavigationEvent>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      viewModel.navigationEvents.collect { events += it }
    }
    return events
  }
}
