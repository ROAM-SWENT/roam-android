// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.guide

import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.guide.SavedGuideRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class SavedGuidesViewModelFavoriteTest {
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
  private val otherGuide = guide.copy(id = "guide-2", monumentName = "Colosseum")
  private val snapshots = MutableStateFlow(listOf(guide))

  /** Favourite writes in call order, completed by each test to simulate the server ack. */
  private val favoriteWrites = mutableListOf<CompletableDeferred<Unit>>()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    every { repository.observeSavedGuides() } returns snapshots
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun setFavoriteUpdatesCollectionImmediatelyAndPersistsIt() =
      runTest(dispatcher) {
        coEvery { repository.setFavorite(any(), any()) } just Runs
        val viewModel = loadedViewModel()

        viewModel.setFavorite(guide.id, true)
        assertTrue(viewModel.uiState.value.guides.single().isFavorite)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.guides.single().isFavorite)
        assertNull(viewModel.uiState.value.favoriteError)
        coVerify(exactly = 1) { repository.setFavorite(guide.id, true) }
      }

  @Test
  fun setFavoriteCanRemoveAGuideFromFavorites() =
      runTest(dispatcher) {
        snapshots.value = listOf(guide.copy(isFavorite = true))
        coEvery { repository.setFavorite(any(), any()) } just Runs
        val viewModel = loadedViewModel()

        viewModel.setFavorite(guide.id, false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.guides.single().isFavorite)
        coVerify(exactly = 1) { repository.setFavorite(guide.id, false) }
      }

  @Test
  fun failedFavoriteChangeIsRolledBackAndReported() =
      runTest(dispatcher) {
        val error = IllegalStateException("favourite failed")
        coEvery { repository.setFavorite(any(), any()) } throws error
        val viewModel = loadedViewModel()

        viewModel.setFavorite(guide.id, true)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.guides.single().isFavorite)
        assertEquals(error, viewModel.uiState.value.favoriteError)
      }

  @Test
  fun settingTheCurrentValueOrAnUnknownGuideDoesNothing() =
      runTest(dispatcher) {
        val viewModel = loadedViewModel()

        viewModel.setFavorite(guide.id, false)
        viewModel.setFavorite("missing-id", true)
        viewModel.toggleFavorite("missing-id")
        advanceUntilIdle()

        assertEquals(listOf(guide), viewModel.uiState.value.guides)
        coVerify(exactly = 0) { repository.setFavorite(any(), any()) }
      }

  @Test
  fun toggleFavoriteFlipsTheDisplayedValue() =
      runTest(dispatcher) {
        coEvery { repository.setFavorite(any(), any()) } just Runs
        val viewModel = loadedViewModel()

        viewModel.toggleFavorite(guide.id)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.guides.single().isFavorite)

        viewModel.toggleFavorite(guide.id)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.guides.single().isFavorite)

        coVerifyOrder {
          repository.setFavorite(guide.id, true)
          repository.setFavorite(guide.id, false)
        }
      }

  @Test
  fun favoriteCanBeChangedFromTheDetailScreenWithoutTheCollection() =
      runTest(dispatcher) {
        coEvery { repository.getGuide(guide.id) } returns guide
        coEvery { repository.setFavorite(any(), any()) } just Runs
        val viewModel = SavedGuidesViewModel(repository)
        viewModel.loadGuide(guide.id)
        advanceUntilIdle()

        viewModel.toggleFavorite(guide.id)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.guide!!.isFavorite)
        coVerify(exactly = 1) { repository.setFavorite(guide.id, true) }
      }

  @Test
  fun favoriteChangeUpdatesBothCollectionAndDetailGuide() =
      runTest(dispatcher) {
        snapshots.value = listOf(guide, otherGuide)
        coEvery { repository.getGuide(guide.id) } returns guide
        coEvery { repository.setFavorite(any(), any()) } just Runs
        val viewModel = loadedViewModel()
        viewModel.loadGuide(guide.id)
        advanceUntilIdle()

        viewModel.setFavorite(guide.id, true)

        val state = viewModel.uiState.value
        assertTrue(state.guide!!.isFavorite)
        assertTrue(state.guides.first { it.id == guide.id }.isFavorite)
        assertFalse(state.guides.first { it.id == otherGuide.id }.isFavorite)
      }

  @Test
  fun staleSnapshotDoesNotRevertAPendingFavorite() =
      runTest(dispatcher) {
        recordFavoriteWrites()
        val viewModel = loadedViewModel()

        viewModel.setFavorite(guide.id, true)
        runCurrent()
        snapshots.value = listOf(guide, otherGuide)
        runCurrent()

        assertTrue(viewModel.uiState.value.guides.first { it.id == guide.id }.isFavorite)

        favoriteWrites.single().complete(Unit)
        snapshots.value = listOf(guide.copy(isFavorite = true), otherGuide)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.guides.first { it.id == guide.id }.isFavorite)
      }

  @Test
  fun pendingWritesNeverBlockANewerRequestAndTheLatestWins() =
      runTest(dispatcher) {
        recordFavoriteWrites()
        val viewModel = loadedViewModel()

        // Offline, no write completes, yet every tap is applied.
        viewModel.setFavorite(guide.id, true)
        viewModel.setFavorite(guide.id, false)
        viewModel.setFavorite(guide.id, true)
        runCurrent()

        assertEquals(3, favoriteWrites.size)
        assertTrue(viewModel.uiState.value.guides.single().isFavorite)
        coVerifyOrder {
          repository.setFavorite(guide.id, true)
          repository.setFavorite(guide.id, false)
          repository.setFavorite(guide.id, true)
        }

        favoriteWrites.forEach { it.complete(Unit) }
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.guides.single().isFavorite)
        assertNull(viewModel.uiState.value.favoriteError)
      }

  @Test
  fun failureOfAnOlderWriteKeepsTheNewerValue() =
      runTest(dispatcher) {
        recordFavoriteWrites()
        val error = IllegalStateException("older write failed")
        val viewModel = loadedViewModel()

        viewModel.setFavorite(guide.id, true)
        viewModel.setFavorite(guide.id, false)
        runCurrent()
        favoriteWrites[0].completeExceptionally(error)
        runCurrent()

        assertFalse(viewModel.uiState.value.guides.single().isFavorite)
        // Coroutines may rethrow a copy of the exception from await(), so compare the message.
        assertEquals(error.message, viewModel.uiState.value.favoriteError?.message)
      }

  @Test
  fun failureOfTheLatestWriteRestoresTheLastConfirmedValue() =
      runTest(dispatcher) {
        recordFavoriteWrites()
        val viewModel = loadedViewModel()

        viewModel.setFavorite(guide.id, true)
        viewModel.setFavorite(guide.id, false)
        runCurrent()
        favoriteWrites[0].complete(Unit)
        runCurrent()
        favoriteWrites[1].completeExceptionally(IllegalStateException("latest write failed"))
        runCurrent()

        // The server confirmed `true`, so that is restored rather than the original `false`.
        assertTrue(viewModel.uiState.value.guides.single().isFavorite)
      }

  @Test
  fun loadGuideShowsAPendingFavorite() =
      runTest(dispatcher) {
        recordFavoriteWrites()
        coEvery { repository.getGuide(guide.id) } returns guide
        val viewModel = loadedViewModel()

        viewModel.setFavorite(guide.id, true)
        viewModel.loadGuide(guide.id)
        runCurrent()

        assertTrue(viewModel.uiState.value.guide!!.isFavorite)
      }

  @Test
  fun saveResultThatPredatesAFavoriteDoesNotRevertIt() =
      runTest(dispatcher) {
        snapshots.value = emptyList()
        val delayedSave = CompletableDeferred<Guide>()
        coEvery { repository.addGuide(any()) } coAnswers { delayedSave.await() }
        recordFavoriteWrites()
        val viewModel = loadedViewModel()

        viewModel.saveGuide(guide)
        runCurrent()
        viewModel.setFavorite(guide.id, true)
        runCurrent()
        // Firestore acknowledges writes in order, so the save completes before the favourite.
        delayedSave.complete(guide)
        runCurrent()

        assertTrue(viewModel.uiState.value.guides.single().isFavorite)
      }

  @Test
  fun deletingAGuideDropsItsPendingFavorite() =
      runTest(dispatcher) {
        recordFavoriteWrites()
        coEvery { repository.removeGuide(guide.id) } just Runs
        val viewModel = loadedViewModel()

        viewModel.setFavorite(guide.id, true)
        runCurrent()
        viewModel.deleteGuide(guide.id)
        runCurrent()
        favoriteWrites.single().completeExceptionally(IllegalStateException("guide deleted"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.guides.isEmpty())
      }

  @Test
  fun clearErrorsResetsFavoriteError() =
      runTest(dispatcher) {
        coEvery { repository.setFavorite(any(), any()) } throws IllegalStateException("failed")
        val viewModel = loadedViewModel()
        viewModel.setFavorite(guide.id, true)
        advanceUntilIdle()

        viewModel.clearErrors()

        assertNull(viewModel.uiState.value.favoriteError)
      }

  @Test
  fun cancellationIsNotShownAsAFavoriteError() =
      runTest(dispatcher) {
        coEvery { repository.setFavorite(any(), any()) } throws CancellationException("cancelled")
        val viewModel = loadedViewModel()

        viewModel.setFavorite(guide.id, true)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.favoriteError)
      }

  private fun TestScope.loadedViewModel(): SavedGuidesViewModel {
    val viewModel = SavedGuidesViewModel(repository)
    viewModel.loadSavedGuides()
    runCurrent()
    return viewModel
  }

  private fun recordFavoriteWrites() {
    coEvery { repository.setFavorite(any(), any()) } coAnswers
        {
          CompletableDeferred<Unit>().also { favoriteWrites += it }.await()
        }
  }
}
