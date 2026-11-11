// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

package com.github.roamswent.roam.ui.loading

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
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
import com.github.roamswent.roam.model.guide.FakeSavedGuideRepository
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.guide.SavedGuideRepository
import com.github.roamswent.roam.model.interests.UserInterestsRepository
import com.github.roamswent.roam.model.interests.UserInterestsState
import com.github.roamswent.roam.model.location.Coordinates
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LoadingViewModelTest {
  private val repository = mockk<MonumentGuideRepository>()
  private val userInterestsRepository = FakeUserInterestsRepository()
  private val dispatcher = StandardTestDispatcher()
  private val uri = Uri.parse("content://capture/photo.jpg")
  private val image = ImagePayload(byteArrayOf(1), "image/jpeg")
  private val request = DiscoveryRequest(image, null, UserPreferences("fr-FR"))
  private var imageReads = 0
  private val savedGuides = FakeSavedGuideRepository()
  private val preparedPhoto = ImagePayload(byteArrayOf(9, 8, 7), "image/jpeg")
  private val discoveryDate = 1_700_000_000_000L
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
                userInterestsRepository,
                savedGuides,
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

  @Test
  fun discoveryCanBeRetriedAfterCancellationAndFailure() =
      runTest(dispatcher) {
        var attempts = 0
        val guide = guide()
        coEvery { repository.guideFor(request) } coAnswers
            {
              when (++attempts) {
                1 -> kotlinx.coroutines.awaitCancellation()
                2 -> throw IllegalStateException("Temporary failure")
                else -> guide
              }
            }
        val viewModel = newViewModel(userInterestsRepository)
        val events = mutableListOf<LoadingNavigationEvent>()
        val collector =
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
              viewModel.navigationEvents.collect(events::add)
            }

        viewModel.startDiscovery()
        runCurrent()
        viewModel.onCancel()
        advanceUntilIdle()
        assertEquals(listOf(LoadingNavigationEvent.NavigateHome), events)

        viewModel.startDiscovery()
        advanceUntilIdle()
        assertEquals(LoadingUiState.Error(R.string.loading_error_generic), viewModel.uiState.value)

        viewModel.startDiscovery()
        advanceUntilIdle()

        assertEquals(
            listOf(
                LoadingNavigationEvent.NavigateHome,
                LoadingNavigationEvent.NavigateToMonumentDetail(guide, uri.toString()),
            ),
            events,
        )
        assertEquals(3, attempts)
        collector.cancel()
      }

  private fun viewModel() = newViewModel(userInterestsRepository).also { it.startDiscovery() }

  @Test
  fun loadingAndDiscoveredGuideAreExposedAsState() =
      runTest(dispatcher) {
        val viewModel = newViewModel(userInterestsRepository)
        val guide = guide()

        assertEquals(false, viewModel.showLoading.value)
        assertEquals(null, viewModel.discoveredGuide.value)

        viewModel.beginLoading()
        assertEquals(true, viewModel.showLoading.value)

        viewModel.onDiscoverySuccess(guide)
        assertEquals(false, viewModel.showLoading.value)
        assertEquals(guide, viewModel.discoveredGuide.value)
      }

  @Test
  fun alreadyAskedUserBeginsLoadingImmediately() =
      runTest(dispatcher) {
        val viewModel = newViewModel(FakeUserInterestsRepository(hasBeenAsked = true))

        viewModel.beginLoading()

        assertEquals(true, viewModel.showLoading.value)
        assertEquals(false, viewModel.showInterestsPrompt.value)
      }

  @Test
  fun neverAskedUserSeesPromptWithoutBeginningLoading() =
      runTest(dispatcher) {
        val viewModel = newViewModel(FakeUserInterestsRepository(hasBeenAsked = false))

        viewModel.beginLoading()

        assertEquals(false, viewModel.showLoading.value)
        assertEquals(true, viewModel.showInterestsPrompt.value)
        coVerify(exactly = 0) { repository.guideFor(any()) }
      }

  @Test
  fun savingInterestsPersistsAndBeginsLoading() =
      runTest(dispatcher) {
        val interests = FakeUserInterestsRepository(hasBeenAsked = false)
        val viewModel = newViewModel(interests)
        viewModel.beginLoading()

        viewModel.onSaveInterests(listOf("art", "history"))

        assertEquals(
            UserInterestsState(hasBeenAsked = true, interests = listOf("art", "history")),
            interests.getUserInterests(),
        )
        assertEquals(false, viewModel.showInterestsPrompt.value)
        assertEquals(true, viewModel.showLoading.value)
      }

  @Test
  fun skippingInterestsPersistsAndBeginsLoading() =
      runTest(dispatcher) {
        val interests = FakeUserInterestsRepository(hasBeenAsked = false)
        val viewModel = newViewModel(interests)
        viewModel.beginLoading()

        viewModel.onSkipInterests()

        assertEquals(
            UserInterestsState(hasBeenAsked = true, interests = emptyList()),
            interests.getUserInterests(),
        )
        assertEquals(false, viewModel.showInterestsPrompt.value)
        assertEquals(true, viewModel.showLoading.value)
      }

  @Test
  fun laterSendDoesNotPromptAgainAfterInterestsDecision() =
      runTest(dispatcher) {
        val interests = FakeUserInterestsRepository(hasBeenAsked = false)
        val firstViewModel = newViewModel(interests)
        firstViewModel.beginLoading()
        firstViewModel.onSkipInterests()

        val nextViewModel = newViewModel(interests)
        nextViewModel.beginLoading()

        assertEquals(false, nextViewModel.showInterestsPrompt.value)
        assertEquals(true, nextViewModel.showLoading.value)
      }

  private fun newViewModel(interestsRepository: UserInterestsRepository) =
      LoadingViewModel(
          repository,
          readImage,
          uri,
          "fr-FR",
          interestsRepository,
          savedGuides,
          clock = { discoveryDate },
          newGuideId = { GUIDE_ID },
      )

  @Test
  fun successfulDiscoveryIsSavedToCollection() =
      runTest(dispatcher) {
        coEvery { repository.guideFor(request) } returns guide().copy(location = scanLocation)
        val viewModel = viewModel()
        val event = async { viewModel.navigationEvents.first() }

        advanceUntilIdle()

        val saved = savedGuides.guides.value.single()
        assertEquals(GUIDE_ID, saved.id)
        assertEquals("The Colosseum", saved.monumentName)
        assertEquals(0.98, saved.confidence, 0.0)
        assertEquals("Built in ancient Rome.", saved.history)
        assertEquals(listOf("It could hold tens of thousands of spectators."), saved.facts)
        assertEquals("A symbol of Rome.", saved.culture)
        // The guide is not on Explore until the user opts in.
        assertNull(saved.location)
        assertEquals(discoveryDate, saved.discoveryDate)
        assertEquals(false, saved.isFavorite)
        // The photo the pipeline sent to the AI is saved as is, not prepared again.
        assertArrayEquals(preparedPhoto.bytes, saved.photoBytes)
        // The shown guide keeps the scan location for a later opt-in.
        val shown = event.await() as LoadingNavigationEvent.NavigateToMonumentDetail
        assertEquals(scanLocation, shown.guide.location)
        assertNull(viewModel.saveError.value)
        assertEquals(ExploreOptInUiState(scanLocation = scanLocation), viewModel.exploreOptIn.value)
      }

  @Test
  fun leavingTheScreenAsSoonAsTheGuideIsShownKeepsTheSave() =
      runTest(dispatcher) {
        coEvery { repository.guideFor(request) } returns guide()
        val store = ViewModelStore()
        val viewModel =
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                  @Suppress("UNCHECKED_CAST")
                  override fun <T : ViewModel> create(modelClass: Class<T>): T =
                      newViewModel(userInterestsRepository) as T
                },
            )[LoadingViewModel::class.java]
        // The user leaves the moment the guide is shown, which clears the ViewModel.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          viewModel.navigationEvents.first()
          store.clear()
        }

        viewModel.startDiscovery()
        advanceUntilIdle()

        assertEquals(1, savedGuides.guides.value.size)
      }

  @Test
  fun failedSaveStillShowsGuideAndExposesError() =
      runTest(dispatcher) {
        val failure = IllegalStateException("A signed-in user is required")
        savedGuides.addError = failure
        coEvery { repository.guideFor(request) } returns guide()
        val viewModel = viewModel()
        val event = async { viewModel.navigationEvents.first() }

        advanceUntilIdle()

        assertTrue(event.await() is LoadingNavigationEvent.NavigateToMonumentDetail)
        assertEquals(failure, viewModel.saveError.value)
        assertEquals(LoadingUiState.Loading, viewModel.uiState.value)
      }

  @Test
  fun guideWithoutPhotoStillShowsGuideAndExposesSaveError() =
      runTest(dispatcher) {
        coEvery { repository.guideFor(request) } returns guide().copy(photo = null)
        val viewModel = viewModel()
        val event = async { viewModel.navigationEvents.first() }

        advanceUntilIdle()

        assertTrue(event.await() is LoadingNavigationEvent.NavigateToMonumentDetail)
        assertTrue(viewModel.saveError.value is IllegalStateException)
        assertTrue(savedGuides.guides.value.isEmpty())
      }

  @Test
  fun failedDiscoverySavesNothing() =
      runTest(dispatcher) {
        coEvery { repository.guideFor(request) } throws MonumentNotIdentifiedException()
        viewModel()

        advanceUntilIdle()

        assertTrue(savedGuides.guides.value.isEmpty())
      }

  @Test
  fun cancelledDiscoverySavesNothing() =
      runTest(dispatcher) {
        coEvery { repository.guideFor(request) } returns guide()
        val viewModel = viewModel()

        viewModel.onCancel()
        advanceUntilIdle()

        assertTrue(savedGuides.guides.value.isEmpty())
      }

  @Test
  fun retryAfterFailedSaveClearsTheError() =
      runTest(dispatcher) {
        savedGuides.addError = IllegalStateException("offline")
        coEvery { repository.guideFor(request) } returns guide()
        val viewModel = viewModel()
        advanceUntilIdle()

        savedGuides.addError = null
        viewModel.startDiscovery()
        advanceUntilIdle()

        assertNull(viewModel.saveError.value)
        assertEquals(1, savedGuides.guides.value.size)
      }

  @Test
  fun showingInExploreSetsTheScanLocationOnTheSavedGuide() =
      runTest(dispatcher) {
        coEvery { repository.guideFor(request) } returns guide().copy(location = scanLocation)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setShownInExplore(true)
        advanceUntilIdle()

        assertEquals(listOf(GUIDE_ID to scanLocation), savedGuides.locationUpdates)
        val saved = savedGuides.guides.value.single()
        assertEquals(GUIDE_ID, saved.id)
        assertEquals(scanLocation, saved.location)
        assertEquals(true, viewModel.exploreOptIn.value?.isShownInExplore)
        assertNull(viewModel.exploreOptIn.value?.error)
      }

  @Test
  fun hidingFromExploreClearsTheLocationAndKeepsTheGuide() =
      runTest(dispatcher) {
        coEvery { repository.guideFor(request) } returns guide().copy(location = scanLocation)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setShownInExplore(true)
        viewModel.setShownInExplore(false)
        advanceUntilIdle()

        assertEquals(
            listOf(GUIDE_ID to scanLocation, GUIDE_ID to null),
            savedGuides.locationUpdates,
        )
        val saved = savedGuides.guides.value.single()
        assertEquals(GUIDE_ID, saved.id)
        assertNull(saved.location)
        assertEquals(false, viewModel.exploreOptIn.value?.isShownInExplore)
      }

  @Test
  fun repeatingTheCurrentChoiceWritesNothing() =
      runTest(dispatcher) {
        coEvery { repository.guideFor(request) } returns guide().copy(location = scanLocation)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setShownInExplore(false)
        advanceUntilIdle()

        assertTrue(savedGuides.locationUpdates.isEmpty())
      }

  @Test
  fun scanWithoutLocationDoesNotOfferExplore() =
      runTest(dispatcher) {
        coEvery { repository.guideFor(request) } returns guide()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setShownInExplore(true)
        advanceUntilIdle()

        assertNull(viewModel.exploreOptIn.value)
        assertTrue(savedGuides.locationUpdates.isEmpty())
        assertNull(savedGuides.guides.value.single().location)
      }

  @Test
  fun failedSaveDoesNotOfferExplore() =
      runTest(dispatcher) {
        savedGuides.addError = IllegalStateException("A signed-in user is required")
        coEvery { repository.guideFor(request) } returns guide().copy(location = scanLocation)
        val viewModel = viewModel()
        advanceUntilIdle()

        assertNull(viewModel.exploreOptIn.value)
      }

  @Test
  fun failedShowInExploreKeepsTheGuideAndRollsBack() =
      runTest(dispatcher) {
        val failure = IllegalStateException("permission denied")
        coEvery { repository.guideFor(request) } returns guide().copy(location = scanLocation)
        val viewModel = viewModel()
        val event = async { viewModel.navigationEvents.first() }
        advanceUntilIdle()
        savedGuides.locationError = failure

        viewModel.setShownInExplore(true)
        advanceUntilIdle()

        assertEquals(
            ExploreOptInUiState(scanLocation = scanLocation, savedLocation = null, error = failure),
            viewModel.exploreOptIn.value,
        )
        val saved = savedGuides.guides.value.single()
        assertEquals(GUIDE_ID, saved.id)
        assertNull(saved.location)
        assertTrue(event.await() is LoadingNavigationEvent.NavigateToMonumentDetail)
        assertEquals(LoadingUiState.Loading, viewModel.uiState.value)
      }

  @Test
  fun failedHideFromExploreRestoresTheConfirmedLocation() =
      runTest(dispatcher) {
        val failure = IllegalStateException("offline")
        coEvery { repository.guideFor(request) } returns guide().copy(location = scanLocation)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.setShownInExplore(true)
        advanceUntilIdle()
        savedGuides.locationError = failure

        viewModel.setShownInExplore(false)
        advanceUntilIdle()

        assertEquals(scanLocation, viewModel.exploreOptIn.value?.savedLocation)
        assertEquals(failure, viewModel.exploreOptIn.value?.error)
        assertEquals(scanLocation, savedGuides.guides.value.single().location)
      }

  @Test
  fun olderUpdateFinishingLastDoesNotOverrideTheConfirmedLocation() =
      runTest(dispatcher) {
        // Each location update waits for its own gate, so the test decides the completion order.
        val gates = ArrayDeque<CompletableDeferred<Unit>>()
        val gatedRepository = mockk<SavedGuideRepository>()
        coEvery { gatedRepository.addGuide(any()) } coAnswers { firstArg<Guide>() }
        coEvery { gatedRepository.setLocation(any(), any()) } coAnswers
            {
              CompletableDeferred<Unit>().also { gates.addLast(it) }.await()
            }
        coEvery { repository.guideFor(request) } returns guide().copy(location = scanLocation)
        val viewModel =
            LoadingViewModel(
                    repository,
                    readImage,
                    uri,
                    "fr-FR",
                    userInterestsRepository,
                    gatedRepository,
                    clock = { discoveryDate },
                    newGuideId = { GUIDE_ID },
                )
                .also { it.startDiscovery() }
        advanceUntilIdle()

        viewModel.setShownInExplore(true)
        viewModel.setShownInExplore(false)
        val (showRequest, hideRequest) = gates.toList()
        // The newer hide request succeeds first, then the older show request.
        hideRequest.complete(Unit)
        runCurrent()
        showRequest.complete(Unit)
        runCurrent()
        // A failed retry must roll back to the hide request, the latest one confirmed.
        viewModel.setShownInExplore(true)
        gates.last().completeExceptionally(IllegalStateException("offline"))
        runCurrent()

        assertNull(viewModel.exploreOptIn.value?.savedLocation)
        assertTrue(viewModel.exploreOptIn.value?.error is IllegalStateException)
      }

  @Test
  fun olderUpdateSucceedingAfterTheLatestFailedIsShown() =
      runTest(dispatcher) {
        val store = GatedLocationStore()
        val viewModel = gatedViewModel(store)
        advanceUntilIdle()

        viewModel.setShownInExplore(true)
        viewModel.setShownInExplore(false)
        // Both updates are issued at once, in order, not queued behind each other.
        assertEquals(listOf(scanLocation, null), store.requested)
        val (showRequest, hideRequest) = store.gates.toList()
        val failure = IllegalStateException("permission denied")
        hideRequest.completeExceptionally(failure)
        runCurrent()
        assertNull(viewModel.exploreOptIn.value?.savedLocation)
        showRequest.complete(Unit)
        runCurrent()

        // The show request is the last one that succeeded, so it is what stays stored.
        assertEquals(scanLocation, store.stored)
        assertEquals(scanLocation, viewModel.exploreOptIn.value?.savedLocation)
        // The failure of the latest request is still reported.
        assertEquals(failure.message, viewModel.exploreOptIn.value?.error?.message)
      }

  @Test
  fun olderUpdateFailingAfterTheLatestFailedKeepsTheConfirmedLocation() =
      runTest(dispatcher) {
        val store = GatedLocationStore()
        val viewModel = gatedViewModel(store)
        advanceUntilIdle()

        viewModel.setShownInExplore(true)
        viewModel.setShownInExplore(false)
        val (showRequest, hideRequest) = store.gates.toList()
        val failure = IllegalStateException("permission denied")
        hideRequest.completeExceptionally(failure)
        runCurrent()
        showRequest.completeExceptionally(IllegalStateException("offline"))
        runCurrent()

        assertNull(store.stored)
        assertNull(viewModel.exploreOptIn.value?.savedLocation)
        // Only the latest request reports its failure.
        assertEquals(failure.message, viewModel.exploreOptIn.value?.error?.message)
      }

  @Test
  fun olderUpdateSucceedingDoesNotOverrideANewerPendingRequest() =
      runTest(dispatcher) {
        val store = GatedLocationStore()
        val viewModel = gatedViewModel(store)
        advanceUntilIdle()

        viewModel.setShownInExplore(true)
        viewModel.setShownInExplore(false)
        store.gates[1].completeExceptionally(IllegalStateException("permission denied"))
        runCurrent()
        // Retrying replaces the failure with a new pending request the switch must keep showing.
        viewModel.setShownInExplore(true)
        viewModel.setShownInExplore(false)
        store.gates[0].complete(Unit)
        runCurrent()

        assertNull(viewModel.exploreOptIn.value?.savedLocation)
        assertNull(viewModel.exploreOptIn.value?.error)
        store.gates[2].complete(Unit)
        store.gates[3].complete(Unit)
        runCurrent()
        assertNull(store.stored)
        assertNull(viewModel.exploreOptIn.value?.savedLocation)
      }

  @Test
  fun resultsArrivingOutOfOrderAfterTheLatestFailedShowTheStoredLocation() =
      runTest(dispatcher) {
        val store = GatedLocationStore()
        val viewModel = gatedViewModel(store)
        advanceUntilIdle()

        viewModel.setShownInExplore(true)
        viewModel.setShownInExplore(false)
        viewModel.setShownInExplore(true)
        store.gates[2].completeExceptionally(IllegalStateException("permission denied"))
        runCurrent()
        store.gates[1].complete(Unit)
        runCurrent()
        // The show request was made before the hide request, so it cannot override it.
        store.gates[0].complete(Unit)
        runCurrent()

        assertNull(store.stored)
        assertNull(viewModel.exploreOptIn.value?.savedLocation)
      }

  @Test
  fun leavingTheScreenWhileUpdatesArePendingKeepsTheLatestChoice() =
      runTest(dispatcher) {
        val store = GatedLocationStore()
        val viewModelStore = ViewModelStore()
        val viewModel =
            ViewModelProvider(
                viewModelStore,
                object : ViewModelProvider.Factory {
                  @Suppress("UNCHECKED_CAST")
                  override fun <T : ViewModel> create(modelClass: Class<T>): T =
                      gatedViewModel(store) as T
                },
            )[LoadingViewModel::class.java]
        advanceUntilIdle()

        viewModel.setShownInExplore(true)
        viewModel.setShownInExplore(false)
        // The user leaves while offline, before either update is acknowledged.
        viewModelStore.clear()
        store.gates.forEach { it.complete(Unit) }
        runCurrent()

        assertEquals(listOf(scanLocation, null), store.requested)
        assertNull(store.stored)
      }

  /**
   * Location store following the [SavedGuideRepository.setLocation] contract, like Firestore's
   * write queue. Each update waits for its own gate, so a test decides when and how it completes.
   * An update belongs to the store once called: it is applied even if the caller stops waiting, and
   * in call order whatever order the results arrive in. [stored] is the location of the last call
   * that succeeded.
   */
  private class GatedLocationStore {
    val gates = mutableListOf<CompletableDeferred<Unit>>()
    val requested = mutableListOf<Coordinates?>()
    private var lastAppliedCall = -1
    var stored: Coordinates? = null
      private set

    val repository =
        mockk<SavedGuideRepository>().also { repository ->
          coEvery { repository.addGuide(any()) } coAnswers { firstArg<Guide>() }
          coEvery { repository.setLocation(any(), any()) } coAnswers
              {
                val location = secondArg<Coordinates?>()
                val call = requested.size
                requested += location
                val gate = CompletableDeferred<Unit>().also { gates += it }
                gate.invokeOnCompletion { failure ->
                  if (failure == null && call > lastAppliedCall) {
                    lastAppliedCall = call
                    stored = location
                  }
                }
                gate.await()
              }
        }
  }

  private fun gatedViewModel(store: GatedLocationStore): LoadingViewModel {
    coEvery { repository.guideFor(request) } returns guide().copy(location = scanLocation)
    return LoadingViewModel(
            repository,
            readImage,
            uri,
            "fr-FR",
            userInterestsRepository,
            store.repository,
            clock = { discoveryDate },
            newGuideId = { GUIDE_ID },
        )
        .also { it.startDiscovery() }
  }

  @Test
  fun retryingAfterAFailedUpdateClearsTheError() =
      runTest(dispatcher) {
        coEvery { repository.guideFor(request) } returns guide().copy(location = scanLocation)
        val viewModel = viewModel()
        advanceUntilIdle()
        savedGuides.locationError = IllegalStateException("offline")
        viewModel.setShownInExplore(true)
        advanceUntilIdle()

        savedGuides.locationError = null
        viewModel.setShownInExplore(true)
        advanceUntilIdle()

        assertEquals(
            ExploreOptInUiState(scanLocation = scanLocation, savedLocation = scanLocation),
            viewModel.exploreOptIn.value,
        )
        assertEquals(scanLocation, savedGuides.guides.value.single().location)
      }

  private class FakeUserInterestsRepository(
      private var hasBeenAsked: Boolean = true,
      private var interests: List<String> = emptyList(),
  ) : UserInterestsRepository {
    override fun getUserInterests() = UserInterestsState(hasBeenAsked, interests)

    override fun updateInterests(interests: List<String>) {
      this.interests = interests
    }

    override fun saveInterests(interests: List<String>) {
      hasBeenAsked = true
      this.interests = interests
    }

    override fun skipInterests() {
      hasBeenAsked = true
      interests = emptyList()
    }
  }

  private companion object {
    const val GUIDE_ID = "scan-guide-id"
    val scanLocation = Coordinates(41.8902, 12.4922)
  }

  private fun guide() =
      MonumentGuide(
          monumentName = "The Colosseum",
          confidence = 0.98,
          history = "Built in ancient Rome.",
          facts = listOf("It could hold tens of thousands of spectators."),
          culture = "A symbol of Rome.",
          language = "en",
          photo = preparedPhoto,
      )
}
