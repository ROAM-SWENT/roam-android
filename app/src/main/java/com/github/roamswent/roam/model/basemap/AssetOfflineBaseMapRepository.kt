// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.basemap

import android.content.Context
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Reads the country outlines bundled in the APK. The data is Natural Earth 1:110m Admin 0 countries
 * (public domain, naturalearthdata.com), reduced to its geometry, rounded to 0.01 degree and
 * without Antarctica, whose ring along the pole and the antimeridian Google Maps cannot fill.
 *
 * The file is parsed once, off the main thread, and kept in memory afterwards.
 */
class AssetOfflineBaseMapRepository(
    private val openAsset: () -> InputStream,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : OfflineBaseMapRepository {

  constructor(context: Context) : this({ context.applicationContext.assets.open(ASSET_NAME) })

  private val mutex = Mutex()
  private var cached: List<BaseMapPolygon>? = null

  override suspend fun countries(): List<BaseMapPolygon> = mutex.withLock {
    cached
        ?: withContext(ioDispatcher) {
              val geoJson = openAsset().bufferedReader().use { it.readText() }
              BaseMapGeoJsonParser.parse(geoJson)
            }
            .also { cached = it }
  }

  companion object {
    /** Name of the bundled GeoJSON in `src/main/assets`. */
    const val ASSET_NAME = "offline_world_countries.geojson"
  }
}
