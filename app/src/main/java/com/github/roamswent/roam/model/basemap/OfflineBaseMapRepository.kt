// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.basemap

/** Provides the low-resolution world map drawn under the pins when the device is offline. */
interface OfflineBaseMapRepository {
  /**
   * Returns the country outlines of the world, without Antarctica. Works without a network.
   *
   * @throws Exception if the bundled map cannot be read or parsed.
   */
  suspend fun countries(): List<BaseMapPolygon>
}
