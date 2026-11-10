/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 * Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
 */

package com.github.roamswent.roam.ui.explore

import com.github.roamswent.roam.model.guide.FakeSavedGuideRepository
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.model.location.LocationProvider
import com.github.roamswent.roam.model.permission.LocationPermissionDecision
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setMainDispatcher() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun resetMainDispatcher() {
    Dispatchers.resetMain()
  }

  /** Verifies that the initial state requires location permission that has not been requested. */
  @Test
  fun initialStateRequiresPermissionNotYetAsked() =
      runTest(dispatcher) {
        val provider = FakeLocationProvider { null }
        val viewModel = ExploreViewModel(provider, FakeSavedGuideRepository())

        assertEquals(
            ExploreUiState.PermissionRequired(LocationPermissionDecision.NOT_ASKED),
            viewModel.uiState.value,
        )
        assertEquals(0, provider.calls)
      }

  /** Verifies that a not-yet-asked permission snapshot restores the permission-required state. */
  @Test
  fun notAskedPermissionSnapshotRequiresPermission() =
      runTest(dispatcher) {
        val viewModel = ExploreViewModel(FakeLocationProvider { null }, FakeSavedGuideRepository())

        viewModel.onPermissionStateChanged(
            isGranted = false,
            decision = LocationPermissionDecision.DENIED,
        )
        assertEquals(
            ExploreUiState.PermissionRequired(LocationPermissionDecision.DENIED),
            viewModel.uiState.value,
        )

        viewModel.onPermissionStateChanged(
            isGranted = false,
            decision = LocationPermissionDecision.NOT_ASKED,
        )

        assertEquals(
            ExploreUiState.PermissionRequired(LocationPermissionDecision.NOT_ASKED),
            viewModel.uiState.value,
        )
      }

  /** Verifies that a denied permission snapshot exposes the permission-required state. */
  @Test
  fun deniedPermissionSnapshotRequiresPermission() =
      runTest(dispatcher) {
        val viewModel = ExploreViewModel(FakeLocationProvider { null }, FakeSavedGuideRepository())

        viewModel.onPermissionStateChanged(
            isGranted = false,
            decision = LocationPermissionDecision.DENIED,
        )

        assertEquals(
            ExploreUiState.PermissionRequired(LocationPermissionDecision.DENIED),
            viewModel.uiState.value,
        )
      }

  /** Verifies that granted permission loads and exposes the returned coordinates. */
  @Test
  fun grantedPermissionLoadsAndExposesCoordinates() =
      runTest(dispatcher) {
        val coordinates = Coordinates(48.85837, 2.29448)
        val result = CompletableDeferred<Coordinates?>()
        val provider = FakeLocationProvider { result.await() }
        val viewModel = ExploreViewModel(provider, FakeSavedGuideRepository())

        viewModel.onPermissionStateChanged(true, LocationPermissionDecision.GRANTED)
        runCurrent()

        assertEquals(ExploreUiState.Loading, viewModel.uiState.value)
        assertEquals(1, provider.calls)

        result.complete(coordinates)
        advanceUntilIdle()

        assertEquals(ExploreUiState.LocationAvailable(coordinates), viewModel.uiState.value)
      }

  /** Verifies that a null location result exposes the unavailable state. */
  @Test
  fun nullCoordinatesExposeUnavailableState() =
      runTest(dispatcher) {
        val result = CompletableDeferred<Coordinates?>()
        val provider = FakeLocationProvider { result.await() }
        val viewModel = ExploreViewModel(provider, FakeSavedGuideRepository())

        viewModel.onPermissionStateChanged(true, LocationPermissionDecision.GRANTED)
        runCurrent()
        assertEquals(ExploreUiState.Loading, viewModel.uiState.value)

        result.complete(null)
        advanceUntilIdle()

        assertEquals(ExploreUiState.LocationUnavailable, viewModel.uiState.value)
      }

  /** Verifies that granting permission after it was not granted starts location retrieval. */
  @Test
  fun transitionFromNotGrantedToGrantedStartsRetrieval() =
      runTest(dispatcher) {
        val provider = FakeLocationProvider { null }
        val viewModel = ExploreViewModel(provider, FakeSavedGuideRepository())
        viewModel.onPermissionStateChanged(false, LocationPermissionDecision.NOT_ASKED)

        viewModel.onPermissionStateChanged(true, LocationPermissionDecision.GRANTED)
        advanceUntilIdle()

        assertEquals(1, provider.calls)
        assertEquals(ExploreUiState.LocationUnavailable, viewModel.uiState.value)
      }

  /** Verifies that a repeated granted snapshot does not start another location retrieval. */
  @Test
  fun repeatedGrantedSnapshotDoesNotStartAnotherRetrieval() =
      runTest(dispatcher) {
        val result = CompletableDeferred<Coordinates?>()
        val provider = FakeLocationProvider { result.await() }
        val viewModel = ExploreViewModel(provider, FakeSavedGuideRepository())

        viewModel.onPermissionStateChanged(true, LocationPermissionDecision.GRANTED)
        runCurrent()
        viewModel.onPermissionStateChanged(true, LocationPermissionDecision.GRANTED)
        advanceUntilIdle()

        assertEquals(1, provider.calls)
        assertEquals(ExploreUiState.Loading, viewModel.uiState.value)
      }

  /** Verifies that a stale request result cannot replace the result of a retry. */
  @Test
  fun staleRequestResultDoesNotReplaceRetryResult() =
      runTest(dispatcher) {
        val pendingContinuations = mutableListOf<Continuation<Coordinates?>>()
        val provider =
            object : LocationProvider {
              var calls = 0

              override suspend fun currentCoordinates(): Coordinates? {
                calls++
                return suspendCoroutine { continuation -> pendingContinuations += continuation }
              }
            }
        val viewModel = ExploreViewModel(provider, FakeSavedGuideRepository())
        val coordinatesA = Coordinates(48.85837, 2.29448)
        val coordinatesB = Coordinates(40.68925, -74.0445)

        viewModel.onPermissionStateChanged(true, LocationPermissionDecision.GRANTED)
        runCurrent()
        assertEquals(1, provider.calls)

        viewModel.retryLocation()
        runCurrent()
        assertEquals(2, provider.calls)
        assertEquals(ExploreUiState.Loading, viewModel.uiState.value)

        pendingContinuations[0].resume(coordinatesA)
        runCurrent()
        assertEquals(ExploreUiState.Loading, viewModel.uiState.value)

        pendingContinuations[1].resume(coordinatesB)
        advanceUntilIdle()

        assertEquals(ExploreUiState.LocationAvailable(coordinatesB), viewModel.uiState.value)
      }

  /** Verifies that a stale location result cannot replace the state after permission is revoked. */
  @Test
  fun revokedPermissionPreventsStaleLocationFromReplacingPermissionState() =
      runTest(dispatcher) {
        var pendingContinuation: Continuation<Coordinates?>? = null
        val provider =
            object : LocationProvider {
              var calls = 0

              override suspend fun currentCoordinates(): Coordinates? {
                calls++
                return suspendCoroutine { continuation -> pendingContinuation = continuation }
              }
            }
        val viewModel = ExploreViewModel(provider, FakeSavedGuideRepository())

        viewModel.onPermissionStateChanged(true, LocationPermissionDecision.GRANTED)
        runCurrent()
        assertEquals(ExploreUiState.Loading, viewModel.uiState.value)
        assertTrue(pendingContinuation != null)

        viewModel.onPermissionStateChanged(false, LocationPermissionDecision.DENIED)
        assertEquals(
            ExploreUiState.PermissionRequired(LocationPermissionDecision.DENIED),
            viewModel.uiState.value,
        )

        pendingContinuation!!.resume(Coordinates(48.85837, 2.29448))
        advanceUntilIdle()

        assertEquals(
            ExploreUiState.PermissionRequired(LocationPermissionDecision.DENIED),
            viewModel.uiState.value,
        )
      }

  /** Verifies that retrying after an unavailable result starts another location retrieval. */
  @Test
  fun retryAfterUnavailableStartsAnotherRetrieval() =
      runTest(dispatcher) {
        val provider = FakeLocationProvider { null }
        val viewModel = ExploreViewModel(provider, FakeSavedGuideRepository())
        viewModel.onPermissionStateChanged(true, LocationPermissionDecision.GRANTED)
        advanceUntilIdle()
        assertEquals(ExploreUiState.LocationUnavailable, viewModel.uiState.value)

        viewModel.retryLocation()
        advanceUntilIdle()

        assertEquals(2, provider.calls)
        assertEquals(ExploreUiState.LocationUnavailable, viewModel.uiState.value)
      }

  /** Verifies that retrying without granted permission does not request a location. */
  @Test
  fun retryDoesNothingWhenPermissionIsNotGranted() =
      runTest(dispatcher) {
        val provider = FakeLocationProvider { null }
        val viewModel = ExploreViewModel(provider, FakeSavedGuideRepository())
        viewModel.onPermissionStateChanged(false, LocationPermissionDecision.DENIED)

        viewModel.retryLocation()
        advanceUntilIdle()

        assertEquals(0, provider.calls)
        assertEquals(
            ExploreUiState.PermissionRequired(LocationPermissionDecision.DENIED),
            viewModel.uiState.value,
        )
      }

  private class FakeLocationProvider(private val coordinates: suspend () -> Coordinates?) :
      LocationProvider {
    var calls = 0
      private set

    override suspend fun currentCoordinates(): Coordinates? {
      calls++
      return coordinates()
    }
  }
}
