// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Codex <codex@openai.com>
package com.github.roamswent.roam.ui.explore

import com.github.roamswent.roam.model.basemap.BaseMapPolygon
import com.github.roamswent.roam.model.basemap.FakeOfflineBaseMapRepository
import com.github.roamswent.roam.model.guide.FakeSavedGuideRepository
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.model.location.LocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.mockito.kotlin.mock

/** Tests when Explore loads the offline base map and what it keeps on failure. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelOfflineBaseMapTest {
  private val dispatcher = StandardTestDispatcher()
  private val country =
      BaseMapPolygon(
          outline =
              listOf(
                  Coordinates(0.0, 0.0),
                  Coordinates(0.0, 10.0),
                  Coordinates(5.0, 10.0),
                  Coordinates(0.0, 0.0),
              )
      )

  @Before
  fun setMainDispatcher() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun resetMainDispatcher() {
    Dispatchers.resetMain()
  }

  private fun viewModel(repository: FakeOfflineBaseMapRepository) =
      ExploreViewModel(
          locationProvider =
              object : LocationProvider {
                override suspend fun currentCoordinates(): Coordinates? = null
              },
          savedGuideRepository = FakeSavedGuideRepository(),
          offlineBaseMapRepository = repository,
          friendRepository = mock(),
          publicProfileRepository = mock(),
          monumentSharingRepository = mock(),
      )

  @Test
  fun baseMapIsNotLoadedUntilRequested() =
      runTest(dispatcher) {
        val repository = FakeOfflineBaseMapRepository(countries = listOf(country))
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        assertEquals(0, repository.calls)
        assertTrue(viewModel.offlineBaseMap.value.isEmpty())
      }

  @Test
  fun requestedBaseMapIsExposedAndLoadedOnlyOnce() =
      runTest(dispatcher) {
        val repository = FakeOfflineBaseMapRepository(countries = listOf(country))
        val viewModel = viewModel(repository)

        viewModel.loadOfflineBaseMap()
        viewModel.loadOfflineBaseMap()
        advanceUntilIdle()
        viewModel.loadOfflineBaseMap()
        advanceUntilIdle()

        assertEquals(listOf(country), viewModel.offlineBaseMap.value)
        assertEquals(1, repository.calls)
      }

  @Test
  fun failedLoadLeavesAnEmptyMapAndIsRetriedOnTheNextRequest() =
      runTest(dispatcher) {
        val repository = FakeOfflineBaseMapRepository(failure = IllegalStateException("broken"))
        val viewModel = viewModel(repository)

        viewModel.loadOfflineBaseMap()
        advanceUntilIdle()
        assertTrue(viewModel.offlineBaseMap.value.isEmpty())

        repository.failure = null
        repository.countries = listOf(country)
        viewModel.loadOfflineBaseMap()
        advanceUntilIdle()

        assertEquals(listOf(country), viewModel.offlineBaseMap.value)
        assertEquals(2, repository.calls)
      }
}
