// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.guide.MonumentSharingRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FriendCollectionViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val repository = mockk<MonumentSharingRepository>()
  private val guide =
      Guide(
          id = "g1",
          photoBytes = byteArrayOf(1),
          monumentName = "Louvre",
          confidence = 0.9,
          history = "",
          facts = emptyList(),
          culture = "",
      )

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  @Test
  fun load_exposesGuidesSharedByOwner() =
      runTest(dispatcher) {
        every { repository.observeSharedGuidesFrom("friend") } returns flowOf(listOf(guide))
        val viewModel = FriendCollectionViewModel(repository, "friend")

        viewModel.load()
        advanceUntilIdle()

        assertEquals(listOf(guide), viewModel.uiState.value.guides)
        assertFalse(viewModel.uiState.value.isLoadingGuides)
      }

  @Test
  fun load_reportsFailure() =
      runTest(dispatcher) {
        every { repository.observeSharedGuidesFrom("friend") } returns
            flow { throw IllegalStateException("boom") }
        val viewModel = FriendCollectionViewModel(repository, "friend")

        viewModel.load()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.guidesError)
        assertFalse(viewModel.uiState.value.isLoadingGuides)
      }

  @Test
  fun onSearchQueryChange_updatesQuery() {
    val viewModel = FriendCollectionViewModel(repository, "friend")

    viewModel.onSearchQueryChange("lou")

    assertEquals("lou", viewModel.uiState.value.searchQuery)
  }

  @Test
  fun load_whileAlreadyLoading_subscribesOnlyOnce() =
      runTest(dispatcher) {
        var subscriptions = 0
        every { repository.observeSharedGuidesFrom("friend") } answers
            {
              subscriptions++
              flow {
                emit(listOf(guide))
                kotlinx.coroutines.awaitCancellation()
              }
            }
        val viewModel = FriendCollectionViewModel(repository, "friend")

        viewModel.load()
        viewModel.load()
        advanceUntilIdle()

        assertEquals(1, subscriptions)
      }
}
