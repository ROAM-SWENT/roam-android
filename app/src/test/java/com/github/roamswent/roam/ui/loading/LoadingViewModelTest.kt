// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.ui.loading

import android.net.Uri
import com.github.roamswent.roam.model.ai.DiscoveryRequest
import com.github.roamswent.roam.model.ai.ImagePayload
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.ai.MonumentGuideRepository
import com.github.roamswent.roam.model.ai.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LoadingViewModelTest {
  private val repository = mockk<MonumentGuideRepository>()
  private val dispatcher = StandardTestDispatcher()
  private val uri = Uri.parse("content://capture/photo.jpg")
  private val image = ImagePayload(byteArrayOf(1), "image/jpeg")
  private val request = DiscoveryRequest(image, null, UserPreferences("fr-FR"))
  private var imageReads = 0
  private val readImage: suspend (Uri) -> ImagePayload = {
    check(it == uri)
    imageReads++
    image
  }

  @Before
  fun setMainDispatcher() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun resetMainDispatcher() {
    Dispatchers.resetMain()
  }

  @Test
  fun successfulDiscoveryAutomaticallyNavigatesToDetail() =
      runTest(dispatcher) {
        val guide =
            MonumentGuide(
                monumentName = "The Colosseum",
                confidence = 0.98,
                history = "Built in ancient Rome.",
                facts = listOf("It could hold tens of thousands of spectators."),
                culture = "A symbol of Rome.",
                language = "en",
            )
        coEvery { repository.guideFor(request) } returns guide
        val viewModel = viewModel()
        val event = async { viewModel.navigationEvents.first() }

        advanceUntilIdle()

        assertEquals(
            LoadingNavigationEvent.NavigateToMonumentDetail(
                guide,
                uri.toString(),
            ),
            event.await(),
        )
        assertEquals(1, imageReads)
        coVerify(exactly = 1) { repository.guideFor(request) }
      }

  @Test
  fun discoveryFailureIsExposedAsError() =
      runTest(dispatcher) {
        coEvery { repository.guideFor(request) } throws IllegalStateException("Network unavailable")
        val viewModel = viewModel()
        val event = async { viewModel.navigationEvents.first() }

        advanceUntilIdle()

        assertEquals(LoadingUiState.Error("Network unavailable"), viewModel.uiState.value)
        assertEquals(
            LoadingNavigationEvent.NavigateHomeWithError("Network unavailable"),
            event.await(),
        )
      }

  @Test
  fun imageReadFailureIsExposedAndSkipsRepository() =
      runTest(dispatcher) {
        val viewModel =
            LoadingViewModel(
                repository,
                { throw IllegalStateException("Image unavailable") },
                uri,
                "fr-FR",
            )
        val event = async { viewModel.navigationEvents.first() }

        advanceUntilIdle()

        assertEquals(LoadingUiState.Error("Image unavailable"), viewModel.uiState.value)
        assertEquals(
            LoadingNavigationEvent.NavigateHomeWithError("Image unavailable"),
            event.await(),
        )
        coVerify(exactly = 0) { repository.guideFor(any()) }
      }

  @Test
  fun failureWithoutMessageUsesFallbackCopy() =
      runTest(dispatcher) {
        coEvery { repository.guideFor(request) } throws IllegalStateException()
        val viewModel = viewModel()
        val event = async { viewModel.navigationEvents.first() }

        advanceUntilIdle()

        assertEquals(LoadingUiState.Error("Something went wrong."), viewModel.uiState.value)
        assertEquals(
            LoadingNavigationEvent.NavigateHomeWithError("Something went wrong."),
            event.await(),
        )
      }

  @Test
  fun cancelCancelsDiscoveryAndNavigatesHome() =
      runTest(dispatcher) {
        var discoveryStarted = false
        coEvery { repository.guideFor(request) } coAnswers
            {
              discoveryStarted = true
              kotlinx.coroutines.awaitCancellation()
            }
        val viewModel = viewModel()

        advanceUntilIdle()
        assertTrue(discoveryStarted)

        val event = async { viewModel.navigationEvents.first() }

        viewModel.onCancel()
        advanceUntilIdle()

        assertEquals(LoadingNavigationEvent.NavigateHome, event.await())
        assertEquals(LoadingUiState.Loading, viewModel.uiState.value)
        coVerify(exactly = 1) { repository.guideFor(request) }
      }

  private fun viewModel() = LoadingViewModel(repository, readImage, uri, "fr-FR")
}
