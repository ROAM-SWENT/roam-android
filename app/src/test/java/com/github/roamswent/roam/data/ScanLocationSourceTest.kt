// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanLocationSourceTest {

  @Test
  fun cameraScanUsesTheDeviceLocation() {
    assertEquals(ScanLocationSource.DEVICE, ScanLocationSource.forScan(isGalleryImage = false))
  }

  @Test
  fun galleryScanUsesOnlyThePhotoMetadata() {
    assertEquals(
        ScanLocationSource.PHOTO_METADATA,
        ScanLocationSource.forScan(isGalleryImage = true),
    )
  }
}
