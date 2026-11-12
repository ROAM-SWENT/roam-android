// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.explore

import com.github.roamswent.roam.model.guide.FakeSavedGuideRepository
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.model.location.LocationProvider
import com.github.roamswent.roam.model.permission.LocationPermissionDecision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Tests the saved monuments that [ExploreViewModel] exposes for the map markers. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelMonumentsTest {
  private val dispatcher = StandardTestDispatcher()
  private val eiffelLocation = Coordinates(48.85837, 2.29448)
  private val colosseumLocation = Coordinates(41.89021, 12.49223)
  private val eiffel = guide("guide-1", "Eiffel Tower", eiffelLocation)
  private val colosseum = guide("guide-2", "Colosseum", colosseumLocation)
  private val unlocated = guide("guide-3", "Big Ben", location = null)

  @Before
  fun setMainDispatcher() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun resetMainDispatcher() {
    Dispatchers.resetMain()
  }

  /** Verifies that a guide with a location becomes a monument with its id, name and location. */
  @Test
  fun locatedGuideIsExposedAsMonument() =
      runTest(dispatcher) {
        val viewModel = viewModel(FakeSavedGuideRepository(listOf(eiffel)))
        observe(viewModel)

        assertEquals(
            listOf(ExploreMonument("guide-1", "Eiffel Tower", eiffelLocation)),
            viewModel.monuments.value,
        )
      }

  /** Verifies that a guide without a location stays off the map. */
  @Test
  fun unlocatedGuideIsNotExposed() =
      runTest(dispatcher) {
        val viewModel = viewModel(FakeSavedGuideRepository(listOf(unlocated)))
        observe(viewModel)

        assertEquals(emptyList<ExploreMonument>(), viewModel.monuments.value)
      }

  /** Verifies that only located guides are exposed, in collection order, one per guide. */
  @Test
  fun onlyLocatedGuidesAreExposedFromMixedCollection() =
      runTest(dispatcher) {
        val viewModel = viewModel(FakeSavedGuideRepository(listOf(eiffel, unlocated, colosseum)))
        observe(viewModel)

        assertEquals(
            listOf(
                ExploreMonument("guide-1", "Eiffel Tower", eiffelLocation),
                ExploreMonument("guide-2", "Colosseum", colosseumLocation),
            ),
            viewModel.monuments.value,
        )
      }

  /** Verifies that two monuments at the same place still produce two separate monuments. */
  @Test
  fun multipleLocatedGuidesProduceOneMonumentEach() =
      runTest(dispatcher) {
        val sameSpot = colosseum.copy(location = eiffelLocation)
        val viewModel = viewModel(FakeSavedGuideRepository(listOf(eiffel, sameSpot)))
        observe(viewModel)

        assertEquals(listOf("guide-1", "guide-2"), viewModel.monuments.value.map { it.guideId })
      }

  /** Verifies that an empty collection exposes no monuments. */
  @Test
  fun emptyCollectionExposesNoMonuments() =
      runTest(dispatcher) {
        val viewModel = viewModel(FakeSavedGuideRepository())
        observe(viewModel)

        assertEquals(emptyList<ExploreMonument>(), viewModel.monuments.value)
      }

  /** Verifies that a repository update reaches the monuments without recreating the view model. */
  @Test
  fun repositoryUpdatesUpdateMonuments() =
      runTest(dispatcher) {
        val repository = FakeSavedGuideRepository(listOf(eiffel))
        val viewModel = viewModel(repository)
        observe(viewModel)

        repository.addGuide(colosseum)
        runCurrent()
        assertEquals(listOf("guide-1", "guide-2"), viewModel.monuments.value.map { it.guideId })

        repository.removeGuide("guide-1")
        runCurrent()
        assertEquals(listOf("guide-2"), viewModel.monuments.value.map { it.guideId })
      }

  /** Verifies that giving a location to an unlocated guide adds its monument. */
  @Test
  fun locationAddedToGuideShowsMonument() =
      runTest(dispatcher) {
        val repository = FakeSavedGuideRepository(listOf(unlocated))
        val viewModel = viewModel(repository)
        observe(viewModel)

        repository.setLocation("guide-3", eiffelLocation)
        runCurrent()

        assertEquals(
            listOf(ExploreMonument("guide-3", "Big Ben", eiffelLocation)),
            viewModel.monuments.value,
        )
      }

  /** Verifies that removing a guide's location removes its monument. */
  @Test
  fun locationRemovedFromGuideHidesMonument() =
      runTest(dispatcher) {
        val repository = FakeSavedGuideRepository(listOf(eiffel, colosseum))
        val viewModel = viewModel(repository)
        observe(viewModel)

        repository.setLocation("guide-1", null)
        runCurrent()

        assertEquals(listOf("guide-2"), viewModel.monuments.value.map { it.guideId })
      }

  /** Verifies that changes irrelevant to the map, like a favourite, do not emit new monuments. */
  @Test
  fun unrelatedGuideChangeDoesNotEmitAgain() =
      runTest(dispatcher) {
        val repository = FakeSavedGuideRepository(listOf(eiffel))
        val viewModel = viewModel(repository)
        val emissions = observe(viewModel)
        val emissionsBefore = emissions.size

        repository.setFavorite("guide-1", true)
        runCurrent()

        assertEquals(emissionsBefore, emissions.size)
        assertEquals(listOf("guide-1"), emissions.last().map { it.guideId })
      }

  /** Verifies that a failing observation leaves the map without monuments instead of crashing. */
  @Test
  fun observationFailureExposesNoMonuments() =
      runTest(dispatcher) {
        val repository =
            FakeSavedGuideRepository(listOf(eiffel)).apply {
              loadError = IllegalStateException("offline")
            }
        val viewModel = viewModel(repository)
        observe(viewModel)

        assertEquals(emptyList<ExploreMonument>(), viewModel.monuments.value)
      }

  /** Verifies that observing monuments leaves the current-location state flow working. */
  @Test
  fun monumentsDoNotAffectCurrentLocationState() =
      runTest(dispatcher) {
        val viewModel = viewModel(FakeSavedGuideRepository(listOf(eiffel)), colosseumLocation)
        observe(viewModel)
        assertEquals(
            ExploreUiState.PermissionRequired(LocationPermissionDecision.NOT_ASKED),
            viewModel.uiState.value,
        )

        viewModel.onPermissionStateChanged(true, LocationPermissionDecision.GRANTED)
        runCurrent()

        assertEquals(ExploreUiState.LocationAvailable(colosseumLocation), viewModel.uiState.value)
        assertEquals(listOf("guide-1"), viewModel.monuments.value.map { it.guideId })
      }

  private fun viewModel(
      repository: FakeSavedGuideRepository,
      userLocation: Coordinates? = null,
  ) =
      ExploreViewModel(
          locationProvider =
              object : LocationProvider {
                override suspend fun currentCoordinates() = userLocation
              },
          savedGuideRepository = repository,
      )

  /** Subscribes like the map does and returns every emitted monument list. */
  private fun TestScope.observe(viewModel: ExploreViewModel): List<List<ExploreMonument>> {
    val emissions = mutableListOf<List<ExploreMonument>>()
    backgroundScope.launch { viewModel.monuments.collect { emissions += it } }
    runCurrent()
    return emissions
  }

  private fun guide(id: String, name: String, location: Coordinates?) =
      Guide(
          id = id,
          photoBytes = byteArrayOf(1, 2, 3),
          monumentName = name,
          confidence = 0.9,
          history = "History.",
          facts = listOf("Fact."),
          culture = "Culture.",
          location = location,
      )
}
