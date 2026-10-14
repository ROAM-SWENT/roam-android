package com.github.roamswent.roam.model.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Fetches the device's current position through Google Play Services. */
class AndroidLocationProvider(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context),
) : LocationProvider {

  override suspend fun currentCoordinates(): Coordinates? {
    if (!hasLocationPermission()) return null
    return withTimeoutOrNull(LOCATION_TIMEOUT_MS) { fetchLocation() }
  }

  @SuppressLint("MissingPermission") // Guarded by hasLocationPermission() above.
  private suspend fun fetchLocation(): Coordinates? {
    val cancellationSource = CancellationTokenSource()
    return suspendCancellableCoroutine { continuation ->
      continuation.invokeOnCancellation { cancellationSource.cancel() }
      fusedLocationClient
          .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationSource.token)
          .addOnSuccessListener { location ->
            if (continuation.isActive) {
              continuation.resume(
                  location?.let { Coordinates(it.latitude, it.longitude) },
                  onCancellation = null,
              )
            }
          }
          .addOnFailureListener {
            if (continuation.isActive) continuation.resume(null, onCancellation = null)
          }
          .addOnCanceledListener {
            if (continuation.isActive) continuation.resume(null, onCancellation = null)
          }
    }
  }

  private fun hasLocationPermission(): Boolean =
      hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ||
          hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

  private fun hasPermission(permission: String): Boolean =
      ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

  private companion object {
    const val LOCATION_TIMEOUT_MS = 5_000L
  }
}
