// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.data

/** Where the observed location of a scan comes from. */
enum class ScanLocationSource {
  /** The device's current position: a camera capture is taken where the user stands. */
  DEVICE,

  /**
   * Only the photo's own EXIF GPS metadata, never the device: a gallery photo may have been taken
   * anywhere, at any time.
   */
  PHOTO_METADATA;

  companion object {
    /** The source for a scan, depending on whether its photo was picked from the gallery. */
    fun forScan(isGalleryImage: Boolean): ScanLocationSource =
        if (isGalleryImage) PHOTO_METADATA else DEVICE
  }
}
