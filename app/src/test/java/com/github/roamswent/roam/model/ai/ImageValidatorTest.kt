package com.github.roamswent.roam.model.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Uses only the magic bytes: these checks never look past the first bytes of the file. */
class ImageValidatorTest {

  private val jpeg = jpeg(hasEoi = true)
  private val png = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(16)
  private val webp =
      "RIFF".toByteArray(Charsets.US_ASCII) +
          ByteArray(4) +
          "WEBP".toByteArray(Charsets.US_ASCII) +
          ByteArray(8)

  @Test
  fun acceptsJpeg() {
    assertEquals(ImageFormat.JPEG, ImageValidator.validate(jpeg, "image/jpeg"))
  }

  @Test
  fun acceptsPng() {
    assertEquals(ImageFormat.PNG, ImageValidator.validate(png, "image/png"))
  }

  @Test
  fun acceptsWebp() {
    assertEquals(ImageFormat.WEBP, ImageValidator.validate(webp, "image/webp"))
  }

  @Test
  fun acceptsJpgAliasAsJpeg() {
    assertEquals(ImageFormat.JPEG, ImageValidator.validate(jpeg, "image/jpg"))
  }

  @Test
  fun acceptsWhenNoMimeTypeIsDeclared() {
    assertEquals(ImageFormat.PNG, ImageValidator.validate(png))
  }

  @Test
  fun rejectsEmptyInput() {
    assertRejected(RejectionReason.Undecodable) { ImageValidator.validate(ByteArray(0)) }
  }

  @Test
  fun rejectsOversizedInput() {
    assertRejected(RejectionReason.Oversized) {
      ImageValidator.validate(ByteArray(ImageValidator.MAX_BYTES + 1))
    }
  }

  @Test
  fun rejectsUnsupportedFormat() {
    val gif = "GIF89a".toByteArray(Charsets.US_ASCII) + ByteArray(20)
    assertRejected(RejectionReason.UnsupportedFormat) { ImageValidator.validate(gif) }
  }

  @Test
  fun acceptsJpegWithDataAppendedAfterEndOfImage() {
    assertEquals(ImageFormat.JPEG, ImageValidator.validate(jpeg + "MOTION".toByteArray()))
  }

  @Test
  fun rejectsJpegWithoutEndOfImageMarker() {
    val truncated = bytes(0xFF, 0xD8, 0xFF, 0xE0) + ByteArray(16)
    assertRejected(RejectionReason.Undecodable) { ImageValidator.validate(truncated) }
  }

  @Test
  fun rejectsTruncatedJpegEvenWithAnEndOfImageMarkerInsideAnEarlierSegment() {
    // An EXIF (APP1) segment can embed a whole thumbnail JPEG, which has its own end-of-image
    // marker. A whole-file search for FF D9 would find that one and wrongly accept a main image
    // that is actually truncated, since no real end-of-image marker follows the scan data.
    val exifPayloadWithFakeEoi = bytes(0x00, 0x00) + bytes(0xFF, 0xD9) + ByteArray(6)
    val app1 = bytes(0xFF, 0xE1) + be16(exifPayloadWithFakeEoi.size + 2) + exifPayloadWithFakeEoi
    val sos = bytes(0xFF, 0xDA) + be16(4) + bytes(0x00, 0x00)
    val truncatedScanData = ByteArray(4) // no real end-of-image marker follows
    val truncated = bytes(0xFF, 0xD8) + app1 + sos + truncatedScanData

    assertRejected(RejectionReason.Undecodable) { ImageValidator.validate(truncated) }
  }

  @Test
  fun acceptsJpegWithAnEndOfImageLookingMarkerInsideAnEarlierSegment() {
    // Same misleading earlier marker as above, but this time a real end-of-image marker follows
    // the scan data, so the file is genuinely complete and must be accepted.
    val exifPayloadWithFakeEoi = bytes(0x00, 0x00) + bytes(0xFF, 0xD9) + ByteArray(6)
    val app1 = bytes(0xFF, 0xE1) + be16(exifPayloadWithFakeEoi.size + 2) + exifPayloadWithFakeEoi
    val sos = bytes(0xFF, 0xDA) + be16(4) + bytes(0x00, 0x00)
    val scanData = ByteArray(4)
    val complete = bytes(0xFF, 0xD8) + app1 + sos + scanData + bytes(0xFF, 0xD9)

    assertEquals(ImageFormat.JPEG, ImageValidator.validate(complete))
  }

  @Test
  fun rejectsRiffContainerThatIsNotWebp() {
    val wave =
        "RIFF".toByteArray(Charsets.US_ASCII) + ByteArray(4) + "WAVE".toByteArray(Charsets.US_ASCII)
    assertRejected(RejectionReason.UnsupportedFormat) { ImageValidator.validate(wave) }
  }

  @Test
  fun rejectsDeclaredMimeThatDoesNotMatchTheBytes() {
    assertRejected(RejectionReason.MimeMismatch) { ImageValidator.validate(png, "image/jpeg") }
  }

  @Test
  fun rejectsDeclaredMimeThatIsNotAllowed() {
    assertRejected(RejectionReason.MimeMismatch) { ImageValidator.validate(jpeg, "image/gif") }
  }

  private fun assertRejected(reason: RejectionReason, block: () -> Unit) {
    val error = assertThrows(RejectedImageException::class.java) { block() }
    assertEquals(reason, error.reason)
  }

  private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

  private fun be16(value: Int): ByteArray = bytes((value shr 8) and 0xFF, value and 0xFF)

  /** A minimal but structurally real JPEG: SOI, a generic marker segment, SOS, scan data, EOI. */
  private fun jpeg(hasEoi: Boolean): ByteArray {
    val genericSegment = bytes(0xFF, 0xE0) + be16(4) + bytes(0x00, 0x00)
    val sos = bytes(0xFF, 0xDA) + be16(4) + bytes(0x00, 0x00)
    val scanData = ByteArray(8)
    val eoi = if (hasEoi) bytes(0xFF, 0xD9) else ByteArray(0)
    return bytes(0xFF, 0xD8) + genericSegment + sos + scanData + eoi
  }
}
