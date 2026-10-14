package com.github.roamswent.roam.model.location

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidLocationProviderTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun returnsNullWhenPermissionIsNotGranted() = runTest {
    val fusedClient: FusedLocationProviderClient = mockk()
    val provider = AndroidLocationProvider(context, fusedClient)

    assertNull(provider.currentCoordinates())
  }

  @Test
  fun returnsCoordinatesWhenPermissionGrantedAndFixSucceeds() = runTest {
    grantLocationPermission()
    val location = fakeLocation(latitude = 48.8584, longitude = 2.2945)
    val fusedClient = fusedClientReturning(success = location)
    val provider = AndroidLocationProvider(context, fusedClient)

    assertEquals(Coordinates(48.8584, 2.2945), provider.currentCoordinates())
  }

  @Test
  fun returnsNullWhenPermissionGrantedButFixFails() = runTest {
    grantLocationPermission()
    val fusedClient = fusedClientReturning(failure = Exception("no fix"))
    val provider = AndroidLocationProvider(context, fusedClient)

    assertNull(provider.currentCoordinates())
  }

  @Test
  fun returnsNullWhenPermissionGrantedButLocationIsNull() = runTest {
    grantLocationPermission()
    val fusedClient = fusedClientReturning(success = null)
    val provider = AndroidLocationProvider(context, fusedClient)

    assertNull(provider.currentCoordinates())
  }

  @Test
  fun returnsNullAndCancelsTheFetchWhenTheFixNeverCompletes() = runTest {
    grantLocationPermission()
    val tokenSlot = slot<CancellationToken>()
    val fusedClient = fusedClientThatNeverCompletes(tokenSlot)
    val provider = AndroidLocationProvider(context, fusedClient)

    assertNull(provider.currentCoordinates())
    assertTrue(tokenSlot.captured.isCancellationRequested)
  }

  private fun grantLocationPermission() {
    shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
  }

  private fun fakeLocation(latitude: Double, longitude: Double): Location =
      Location("test").apply {
        this.latitude = latitude
        this.longitude = longitude
      }

  private fun fusedClientReturning(
      success: Location? = null,
      failure: Exception? = null,
  ): FusedLocationProviderClient {
    val task: Task<Location> = mockk()
    every { task.addOnSuccessListener(any()) } answers
        {
          if (failure == null) firstArg<OnSuccessListener<Location>>().onSuccess(success)
          task
        }
    every { task.addOnFailureListener(any()) } answers
        {
          if (failure != null) firstArg<OnFailureListener>().onFailure(failure)
          task
        }
    every { task.addOnCanceledListener(any()) } returns task

    val fusedClient: FusedLocationProviderClient = mockk()
    every { fusedClient.getCurrentLocation(any<Int>(), any()) } returns task
    return fusedClient
  }

  /** Registers listeners but never invokes any of them, as if the fix never arrives. */
  private fun fusedClientThatNeverCompletes(
      tokenSlot: CapturingSlot<CancellationToken>
  ): FusedLocationProviderClient {
    val task: Task<Location> = mockk()
    every { task.addOnSuccessListener(any()) } returns task
    every { task.addOnFailureListener(any()) } returns task
    every { task.addOnCanceledListener(any()) } returns task

    val fusedClient: FusedLocationProviderClient = mockk()
    every { fusedClient.getCurrentLocation(any<Int>(), capture(tokenSlot)) } returns task
    return fusedClient
  }
}
