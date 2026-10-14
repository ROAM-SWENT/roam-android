package com.github.roamswent.roam.data

import android.content.Context
import android.net.Uri
import com.github.roamswent.roam.model.ai.DiscoveryRequest
import com.github.roamswent.roam.model.ai.ImagePayload
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.ai.MonumentGuideRepository
import com.github.roamswent.roam.model.ai.UserPreferences
import com.github.roamswent.roam.repository.CaptureSender
import java.util.Locale
import kotlinx.coroutines.CancellationException

/**
 * Sends the captured photo through the AI discovery pipeline. [readBytes] is injectable so tests
 * don't need a real [Context]/content resolver.
 */
class GuideCaptureSender(
    private val context: Context,
    private val guideRepository: MonumentGuideRepository,
    private val readBytes: (Uri) -> ByteArray = { uri ->
      checkNotNull(context.contentResolver.openInputStream(uri)) { "cannot open $uri" }
          .use { it.readBytes() }
    },
) : CaptureSender {

  override suspend fun send(uri: Uri): Result<MonumentGuide> =
      try {
        Result.success(
            guideRepository.guideFor(
                DiscoveryRequest(
                    image = ImagePayload(readBytes(uri), "image/jpeg"),
                    location = null,
                    preferences = UserPreferences(language = Locale.getDefault().language),
                )
            )
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }
}
