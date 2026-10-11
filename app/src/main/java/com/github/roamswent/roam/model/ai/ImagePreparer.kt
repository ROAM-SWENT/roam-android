package com.github.roamswent.roam.model.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Turns a photo into the clean JPEG sent to Gemini. Runs the checks that need a decoder:
 * dimensions, animation, and the full pixel decode, which is the real validity check. The result is
 * re-encoded at most [maxSide] px on the long side. Re-encoding drops all metadata and any bytes
 * appended after the image.
 */
class ImagePreparer(
    private val maxSide: Int = DEFAULT_MAX_SIDE,
    private val jpegQuality: Int = 85,
) {

  companion object {
    const val MIN_SIDE = 64
    const val MAX_SIDE = 8000
    const val DEFAULT_MAX_SIDE = 1024
  }

  /** @throws RejectedImageException when the image fails any check, including the validator's. */
  fun prepare(raw: ByteArray, declaredMimeType: String? = null): ImagePayload {
    ImageValidator.validate(raw, declaredMimeType)
    val (width, height) = readDimensions(raw)
    if (min(width, height) < MIN_SIDE || max(width, height) > MAX_SIDE) {
      throw RejectedImageException(RejectionReason.DimensionsOutOfRange)
    }
    if (isAnimated(raw)) throw RejectedImageException(RejectionReason.Animated)

    val bitmap = decodeScaled(raw, width, height)
    return ImagePayload(encodeJpeg(bitmap), "image/jpeg")
  }

  /** Reads the size from the header only. Nothing is allocated for the pixels. */
  private fun readDimensions(raw: ByteArray): Pair<Int, Int> {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    decodeBytes(raw, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) {
      throw RejectedImageException(RejectionReason.Undecodable)
    }
    return options.outWidth to options.outHeight
  }

  /**
   * Platform decoders report malformed data as null, and some implementations throw instead. Both
   * mean the image cannot be decoded, so both become null here.
   */
  private fun decodeBytes(raw: ByteArray, options: BitmapFactory.Options): Bitmap? =
      try {
        BitmapFactory.decodeByteArray(raw, 0, raw.size, options)
      } catch (e: RuntimeException) {
        null
      }

  /** Asks ImageDecoder for the animation flag, decoding only a 1x1 frame. */
  private fun isAnimated(raw: ByteArray): Boolean {
    var animated = false
    try {
      val source = ImageDecoder.createSource(ByteBuffer.wrap(raw))
      ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        animated = info.isAnimated
        decoder.setTargetSize(1, 1)
      }
    } catch (e: ImageDecoder.DecodeException) {
      throw RejectedImageException(RejectionReason.Undecodable)
    }
    return animated
  }

  /**
   * Decodes the pixels, downsampled by a power of two so a huge photo never allocates its full
   * size, then scales to exactly [maxSide] on the long side.
   */
  private fun decodeScaled(raw: ByteArray, width: Int, height: Int): Bitmap {
    val sample = Integer.highestOneBit(max(1, max(width, height) / maxSide))
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded =
        decodeBytes(raw, options) ?: throw RejectedImageException(RejectionReason.Undecodable)
    val longSide = max(decoded.width, decoded.height)
    if (longSide <= maxSide) return decoded

    val scale = maxSide.toFloat() / longSide
    val scaled =
        Bitmap.createScaledBitmap(
            decoded,
            max(1, (decoded.width * scale).roundToInt()),
            max(1, (decoded.height * scale).roundToInt()),
            true,
        )
    decoded.recycle()
    return scaled
  }

  /** JPEG has no alpha channel, so the image is flattened onto white first. */
  private fun encodeJpeg(bitmap: Bitmap): ByteArray {
    val flattened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    Canvas(flattened).apply {
      drawColor(Color.WHITE)
      drawBitmap(bitmap, 0f, 0f, null)
    }
    val out = ByteArrayOutputStream()
    val written = flattened.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
    bitmap.recycle()
    flattened.recycle()
    if (!written) throw RejectedImageException(RejectionReason.Undecodable)
    return out.toByteArray()
  }
}
