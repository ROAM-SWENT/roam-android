// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.location

import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream

/**
 * Reads where a photo was taken from its EXIF GPS metadata. It works on the raw bytes, before the
 * discovery pipeline re-encodes the photo and drops its metadata, so it does not depend on how the
 * photo was picked.
 */
object ExifLocationReader {

  /**
   * Returns the GPS position stored in [bytes], or null when there is no usable one: no EXIF or no
   * GPS tags, malformed metadata, a position out of range, or the (0, 0) placeholder some apps
   * write without a fix. Never throws, so a photo without a location never fails a scan.
   */
  fun read(bytes: ByteArray): Coordinates? {
    val latLong =
        try {
          ExifInterface(ByteArrayInputStream(bytes)).latLong
        } catch (e: Exception) {
          // Malformed or unsupported metadata is the same as no location.
          null
        } ?: return null
    val latitude = latLong[0]
    val longitude = latLong[1]
    if (latitude == 0.0 && longitude == 0.0) return null
    return try {
      Coordinates(latitude, longitude)
    } catch (e: IllegalArgumentException) {
      // Out of range, NaN or infinite.
      null
    }
  }
}
