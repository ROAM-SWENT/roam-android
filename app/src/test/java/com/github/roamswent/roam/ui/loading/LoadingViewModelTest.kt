// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.ui.loading

import android.net.Uri
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.ai.BlockedException
import com.github.roamswent.roam.model.ai.ConfigurationException
import com.github.roamswent.roam.model.ai.DiscoveryRequest
import com.github.roamswent.roam.model.ai.ImagePayload
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.ai.MonumentGuideRepository
import com.github.roamswent.roam.model.ai.MonumentNotIdentifiedException
import com.github.roamswent.roam.model.ai.NetworkUnavailableException
import com.github.roamswent.roam.model.ai.ParseException
import com.github.roamswent.roam.model.ai.RejectedImageException
import com.github.roamswent.roam.model.ai.RejectionReason
import com.github.roamswent.roam.model.ai.TransientException
import com.github.roamswent.roam.model.ai.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
        val guide = guide()
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
  fun cancelAfterSuccessEmitsOnlyDetailNavigation() =
      runTest(dispatcher) {
        val guide = guide()
        coEvery { repository.guideFor(request) } returns guide
        val viewModel = viewModel()
        val events = mutableListOf<LoadingNavigationEvent>()
        val collector =
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
              viewModel.navigationEvents.collect(events::add)
            }

        advanceUntilIdle()
        viewModel.onCancel()
        advanceUntilIdle()

        assertEquals(
            listOf(LoadingNavigationEvent.NavigateToMonumentDetail(guide, uri.toString())),
            events,
        )
        collector.cancel()
      }

  @Test
  fun discoveryFailureIsExposedAsError() =
      runTest(dispatcher) {
        coEvery { repository.guideFor(request) } throws IllegalStateException("Network unavailable")
        val viewModel = viewModel()

        advanceUntilIdle()

        assertEquals(LoadingUiState.Error(R.string.loading_error_generic), viewModel.uiState.value)
      }

  @Test
  fun imageReadFailureIsExposedAndSkipsRepository() =
      runTest(dispatcher) {
        val viewModel =
            LoadingViewModel(
                repository,
                { throw IOException("internal read failure") },
                uri,
                "fr-FR",
            )
        viewModel.startDiscovery()

        advanceUntilIdle()

        assertEquals(
            LoadingUiState.Error(R.string.loading_error_read_image),
            viewModel.uiState.value,
        )
        coVerify(exactly = 0) { repository.guideFor(any()) }
      }

  @Test
  fun knownDiscoveryFailuresUseUserFriendlyMessages() =
      runTest(dispatcher) {
        val failures =
            listOf(
                NetworkUnavailableException() to R.string.loading_error_network,
                RejectedImageException(RejectionReason.Oversized) to
                    R.string.loading_error_rejected_image,
                MonumentNotIdentifiedException() to R.string.loading_error_not_identified,
                BlockedException("internal detail") to R.string.loading_error_blocked,
                ParseException() to R.string.loading_error_parse,
                TransientException("internal detail") to R.string.loading_error_transient,
                ConfigurationException("internal detail") to R.string.loading_error_configuration,
            )

        failures.forEach { (exception, expectedMessage) ->
          coEvery { repository.guideFor(request) } throws exception
          val viewModel = viewModel()

          advanceUntilIdle()

          assertEquals(LoadingUiState.Error(expectedMessage), viewModel.uiState.value)
        }
      }

  @Test
  fun failureWithoutMessageUsesFallbackCopy() =
      runTest(dispatcher) {
        coEvery { repository.guideFor(request) } throws IllegalStateException()
        val viewModel = viewModel()

        advanceUntilIdle()

        assertEquals(LoadingUiState.Error(R.string.loading_error_generic), viewModel.uiState.value)
      }

  @Test
  fun discoveryTimeoutIsExposedAsError() =
      runTest(dispatcher) {
        coEvery { repository.guideFor(request) } coAnswers
            {
              kotlinx.coroutines.awaitCancellation()
            }
        val viewModel = viewModel()

        advanceUntilIdle()

        assertEquals(
            LoadingUiState.Error(R.string.loading_error_timeout),
            viewModel.uiState.value,
        )
        coVerify(exactly = 1) { repository.guideFor(request) }
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

        runCurrent()
        assertTrue(discoveryStarted)

        val event = async { viewModel.navigationEvents.first() }

        viewModel.onCancel()
        advanceUntilIdle()

        assertEquals(LoadingNavigationEvent.NavigateHome, event.await())
        assertEquals(LoadingUiState.Loading, viewModel.uiState.value)
        coVerify(exactly = 1) { repository.guideFor(request) }
      }

  private fun viewModel() =
      LoadingViewModel(repository, readImage, uri, "fr-FR").also { it.startDiscovery() }

  @Test
  fun loadingAndDiscoveredGuideAreExposedAsState() =
      runTest(dispatcher) {
        val viewModel = LoadingViewModel(repository, readImage, uri, "fr-FR")
        val guide = guide()

        assertEquals(false, viewModel.showLoading.value)
        assertEquals(null, viewModel.discoveredGuide.value)

        viewModel.beginLoading()
        assertEquals(true, viewModel.showLoading.value)

        viewModel.onDiscoverySuccess(guide)
        assertEquals(false, viewModel.showLoading.value)
        assertEquals(guide, viewModel.discoveredGuide.value)
      }

  private fun guide() =
      MonumentGuide(
          monumentName = "The Colosseum",
          confidence = 0.98,
          history = "Built in ancient Rome.",
          facts = listOf("It could hold tens of thousands of spectators."),
          culture = "A symbol of Rome.",
          language = "en",
      )
}
