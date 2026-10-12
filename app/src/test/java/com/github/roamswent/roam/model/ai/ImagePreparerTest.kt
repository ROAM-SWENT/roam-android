package com.github.roamswent.roam.model.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImagePreparerTest {

  private val preparer = ImagePreparer()

  @Test
  fun preparesJpegAsJpegPayload() {
    val payload = preparer.prepare(encode(640, 480, Bitmap.CompressFormat.JPEG), "image/jpeg")
    assertEquals("image/jpeg", payload.mimeType)
    assertEquals(640 to 480, dimensionsOf(payload.bytes))
  }

  @Test
  fun preparesPngAsJpegPayload() {
    val payload = preparer.prepare(encode(640, 480, Bitmap.CompressFormat.PNG))
    assertEquals("image/jpeg", payload.mimeType)
    assertEquals(640 to 480, dimensionsOf(payload.bytes))
  }

  @Test
  fun downscalesLongSideToMaxSide() {
    val payload = preparer.prepare(encode(2048, 1024, Bitmap.CompressFormat.PNG))
    assertEquals(1024 to 512, dimensionsOf(payload.bytes))
  }

  @Test
  fun keepsSmallImagesAtTheirSize() {
    val payload = preparer.prepare(encode(300, 200, Bitmap.CompressFormat.PNG))
    assertEquals(300 to 200, dimensionsOf(payload.bytes))
  }

  @Test
  fun dropsBytesAppendedAfterTheImage() {
    val appended = encode(640, 480, Bitmap.CompressFormat.JPEG) + "PAYLOAD".toByteArray()
    val payload = preparer.prepare(appended, "image/jpeg")
    assertTrue(!String(payload.bytes, Charsets.ISO_8859_1).contains("PAYLOAD"))
  }

  @Test
  fun rejectsCorruptPixelData() {
    val corrupt = bytes(0xFF, 0xD8, 0xFF, 0xE0) + ByteArray(64) { 0x13 }
    assertRejected(RejectionReason.Undecodable) { preparer.prepare(corrupt) }
  }

  @Test
  fun rejectsImagesBelowMinimumSide() {
    assertRejected(RejectionReason.DimensionsOutOfRange) {
      preparer.prepare(encode(32, 32, Bitmap.CompressFormat.PNG))
    }
  }

  @Test
  fun rejectsBadMimeBeforeDecoding() {
    assertRejected(RejectionReason.MimeMismatch) {
      preparer.prepare(encode(640, 480, Bitmap.CompressFormat.PNG), "image/jpeg")
    }
  }

  @Test
  fun downscalesLandscapePhotoFromFixture() {
    val payload = preparer.prepare(fixture("eiffel_landscape_2000x1333.jpg"))
    assertEquals(1024 to 682, dimensionsOf(payload.bytes))
  }

  @Test
  fun downscalesPortraitPhotoFromFixture() {
    val payload = preparer.prepare(fixture("eiffel_portrait_1493x2000.jpg"))
    val (width, height) = dimensionsOf(payload.bytes)
    assertEquals(1024, height)
    assertTrue(width < height)
  }

  @Test
  fun downscalesVeryWideFixtureWithinMemoryLimits() {
    val payload = preparer.prepare(fixture("eiffel_wide_6000px.jpg"))
    assertEquals(1024 to 683, dimensionsOf(payload.bytes))
  }

  @Test
  fun stripsExifFromPhotoFixture() {
    val payload = preparer.prepare(fixture("eiffel_exif_960x1434.jpg"))
    assertFalse(String(payload.bytes, Charsets.ISO_8859_1).contains("Exif"))
  }

  @Test
  fun rejectsTinyFixtureBelowMinimumSide() {
    assertRejected(RejectionReason.DimensionsOutOfRange) {
      preparer.prepare(fixture("eiffel_tiny_32px.jpg"))
    }
  }

  @Test
  fun rejectsAnimatedGifFixtureAsUnsupported() {
    assertRejected(RejectionReason.UnsupportedFormat) {
      preparer.prepare(fixture("eiffel_animated_unsupported.gif"))
    }
  }

  @Ignore("Robolectric cannot decode WebP. Verify on an emulator (instrumented test).")
  @Test
  fun preparesLossyWebpFixture() {
    val payload = preparer.prepare(fixture("eiffel_lossy_330x611.webp"), "image/webp")
    assertEquals("image/jpeg", payload.mimeType)
    assertEquals(330 to 611, dimensionsOf(payload.bytes))
  }

  @Ignore("Robolectric cannot decode WebP. Verify on an emulator (instrumented test).")
  @Test
  fun flattensTransparentWebpFixtureOntoWhite() {
    val payload = preparer.prepare(fixture("eiffel_alpha_1000x1200.webp"))
    val decoded = BitmapFactory.decodeByteArray(payload.bytes, 0, payload.bytes.size)
    val corner = decoded.getPixel(0, 0)
    assertTrue(Color.red(corner) > 240 && Color.green(corner) > 240 && Color.blue(corner) > 240)
  }

  @Test
  fun rejectsTruncatedJpegFixture() {
    assertRejected(RejectionReason.Undecodable) {
      preparer.prepare(fixture("eiffel_truncated.jpg"))
    }
  }

  private fun fixture(name: String): ByteArray =
      javaClass.getResourceAsStream("/fixtures/$name")!!.use { it.readBytes() }

  private fun assertRejected(reason: RejectionReason, block: () -> Unit) {
    val error = assertThrows(RejectedImageException::class.java) { block() }
    assertEquals(reason, error.reason)
  }

  private fun encode(width: Int, height: Int, format: Bitmap.CompressFormat): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.rgb(200, 120, 40))
    val out = ByteArrayOutputStream()
    bitmap.compress(format, 90, out)
    bitmap.recycle()
    return out.toByteArray()
  }

  private fun dimensionsOf(bytes: ByteArray): Pair<Int, Int> {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    return options.outWidth to options.outHeight
  }

  private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
}
