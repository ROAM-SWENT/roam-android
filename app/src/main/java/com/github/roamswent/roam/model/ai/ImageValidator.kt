package com.github.roamswent.roam.model.ai

/** The image formats the app accepts. */
enum class ImageFormat(val mimeType: String) {
  JPEG("image/jpeg"),
  PNG("image/png"),
  WEBP("image/webp"),
}

/**
 * Cheap checks on the raw bytes, run before anything is decoded: size, the real format from the
 * magic bytes, the declared MIME type, and that a JPEG is not cut off. Dimensions, animation and
 * pixel validity are checked when the image is decoded, in ImagePreparer.
 */
object ImageValidator {

  const val MAX_BYTES = 20 * 1024 * 1024

  private val JPEG_SIGNATURE = bytesOf(0xFF, 0xD8, 0xFF)
  private val JPEG_END_OF_IMAGE = bytesOf(0xFF, 0xD9)
  private val PNG_SIGNATURE = bytesOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
  private val RIFF = "RIFF".toByteArray(Charsets.US_ASCII)
  private val WEBP = "WEBP".toByteArray(Charsets.US_ASCII)

  /**
   * Returns the format found from the file's magic bytes.
   *
   * @param declaredMimeType the MIME type the caller claims, if any. A mismatch is rejected.
   * @throws RejectedImageException with the reason the image was refused.
   */
  fun validate(bytes: ByteArray, declaredMimeType: String? = null): ImageFormat {
    if (bytes.isEmpty()) reject(RejectionReason.Undecodable)
    if (bytes.size > MAX_BYTES) reject(RejectionReason.Oversized)
    val format = detectFormat(bytes) ?: reject(RejectionReason.UnsupportedFormat)
    if (declaredMimeType != null && formatOf(declaredMimeType) != format) {
      reject(RejectionReason.MimeMismatch)
    }
    // A cut-off JPEG decodes partly, so the end-of-image marker must be present. Data appended
    // after it (for example a motion-photo video) is allowed: re-encoding drops it.
    if (format == ImageFormat.JPEG && !bytes.containsSequence(JPEG_END_OF_IMAGE)) {
      reject(RejectionReason.Undecodable)
    }
    return format
  }

  private fun reject(reason: RejectionReason): Nothing = throw RejectedImageException(reason)

  private fun formatOf(mimeType: String): ImageFormat? {
    val normalised = mimeType.trim().lowercase()
    val canonical = if (normalised == "image/jpg") "image/jpeg" else normalised
    return ImageFormat.entries.firstOrNull { it.mimeType == canonical }
  }

  private fun detectFormat(bytes: ByteArray): ImageFormat? =
      when {
        bytes.matchesAt(0, JPEG_SIGNATURE) -> ImageFormat.JPEG
        bytes.matchesAt(0, PNG_SIGNATURE) -> ImageFormat.PNG
        bytes.matchesAt(0, RIFF) && bytes.matchesAt(8, WEBP) -> ImageFormat.WEBP
        else -> null
      }

  private fun ByteArray.matchesAt(offset: Int, expected: ByteArray): Boolean =
      size >= offset + expected.size && expected.indices.all { this[offset + it] == expected[it] }

  private fun ByteArray.containsSequence(sequence: ByteArray): Boolean =
      (0..size - sequence.size).any { matchesAt(it, sequence) }

  private fun bytesOf(vararg values: Int): ByteArray =
      ByteArray(values.size) { values[it].toByte() }
}
