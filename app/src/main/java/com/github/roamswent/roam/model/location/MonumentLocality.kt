// Co-authored-by: Codex <codex@openai.com>
package com.github.roamswent.roam.model.location

import android.content.Context
import android.location.Geocoder
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class MonumentLocality(val city: String?, val country: String?)

@Suppress("DEPRECATION") // The blocking API also supports Android versions below 33.
internal suspend fun resolveMonumentLocality(
    context: Context,
    coordinates: Coordinates,
): MonumentLocality? =
    withContext(Dispatchers.IO) {
      if (!Geocoder.isPresent()) return@withContext null
      val approximate = coordinates.coarsened()
      try {
        Geocoder(context)
            .getFromLocation(approximate.latitude, approximate.longitude, MAX_ADDRESS_RESULTS)
            ?.firstOrNull()
            ?.let { MonumentLocality(it.locality, it.countryName) }
      } catch (_: IOException) {
        null
      } catch (_: IllegalArgumentException) {
        null
      }
    }

private const val MAX_ADDRESS_RESULTS = 1
