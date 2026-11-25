// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.guide

import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.guide.MonumentShareSettings
import com.github.roamswent.roam.model.guide.MonumentSharingRepository
import com.github.roamswent.roam.model.guide.SavedGuideRepository
import com.github.roamswent.roam.model.guide.ShareAudience
import com.github.roamswent.roam.model.guide.SharingEligibility
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
  private val sharing = mockk<MonumentSharingRepository>()
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

  /** Sharing writes in call order, completed by each test to simulate the server ack. */
  private val sharingWrites = mutableListOf<CompletableDeferred<Unit>>()

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
  fun setShareAudienceUpdatesAndPersistsAllowedFriendUids() =
      runTest(dispatcher) {
        coEvery { sharing.setShareAudience(any(), any(), any()) } just Runs
        val viewModel = loadedViewModel()

        viewModel.setShareAudience(guide.id, ShareAudience.SELECTED_FRIENDS, setOf("friend-a"))
        assertEquals(
            setOf("friend-a"),
            viewModel.uiState.value.guides.single().shareSettings.sharedWithUids,
        )
        assertEquals(setOf(guide.id), viewModel.uiState.value.sharingGuideIds)
        advanceUntilIdle()

        assertEquals(
            setOf("friend-a"),
            viewModel.uiState.value.guides.single().shareSettings.sharedWithUids,
        )
        assertTrue(viewModel.uiState.value.sharingGuideIds.isEmpty())
        assertNull(viewModel.uiState.value.sharingError)
        coVerify(exactly = 1) {
          sharing.setShareAudience(guide.id, ShareAudience.SELECTED_FRIENDS, setOf("friend-a"))
        }
      }

  @Test
  fun failedSharingChangeIsRolledBackAndReported() =
      runTest(dispatcher) {
        val error = IllegalStateException("sharing failed")
        coEvery { sharing.setShareAudience(any(), any(), any()) } throws error
        val viewModel = loadedViewModel()

        viewModel.setShareAudience(guide.id, ShareAudience.SELECTED_FRIENDS, setOf("friend-a"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.guides.single().shareSettings.sharedWithUids.isEmpty())
        assertEquals(error, viewModel.uiState.value.sharingError)
      }

  @Test
  fun tooFarGuideIgnoresSharingChanges() =
      runTest(dispatcher) {
        snapshots.value = listOf(guide.copy(sharingEligibility = SharingEligibility.TOO_FAR))
        coEvery { sharing.setShareAudience(any(), any(), any()) } just Runs
        val viewModel = loadedViewModel()

        viewModel.setShareAudience(guide.id, ShareAudience.ALL_FRIENDS)
        advanceUntilIdle()

        assertEquals(MonumentShareSettings(), viewModel.uiState.value.guides.single().shareSettings)
        coVerify(exactly = 0) { sharing.setShareAudience(any(), any(), any()) }
      }

  @Test
  fun sharingChangeUpdatesCollectionAndDetailWithoutChangingOtherGuides() =
      runTest(dispatcher) {
        snapshots.value = listOf(guide, otherGuide)
        coEvery { repository.getGuide(guide.id) } returns guide
        coEvery { sharing.setShareAudience(any(), any(), any()) } just Runs
        val viewModel = loadedViewModel()
        viewModel.loadGuide(guide.id)
        advanceUntilIdle()

        viewModel.setShareAudience(guide.id, ShareAudience.SELECTED_FRIENDS, setOf("friend-a"))

        assertEquals(
            setOf("friend-a"),
            viewModel.uiState.value.guides.first { it.id == guide.id }.shareSettings.sharedWithUids,
        )
        assertEquals(
            setOf("friend-a"),
            viewModel.uiState.value.guide!!.shareSettings.sharedWithUids,
        )
        assertTrue(
            viewModel.uiState.value.guides
                .first { it.id == otherGuide.id }
                .shareSettings
                .sharedWithUids
                .isEmpty()
        )
        advanceUntilIdle()
        coVerify(exactly = 1) {
          sharing.setShareAudience(guide.id, ShareAudience.SELECTED_FRIENDS, setOf("friend-a"))
        }
      }

  @Test
  fun sharingCanBeChangedForDetailGuideWithoutLoadedCollection() =
      runTest(dispatcher) {
        coEvery { repository.getGuide(guide.id) } returns guide
        coEvery { sharing.setShareAudience(any(), any(), any()) } just Runs
        val viewModel = SavedGuidesViewModel(repository, sharing)
        viewModel.loadGuide(guide.id)
        advanceUntilIdle()

        viewModel.setShareAudience(guide.id, ShareAudience.SELECTED_FRIENDS, setOf("friend-a"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.guides.isEmpty())
        assertEquals(
            setOf("friend-a"),
            viewModel.uiState.value.guide!!.shareSettings.sharedWithUids,
        )
        coVerify(exactly = 1) {
          sharing.setShareAudience(guide.id, ShareAudience.SELECTED_FRIENDS, setOf("friend-a"))
        }
      }

  @Test
  fun sharingChangeDeduplicatesUidsAndSkipsUnchangedOrUnknownGuides() =
      runTest(dispatcher) {
        recordSharingWrites()
        val viewModel = loadedViewModel()

        viewModel.setShareAudience(guide.id, ShareAudience.NONE)
        viewModel.setShareAudience("missing-id", ShareAudience.SELECTED_FRIENDS, setOf("friend-a"))
        viewModel.setShareAudience(
            guide.id,
            ShareAudience.SELECTED_FRIENDS,
            setOf("friend-a", "friend-a", "friend-b"),
        )
        viewModel.setShareAudience(
            guide.id,
            ShareAudience.SELECTED_FRIENDS,
            setOf("friend-a", "friend-b"),
        )
        runCurrent()

        assertEquals(
            setOf("friend-a", "friend-b"),
            viewModel.uiState.value.guides.single().shareSettings.sharedWithUids,
        )
        assertEquals(1, sharingWrites.size)
        coVerify(exactly = 1) {
          sharing.setShareAudience(
              guide.id,
              ShareAudience.SELECTED_FRIENDS,
              setOf("friend-a", "friend-b"),
          )
        }

        sharingWrites.single().complete(Unit)
        advanceUntilIdle()
      }

  @Test
  fun pendingSharingOverridesRepositorySnapshotsAndGuideLoads() =
      runTest(dispatcher) {
        recordSharingWrites()
        coEvery { repository.getGuide(guide.id) } returns guide
        val viewModel = loadedViewModel()

        viewModel.setShareAudience(guide.id, ShareAudience.SELECTED_FRIENDS, setOf("friend-a"))
        runCurrent()
        snapshots.value = listOf(guide, otherGuide)
        runCurrent()
        viewModel.loadGuide(guide.id)
        runCurrent()

        assertEquals(
            setOf("friend-a"),
            viewModel.uiState.value.guides.first { it.id == guide.id }.shareSettings.sharedWithUids,
        )
        assertEquals(
            setOf("friend-a"),
            viewModel.uiState.value.guide!!.shareSettings.sharedWithUids,
        )

        sharingWrites.single().complete(Unit)
        advanceUntilIdle()
      }

  @Test
  fun latestSharingRequestWinsAndFailureRestoresLatestConfirmedPermissions() =
      runTest(dispatcher) {
        recordSharingWrites()
        val latestError = IllegalStateException("latest sharing write failed")
        val viewModel = loadedViewModel()

        viewModel.setShareAudience(guide.id, ShareAudience.SELECTED_FRIENDS, setOf("friend-a"))
        viewModel.setShareAudience(guide.id, ShareAudience.SELECTED_FRIENDS, setOf("friend-b"))
        runCurrent()
        assertEquals(
            setOf("friend-b"),
            viewModel.uiState.value.guides.single().shareSettings.sharedWithUids,
        )
        assertEquals(setOf(guide.id), viewModel.uiState.value.sharingGuideIds)

        sharingWrites[0].complete(Unit)
        runCurrent()
        assertEquals(
            setOf("friend-b"),
            viewModel.uiState.value.guides.single().shareSettings.sharedWithUids,
        )
        assertEquals(setOf(guide.id), viewModel.uiState.value.sharingGuideIds)

        sharingWrites[1].completeExceptionally(latestError)
        advanceUntilIdle()

        assertEquals(
            setOf("friend-a"),
            viewModel.uiState.value.guides.single().shareSettings.sharedWithUids,
        )
        assertTrue(viewModel.uiState.value.sharingGuideIds.isEmpty())
        assertTrue(viewModel.uiState.value.sharingError is IllegalStateException)
        assertEquals(latestError.message, viewModel.uiState.value.sharingError?.message)
      }

  @Test
  fun failureOfAnOlderSharingWriteDoesNotRollbackOrReportOverNewerRequest() =
      runTest(dispatcher) {
        recordSharingWrites()
        val viewModel = loadedViewModel()

        viewModel.setShareAudience(guide.id, ShareAudience.SELECTED_FRIENDS, setOf("friend-a"))
        viewModel.setShareAudience(guide.id, ShareAudience.SELECTED_FRIENDS, setOf("friend-b"))
        runCurrent()
        sharingWrites[0].completeExceptionally(IllegalStateException("older write failed"))
        runCurrent()

        assertEquals(
            setOf("friend-b"),
            viewModel.uiState.value.guides.single().shareSettings.sharedWithUids,
        )
        assertNull(viewModel.uiState.value.sharingError)
        assertEquals(setOf(guide.id), viewModel.uiState.value.sharingGuideIds)

        sharingWrites[1].complete(Unit)
        advanceUntilIdle()
        assertEquals(
            setOf("friend-b"),
            viewModel.uiState.value.guides.single().shareSettings.sharedWithUids,
        )
        assertNull(viewModel.uiState.value.sharingError)
        assertTrue(viewModel.uiState.value.sharingGuideIds.isEmpty())
      }

  @Test
  fun sharingWritesForDifferentGuidesKeepIndependentPendingState() =
      runTest(dispatcher) {
        snapshots.value = listOf(guide, otherGuide)
        recordSharingWrites()
        val error = IllegalStateException("first guide sharing failed")
        val viewModel = loadedViewModel()

        viewModel.setShareAudience(guide.id, ShareAudience.SELECTED_FRIENDS, setOf("friend-a"))
        viewModel.setShareAudience(otherGuide.id, ShareAudience.SELECTED_FRIENDS, setOf("friend-b"))
        runCurrent()
        assertEquals(setOf(guide.id, otherGuide.id), viewModel.uiState.value.sharingGuideIds)

        sharingWrites[0].completeExceptionally(error)
        runCurrent()

        assertEquals(
            emptySet<String>(),
            viewModel.uiState.value.guides.first { it.id == guide.id }.shareSettings.sharedWithUids,
        )
        assertEquals(
            setOf("friend-b"),
            viewModel.uiState.value.guides
                .first { it.id == otherGuide.id }
                .shareSettings
                .sharedWithUids,
        )
        assertEquals(setOf(otherGuide.id), viewModel.uiState.value.sharingGuideIds)
        assertTrue(viewModel.uiState.value.sharingError is IllegalStateException)
        assertEquals(error.message, viewModel.uiState.value.sharingError?.message)

        sharingWrites[1].complete(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.sharingGuideIds.isEmpty())
        assertEquals(
            setOf("friend-b"),
            viewModel.uiState.value.guides.last().shareSettings.sharedWithUids,
        )
      }

  @Test
  fun sharingWriteCanBeRetriedAfterFailure() =
      runTest(dispatcher) {
        var attempts = 0
        coEvery { sharing.setShareAudience(any(), any(), any()) } coAnswers
            {
              attempts++
              if (attempts == 1) throw IllegalStateException("offline") else Unit
            }
        val viewModel = loadedViewModel()

        viewModel.setShareAudience(guide.id, ShareAudience.SELECTED_FRIENDS, setOf("friend-a"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.guides.single().shareSettings.sharedWithUids.isEmpty())
        assertEquals("offline", viewModel.uiState.value.sharingError?.message)

        viewModel.setShareAudience(guide.id, ShareAudience.SELECTED_FRIENDS, setOf("friend-a"))
        advanceUntilIdle()

        assertEquals(
            setOf("friend-a"),
            viewModel.uiState.value.guides.single().shareSettings.sharedWithUids,
        )
        assertNull(viewModel.uiState.value.sharingError)
        coVerify(exactly = 2) {
          sharing.setShareAudience(guide.id, ShareAudience.SELECTED_FRIENDS, setOf("friend-a"))
        }
      }

  @Test
  fun failedSharingWriteDuringFailedDeleteRestoresConfirmedPermissions() =
      runTest(dispatcher) {
        recordSharingWrites()
        val delete = recordDelete()
        val viewModel = loadedViewModel()

        viewModel.setShareAudience(guide.id, ShareAudience.SELECTED_FRIENDS, setOf("friend-a"))
        viewModel.deleteGuide(guide.id)
        runCurrent()
        sharingWrites.single().completeExceptionally(IllegalStateException("sharing failed"))
        runCurrent()

        assertNull(viewModel.uiState.value.sharingError)
        assertTrue(viewModel.uiState.value.guides.isEmpty())
        delete.completeExceptionally(IllegalStateException("delete failed"))
        advanceUntilIdle()

        assertEquals(
            emptySet<String>(),
            viewModel.uiState.value.guides.single().shareSettings.sharedWithUids,
        )
        assertNull(viewModel.uiState.value.sharingError)
      }

  @Test
  fun confirmedSharingDuringFailedDeleteIsRestoredWithTheGuide() =
      runTest(dispatcher) {
        recordSharingWrites()
        val delete = recordDelete()
        val viewModel = loadedViewModel()

        viewModel.setShareAudience(guide.id, ShareAudience.SELECTED_FRIENDS, setOf("friend-a"))
        viewModel.deleteGuide(guide.id)
        runCurrent()
        sharingWrites.single().complete(Unit)
        runCurrent()
        delete.completeExceptionally(IllegalStateException("delete failed"))
        advanceUntilIdle()

        assertEquals(
            setOf("friend-a"),
            viewModel.uiState.value.guides.single().shareSettings.sharedWithUids,
        )
        assertNull(viewModel.uiState.value.sharingError)
      }

  @Test
  fun sharingFailureAfterSuccessfulDeletionIsNotReported() =
      runTest(dispatcher) {
        recordSharingWrites()
        coEvery { repository.removeGuide(guide.id) } just Runs
        val viewModel = loadedViewModel()

        viewModel.setShareAudience(guide.id, ShareAudience.SELECTED_FRIENDS, setOf("friend-a"))
        viewModel.deleteGuide(guide.id)
        runCurrent()
        assertTrue(viewModel.uiState.value.guides.isEmpty())

        sharingWrites.single().completeExceptionally(IllegalStateException("guide deleted"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.guides.isEmpty())
        assertTrue(viewModel.uiState.value.sharingGuideIds.isEmpty())
        assertNull(viewModel.uiState.value.sharingError)
      }

  @Test
  fun clearSharingErrorPreservesOtherOperationErrors() =
      runTest(dispatcher) {
        val deleteError = IllegalStateException("delete failed")
        coEvery { sharing.setShareAudience(any(), any(), any()) } throws
            IllegalStateException("sharing failed")
        coEvery { repository.removeGuide(otherGuide.id) } throws deleteError
        snapshots.value = listOf(guide, otherGuide)
        val viewModel = loadedViewModel()
        viewModel.setShareAudience(guide.id, ShareAudience.SELECTED_FRIENDS, setOf("friend-a"))
        viewModel.deleteGuide(otherGuide.id)
        advanceUntilIdle()

        viewModel.clearSharingError()

        assertNull(viewModel.uiState.value.sharingError)
        assertEquals(deleteError, viewModel.uiState.value.deleteError)
      }

  @Test
  fun cancellationIsNotShownAsASharingError() =
      runTest(dispatcher) {
        coEvery { sharing.setShareAudience(any(), any(), any()) } throws
            CancellationException("cancelled")
        val viewModel = loadedViewModel()

        viewModel.setShareAudience(guide.id, ShareAudience.SELECTED_FRIENDS, setOf("friend-a"))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.sharingError)
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
        val viewModel = SavedGuidesViewModel(repository, sharing)
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
        // Only the latest request may report an error.
        assertNull(viewModel.uiState.value.favoriteError)
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
  fun favoriteFailureAfterTheGuideIsDeletedIsNotReported() =
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
        assertNull(viewModel.uiState.value.favoriteError)
      }

  @Test
  fun failedDeleteKeepsAPendingFavoriteAndItsRollback() =
      runTest(dispatcher) {
        recordFavoriteWrites()
        val delete = recordDelete()
        val viewModel = loadedViewModel()

        viewModel.setFavorite(guide.id, true)
        viewModel.deleteGuide(guide.id)
        runCurrent()
        delete.completeExceptionally(IllegalStateException("delete failed"))
        runCurrent()
        assertTrue(viewModel.uiState.value.guides.single().isFavorite)

        // A snapshot that predates the favourite does not flip the restored guide back.
        snapshots.value = listOf(guide, otherGuide)
        runCurrent()
        assertTrue(viewModel.uiState.value.guides.first { it.id == guide.id }.isFavorite)

        val error = IllegalStateException("favourite failed")
        favoriteWrites.single().completeExceptionally(error)
        runCurrent()
        assertFalse(viewModel.uiState.value.guides.first { it.id == guide.id }.isFavorite)
        assertEquals(error.message, viewModel.uiState.value.favoriteError?.message)
      }

  @Test
  fun favoriteFailingDuringAFailedDeleteRestoresTheConfirmedValue() =
      runTest(dispatcher) {
        recordFavoriteWrites()
        val delete = recordDelete()
        val viewModel = loadedViewModel()

        viewModel.setFavorite(guide.id, true)
        viewModel.deleteGuide(guide.id)
        runCurrent()
        favoriteWrites.single().completeExceptionally(IllegalStateException("favourite failed"))
        runCurrent()
        assertNull(viewModel.uiState.value.favoriteError)

        delete.completeExceptionally(IllegalStateException("delete failed"))
        runCurrent()
        assertFalse(viewModel.uiState.value.guides.single().isFavorite)
      }

  @Test
  fun olderFavoriteFailingDuringADeleteIsNotReported() =
      runTest(dispatcher) {
        recordFavoriteWrites()
        recordDelete()
        val viewModel = loadedViewModel()

        viewModel.setFavorite(guide.id, true)
        viewModel.setFavorite(guide.id, false)
        viewModel.deleteGuide(guide.id)
        runCurrent()
        favoriteWrites[0].completeExceptionally(IllegalStateException("older write failed"))
        runCurrent()

        assertNull(viewModel.uiState.value.favoriteError)
      }

  @Test
  fun favoriteConfirmedDuringAFailedDeleteIsKept() =
      runTest(dispatcher) {
        recordFavoriteWrites()
        val delete = recordDelete()
        val viewModel = loadedViewModel()

        viewModel.setFavorite(guide.id, true)
        viewModel.deleteGuide(guide.id)
        runCurrent()
        favoriteWrites.single().complete(Unit)
        runCurrent()
        delete.completeExceptionally(IllegalStateException("delete failed"))
        runCurrent()

        assertTrue(viewModel.uiState.value.guides.single().isFavorite)
        assertNull(viewModel.uiState.value.favoriteError)
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
  fun clearFavoriteErrorKeepsOtherErrors() =
      runTest(dispatcher) {
        val deleteError = IllegalStateException("delete failed")
        coEvery { repository.setFavorite(any(), any()) } throws IllegalStateException("failed")
        coEvery { repository.removeGuide(otherGuide.id) } throws deleteError
        snapshots.value = listOf(guide, otherGuide)
        val viewModel = loadedViewModel()
        viewModel.setFavorite(guide.id, true)
        viewModel.deleteGuide(otherGuide.id)
        advanceUntilIdle()

        viewModel.clearFavoriteError()

        assertNull(viewModel.uiState.value.favoriteError)
        assertEquals(deleteError, viewModel.uiState.value.deleteError)
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

  @Test
  fun favouritesFilterIsOffByDefaultAndCanBeSwitched() =
      runTest(dispatcher) {
        val viewModel = loadedViewModel()
        assertFalse(viewModel.uiState.value.showFavoritesOnly)

        viewModel.setShowFavoritesOnly(true)
        assertTrue(viewModel.uiState.value.showFavoritesOnly)

        viewModel.setShowFavoritesOnly(false)
        assertFalse(viewModel.uiState.value.showFavoritesOnly)
      }

  @Test
  fun favouritesFilterKeepsEveryGuideInState() =
      runTest(dispatcher) {
        coEvery { repository.setFavorite(any(), any()) } just Runs
        snapshots.value = listOf(guide, otherGuide)
        val viewModel = loadedViewModel()

        viewModel.setShowFavoritesOnly(true)
        viewModel.toggleFavorite(guide.id)
        advanceUntilIdle()

        assertEquals(listOf(true, false), viewModel.uiState.value.guides.map { it.isFavorite })
        assertTrue(viewModel.uiState.value.showFavoritesOnly)
      }

  private fun TestScope.loadedViewModel(): SavedGuidesViewModel {
    val viewModel = SavedGuidesViewModel(repository, sharing)
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

  private fun recordSharingWrites() {
    coEvery { sharing.setShareAudience(any(), any(), any()) } coAnswers
        {
          CompletableDeferred<Unit>().also { sharingWrites += it }.await()
        }
  }

  /** Makes the deletion of [guide] wait until the returned result is completed. */
  private fun recordDelete(): CompletableDeferred<Unit> {
    val delete = CompletableDeferred<Unit>()
    coEvery { repository.removeGuide(guide.id) } coAnswers { delete.await() }
    return delete
  }
}
