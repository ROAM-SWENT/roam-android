// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.basemap

/** Returns [countries], or throws [failure] when set, and counts the calls. */
class FakeOfflineBaseMapRepository(
    var countries: List<BaseMapPolygon> = emptyList(),
    var failure: Exception? = null,
) : OfflineBaseMapRepository {
  var calls = 0
    private set

  override suspend fun countries(): List<BaseMapPolygon> {
    calls++
    failure?.let { throw it }
    return countries
  }
}
