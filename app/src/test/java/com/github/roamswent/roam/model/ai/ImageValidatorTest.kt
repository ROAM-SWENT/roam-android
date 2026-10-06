package com.github.roamswent.roam.model.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Uses only the magic bytes: these checks never look past the first bytes of the file. */
class ImageValidatorTest {

  private val jpeg = bytes(0xFF, 0xD8, 0xFF, 0xE0) + ByteArray(16)
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
}
