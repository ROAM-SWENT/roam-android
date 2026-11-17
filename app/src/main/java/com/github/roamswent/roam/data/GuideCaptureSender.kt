// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

package com.github.roamswent.roam.data

import android.content.Context
import android.net.Uri
import com.github.roamswent.roam.model.ai.DiscoveryRequest
import com.github.roamswent.roam.model.ai.ImagePayload
import com.github.roamswent.roam.model.ai.ImageValidator
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.ai.MonumentGuideRepository
import com.github.roamswent.roam.model.ai.UserPreferences
import com.github.roamswent.roam.model.interests.UserInterestsRepository
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.model.location.ExifLocationReader
import com.github.roamswent.roam.repository.CaptureSender
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CancellationException

/**
 * Sends the captured photo through the AI discovery pipeline. [readBytes] is injectable so tests
 * don't need a real [Context]/content resolver.
 *
 * The observed location depends on [locationSource]. For [ScanLocationSource.DEVICE] the pipeline
 * asks the device for its current position. For [ScanLocationSource.PHOTO_METADATA] it is only the
 * position [readPhotoLocation] finds in the photo's own bytes, or none: the device is never asked.
 */
class GuideCaptureSender(
    private val context: Context,
    private val guideRepository: MonumentGuideRepository,
    private val userInterestsRepository: UserInterestsRepository,
    private val readBytes: (Uri) -> ByteArray = { uri ->
      checkNotNull(context.contentResolver.openInputStream(uri)) { "cannot open $uri" }
          .use { it.readBytes() }
    },
    private val locationSource: ScanLocationSource = ScanLocationSource.DEVICE,
    private val readPhotoLocation: (ByteArray) -> Coordinates? = ExifLocationReader::read,
) : CaptureSender {

  override suspend fun send(uri: Uri): Result<MonumentGuide> =
      try {
        val bytes = readBytes(uri)
        val mimType =
            context.contentResolver.getType(uri)
                ?: ImageValidator.detectFormat(bytes)?.mimeType
                ?: throw IOException("Unsupported image format")
        val fromPhotoMetadata = locationSource == ScanLocationSource.PHOTO_METADATA
        Result.success(
            guideRepository.guideFor(
                DiscoveryRequest(
                    image = ImagePayload(bytes, mimType),
                    // The raw bytes still hold the metadata; the pipeline drops it when preparing.
                    location = if (fromPhotoMetadata) readPhotoLocation(bytes) else null,
                    preferences =
                        UserPreferences(
                            language = Locale.getDefault().language,
                            interests = userInterestsRepository.getUserInterests().interests,
                        ),
                    useDeviceLocation = !fromPhotoMetadata,
                )
            )
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }
}
