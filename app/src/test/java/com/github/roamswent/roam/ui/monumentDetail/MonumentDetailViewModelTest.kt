// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.monumentDetail

import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MonumentDetailViewModelTest {
  private val repository = mockk<MonumentDetailRepository>()
  private val dispatcher = StandardTestDispatcher()
  private val monument = Monument("id-1", "Monument", "image.jpg", "Description")

  @Before
  fun setMainDispatcher() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun resetMainDispatcher() {
    Dispatchers.resetMain()
  }

  @Test
  fun loadsMonumentFromSavedStateOnCreation() =
      runTest(dispatcher) {
        coEvery { repository.getMonument("id-1") } returns monument
        val viewModel = viewModel()

        advanceUntilIdle()

        assertEquals(MonumentDetailUiState.Success(monument), viewModel.uiState.value)
        coVerify(exactly = 1) { repository.getMonument("id-1") }
      }

  @Test
  fun loadPublishesLoadingThenLoadsAgain() =
      runTest(dispatcher) {
        var calls = 0
        coEvery { repository.getMonument("id-1") } coAnswers
            {
              if (calls++ == 0) throw IllegalStateException("Unavailable")
              monument
            }
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(MonumentDetailUiState.Error("Unavailable"), viewModel.uiState.value)

        viewModel.load()

        assertEquals(MonumentDetailUiState.Loading, viewModel.uiState.value)
        advanceUntilIdle()

        assertEquals(MonumentDetailUiState.Success(monument), viewModel.uiState.value)
        coVerify(exactly = 2) { repository.getMonument("id-1") }
      }

  @Test
  fun errorWithoutMessageUsesFallback() =
      runTest(dispatcher) {
        coEvery { repository.getMonument("id-1") } throws Exception()
        val viewModel = viewModel()

        advanceUntilIdle()

        assertEquals(
            MonumentDetailUiState.Error("Something went wrong"),
            viewModel.uiState.value,
        )
      }

  @Test
  fun missingMonumentIdUsesEmptyString() =
      runTest(dispatcher) {
        coEvery { repository.getMonument("") } returns monument
        val viewModel = MonumentDetailViewModel(SavedStateHandle(), repository)

        advanceUntilIdle()

        assertEquals(MonumentDetailUiState.Success(monument), viewModel.uiState.value)
        coVerify(exactly = 1) { repository.getMonument("") }
      }

  @Test
  fun cancellationDoesNotBecomeAnErrorState() =
      runTest(dispatcher) {
        coEvery { repository.getMonument("id-1") } throws CancellationException("Cancelled")
        val viewModel = viewModel()

        advanceUntilIdle()

        assertEquals(MonumentDetailUiState.Loading, viewModel.uiState.value)
      }

  @Test
  fun backClickEmitsNavigateHome() =
      runTest(dispatcher) {
        coEvery { repository.getMonument("id-1") } returns monument
        val viewModel = viewModel()
        val event = async { viewModel.events.first() }

        viewModel.onBackClick()

        assertEquals(MonumentDetailEvent.NavigateHome, event.await())
      }

  private fun viewModel() =
      MonumentDetailViewModel(SavedStateHandle(mapOf("monumentId" to "id-1")), repository)
}
