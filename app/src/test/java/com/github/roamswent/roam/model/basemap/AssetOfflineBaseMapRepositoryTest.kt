// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.basemap

import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssetOfflineBaseMapRepositoryTest {
  private val square =
      """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":
        {"type":"Polygon","coordinates":[[[0,0],[10,0],[10,5],[0,5],[0,0]]]}}]}"""

  @Test
  fun assetIsParsedOnceAndThenServedFromMemory() = runTest {
    var opens = 0
    val repository =
        AssetOfflineBaseMapRepository(
            openAsset = {
              opens++
              ByteArrayInputStream(square.toByteArray())
            },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

    val first = repository.countries()
    val second = repository.countries()

    assertEquals(1, first.size)
    assertSame(first, second)
    assertEquals(1, opens)
  }

  @Test
  fun aFailedReadIsRetriedOnTheNextCall() = runTest {
    var opens = 0
    val repository =
        AssetOfflineBaseMapRepository(
            openAsset = {
              opens++
              if (opens == 1) error("disk error")
              ByteArrayInputStream(square.toByteArray())
            },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

    runCatching { repository.countries() }

    assertEquals(1, repository.countries().size)
    assertEquals(2, opens)
  }

  /** Checks the file shipped in the APK: it parses, covers the world and leaves out Antarctica. */
  @Test
  fun bundledWorldMapParsesIntoCountriesWithoutAntarctica() = runTest {
    val asset = File("src/main/assets/${AssetOfflineBaseMapRepository.ASSET_NAME}")
    val repository =
        AssetOfflineBaseMapRepository(
            openAsset = { asset.inputStream() },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

    val countries = repository.countries()
    val points = countries.flatMap { it.outline }

    assertTrue("expected the world's countries, got ${countries.size}", countries.size > 150)
    assertTrue(points.minOf { it.latitude } > -60.0)
    assertTrue(points.maxOf { it.latitude } > 70.0)
    assertTrue(points.minOf { it.longitude } < -170.0)
    assertTrue(points.maxOf { it.longitude } > 170.0)
  }
}
