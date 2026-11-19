// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.location

import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExifLocationReaderTest {

  @get:Rule val tempFolder = TemporaryFolder()

  /** EXIF stores coordinates as rationals, so a round trip is only this precise. */
  private val tolerance = 1e-5

  private fun fixture(name: String): ByteArray =
      javaClass.getResourceAsStream("/fixtures/$name")!!.use { it.readBytes() }

  /** A real JPEG whose EXIF metadata is set by [configure]. */
  private fun jpegWith(configure: ExifInterface.() -> Unit): ByteArray {
    val file = tempFolder.newFile("photo.jpg")
    file.writeBytes(fixture("eiffel_tiny_32px.jpg"))
    ExifInterface(file).apply {
      configure()
      saveAttributes()
    }
    return file.readBytes()
  }

  private fun assertLocation(expected: Coordinates, actual: Coordinates?) {
    assertNotNull(actual)
    assertEquals(expected.latitude, actual!!.latitude, tolerance)
    assertEquals(expected.longitude, actual.longitude, tolerance)
  }

  @Test
  fun readsNorthEastGpsLocation() {
    val bytes = jpegWith { setLatLong(48.8584, 2.2945) }

    assertLocation(Coordinates(48.8584, 2.2945), ExifLocationReader.read(bytes))
  }

  @Test
  fun readsSouthWestGpsLocationAsNegativeValues() {
    val bytes = jpegWith { setLatLong(-22.9519, -43.2105) }

    assertLocation(Coordinates(-22.9519, -43.2105), ExifLocationReader.read(bytes))
  }

  @Test
  fun photoWithoutGpsHasNoLocation() {
    assertNull(ExifLocationReader.read(fixture("eiffel_tiny_32px.jpg")))
  }

  @Test
  fun exifWithoutGpsTagsHasNoLocation() {
    val bytes = jpegWith { setAttribute(ExifInterface.TAG_MAKE, "Roam") }

    assertNull(ExifLocationReader.read(bytes))
  }

  @Test
  fun zeroZeroPlaceholderHasNoLocation() {
    val bytes = jpegWith { setLatLong(0.0, 0.0) }

    assertNull(ExifLocationReader.read(bytes))
  }

  @Test
  fun outOfRangeGpsHasNoLocation() {
    val bytes = jpegWith {
      setAttribute(ExifInterface.TAG_GPS_LATITUDE, "100/1,0/1,0/1")
      setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
      setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "2/1,0/1,0/1")
      setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
    }

    assertNull(ExifLocationReader.read(bytes))
  }

  @Test
  fun photoInAnotherFormatWithoutGpsHasNoLocation() {
    assertNull(ExifLocationReader.read(fixture("eiffel_lossy_330x611.webp")))
  }

  @Test
  fun garbageBytesHaveNoLocationAndDoNotThrow() {
    assertNull(ExifLocationReader.read("not a photo".toByteArray()))
  }

  @Test
  fun emptyBytesHaveNoLocationAndDoNotThrow() {
    assertNull(ExifLocationReader.read(ByteArray(0)))
  }
}
