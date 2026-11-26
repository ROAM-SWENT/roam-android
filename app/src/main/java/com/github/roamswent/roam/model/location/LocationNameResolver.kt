// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.model.location

import android.content.Context
import android.location.Geocoder
import android.util.Log
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocationName(val city: String, val country: String)

fun interface LocationNameResolver {
  suspend fun resolve(coordinates: Coordinates): LocationName?
}

class AndroidLocationNameResolver internal constructor(private val geocoder: Geocoder) :
    LocationNameResolver {
  constructor(context: Context) : this(Geocoder(context.applicationContext, Locale.getDefault()))

  @Suppress("DEPRECATION")
  override suspend fun resolve(coordinates: Coordinates): LocationName? =
      withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        try {
          val address =
              geocoder
                  .getFromLocation(coordinates.latitude, coordinates.longitude, 1)
                  ?.firstOrNull() ?: return@withContext null
          val city = address.locality?.takeIf { it.isNotBlank() } ?: address.subAdminArea.orEmpty()
          val country = address.countryName.orEmpty()
          if (city.isBlank() && country.isBlank()) null else LocationName(city, country)
        } catch (exception: IOException) {
          Log.w("LocationNameResolver", "Unable to resolve monument coordinates", exception)
          null
        }
      }
}
