// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.guide

import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.guide.SavedGuideRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedGuidesViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val repository = mockk<SavedGuideRepository>()
  private val guide =
      Guide(
          id = "guide-1",
          photoBytes = byteArrayOf(1, 2, 3),
          monumentName = "Eiffel Tower",
          confidence = 0.92,
          history = "Built in 1889.",
          facts = listOf("330 meters tall"),
          culture = "A symbol of Paris.",
      )

  @Before
  fun setMainDispatcher() {
    Dispatchers.setMain(dispatcher)
    every { repository.observeSavedGuides() } answers { flow { emit(repository.getSavedGuides()) } }
  }

  @After
  fun resetMainDispatcher() {
    Dispatchers.resetMain()
  }

  @Test
  fun loadsCollectionIntoUiState() =
      runTest(dispatcher) {
        coEvery { repository.getSavedGuides() } returns listOf(guide)
        val viewModel = SavedGuidesViewModel(repository)

        viewModel.loadSavedGuides()
        assertTrue(viewModel.uiState.value.isLoadingGuides)
        advanceUntilIdle()

        assertEquals(listOf(guide), viewModel.uiState.value.guides)
        assertFalse(viewModel.uiState.value.isLoadingGuides)
        assertNull(viewModel.uiState.value.guidesError)
      }

  @Test
  fun ignoresRepeatedCollectionLoadWhileRequestIsActive() =
      runTest(dispatcher) {
        val result = CompletableDeferred<List<Guide>>()
        coEvery { repository.getSavedGuides() } coAnswers { result.await() }
        val viewModel = SavedGuidesViewModel(repository)

        viewModel.loadSavedGuides()
        runCurrent()
        viewModel.loadSavedGuides()
        runCurrent()

        coVerify(exactly = 1) { repository.getSavedGuides() }
        result.complete(listOf(guide))
        advanceUntilIdle()
        assertEquals(listOf(guide), viewModel.uiState.value.guides)
      }

  @Test
  fun reflectsSubsequentCollectionSnapshots() =
      runTest(dispatcher) {
        val snapshots = MutableStateFlow(listOf(guide))
        every { repository.observeSavedGuides() } returns snapshots
        val viewModel = SavedGuidesViewModel(repository)

        viewModel.loadSavedGuides()
        runCurrent()
        assertEquals(listOf(guide), viewModel.uiState.value.guides)

        snapshots.value = emptyList()
        runCurrent()
        assertTrue(viewModel.uiState.value.guides.isEmpty())
      }

  @Test
  fun savesGuideAndExposesItsGeneratedId() =
      runTest(dispatcher) {
        val guideWithoutId = guide.copy(id = "")
        coEvery { repository.addGuide(any()) } coAnswers { firstArg<Guide>() }
        val viewModel = SavedGuidesViewModel(repository)

        viewModel.saveGuide(guideWithoutId)
        val savedGuide = viewModel.uiState.value.guides.single()
        assertTrue(savedGuide.id.isNotBlank())
        assertTrue(viewModel.uiState.value.isSavingGuide)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSavingGuide)
        assertEquals(savedGuide.id, viewModel.uiState.value.savedGuideId)
        assertEquals(listOf(savedGuide), viewModel.uiState.value.guides)
        assertNull(viewModel.uiState.value.saveError)
        coVerify(exactly = 1) { repository.addGuide(savedGuide) }
      }

  @Test
  fun repeatedSaveClicksForSameGuideOnlySubmitOneSave() =
      runTest(dispatcher) {
        val guideToSave = guide.copy(id = "")
        val delayedSave = CompletableDeferred<Guide>()
        coEvery { repository.addGuide(any()) } coAnswers { delayedSave.await() }
        val viewModel = SavedGuidesViewModel(repository)

        viewModel.saveGuide(guideToSave)
        viewModel.saveGuide(guideToSave.copy(discoveryDate = guideToSave.discoveryDate!! + 1))
        runCurrent()

        assertEquals(1, viewModel.uiState.value.guides.size)
        assertTrue(viewModel.uiState.value.isSavingGuide)
        coVerify(exactly = 1) { repository.addGuide(any()) }

        val pendingGuide = viewModel.uiState.value.guides.single()
        delayedSave.complete(pendingGuide)
        advanceUntilIdle()

        assertEquals(listOf(pendingGuide), viewModel.uiState.value.guides)
      }

  @Test
  fun showsMultiplePendingSavesImmediatelyWhileOffline() =
      runTest(dispatcher) {
        val firstGuide = guide.copy(id = "")
        val secondGuide = guide.copy(id = "", monumentName = "Arc de Triomphe")
        val firstResult = CompletableDeferred<Guide>()
        val secondResult = CompletableDeferred<Guide>()
        val calls = mutableListOf<Guide>()
        coEvery { repository.addGuide(any()) } coAnswers
            {
              val savedGuide = firstArg<Guide>()
              calls += savedGuide
              if (savedGuide.monumentName == firstGuide.monumentName) {
                firstResult.await()
              } else {
                secondResult.await()
              }
            }
        val viewModel = SavedGuidesViewModel(repository)

        viewModel.saveGuide(firstGuide)
        runCurrent()
        viewModel.saveGuide(secondGuide)
        runCurrent()

        assertEquals(2, viewModel.uiState.value.guides.size)
        assertTrue(viewModel.uiState.value.isSavingGuide)
        coVerify(exactly = 2) { repository.addGuide(any()) }
        firstResult.complete(calls.first())
        secondResult.complete(calls.last())
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSavingGuide)
        assertEquals(2, viewModel.uiState.value.guides.size)
      }

  @Test
  fun exposesSaveFailure() =
      runTest(dispatcher) {
        val error = IllegalStateException("save failed")
        coEvery { repository.addGuide(any()) } throws error
        val viewModel = SavedGuidesViewModel(repository)

        viewModel.saveGuide(guide)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSavingGuide)
        assertEquals(error, viewModel.uiState.value.saveError)
        assertNull(viewModel.uiState.value.savedGuideId)
      }

  @Test
  fun exposesCollectionLoadFailure() =
      runTest(dispatcher) {
        val error = IllegalStateException("offline cache unavailable")
        coEvery { repository.getSavedGuides() } throws error
        val viewModel = SavedGuidesViewModel(repository)

        viewModel.loadSavedGuides()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoadingGuides)
        assertEquals(error, viewModel.uiState.value.guidesError)
      }

  @Test
  fun loadsPreciseGuideIntoUiState() =
      runTest(dispatcher) {
        coEvery { repository.getGuide(guide.id) } returns guide
        val viewModel = SavedGuidesViewModel(repository)

        viewModel.loadGuide(guide.id)
        advanceUntilIdle()

        assertEquals(guide, viewModel.uiState.value.guide)
        assertFalse(viewModel.uiState.value.isLoadingGuide)
        assertNull(viewModel.uiState.value.guideError)
        coVerify { repository.getGuide(guide.id) }
      }

  @Test
  fun deletingGuideRemovesItFromLoadedCollectionAndDetail() =
      runTest(dispatcher) {
        coEvery { repository.getSavedGuides() } returns listOf(guide)
        coEvery { repository.getGuide(guide.id) } returns guide
        coEvery { repository.removeGuide(guide.id) } returns Unit
        val viewModel = SavedGuidesViewModel(repository)

        viewModel.loadSavedGuides()
        advanceUntilIdle()
        viewModel.loadGuide(guide.id)
        advanceUntilIdle()
        viewModel.deleteGuide(guide.id)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.guides.isEmpty())
        assertNull(viewModel.uiState.value.guide)
        assertTrue(viewModel.uiState.value.deletingGuideIds.isEmpty())
        assertNull(viewModel.uiState.value.deleteError)
        coVerify { repository.removeGuide(guide.id) }
      }

  @Test
  fun removesGuideFromUiBeforeOfflineDeleteIsAcknowledged() =
      runTest(dispatcher) {
        val deleteAcknowledgement = CompletableDeferred<Unit>()
        coEvery { repository.getSavedGuides() } returns listOf(guide)
        coEvery { repository.removeGuide(guide.id) } coAnswers { deleteAcknowledgement.await() }
        val viewModel = SavedGuidesViewModel(repository)

        viewModel.loadSavedGuides()
        advanceUntilIdle()
        viewModel.deleteGuide(guide.id)
        runCurrent()

        assertTrue(viewModel.uiState.value.guides.isEmpty())
        assertTrue(guide.id in viewModel.uiState.value.deletingGuideIds)

        deleteAcknowledgement.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.deletingGuideIds.isEmpty())
      }

  @Test
  fun exposesDeletionFailure() =
      runTest(dispatcher) {
        val error = IllegalStateException("delete failed")
        coEvery { repository.getSavedGuides() } returns listOf(guide)
        coEvery { repository.removeGuide(guide.id) } throws error
        val viewModel = SavedGuidesViewModel(repository)

        viewModel.loadSavedGuides()
        advanceUntilIdle()
        viewModel.deleteGuide(guide.id)
        advanceUntilIdle()

        assertEquals(listOf(guide), viewModel.uiState.value.guides)
        assertEquals(error, viewModel.uiState.value.deleteError)
        assertTrue(viewModel.uiState.value.deletingGuideIds.isEmpty())
      }
}
