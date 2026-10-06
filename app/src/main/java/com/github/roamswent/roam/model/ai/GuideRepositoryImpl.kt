package com.github.roamswent.roam.model.ai

import com.github.roamswent.roam.model.location.LocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Runs the whole discovery pipeline: prepare the photo, pre-screen it, request the guide, and parse
 * the reply. Every failure is thrown as a [DiscoveryException].
 *
 * Transient errors are retried with a growing pause, up to [MAX_TRANSIENT_ATTEMPTS] attempts.
 * Malformed replies are retried once. Blocked, network, and configuration errors are not retried.
 *
 * The request location is used when set. When it is null, [LocationProvider] is asked, and the
 * location is omitted from the prompt if it is still unavailable.
 */
class GuideRepositoryImpl(
    private val imagePreparer: ImagePreparer,
    private val imageScreen: ImageScreen,
    private val geminiClient: GeminiClient,
    private val locationProvider: LocationProvider,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) : MonumentGuideRepository {

  companion object {
    const val MAX_TRANSIENT_ATTEMPTS = 3
    const val MAX_PARSE_ATTEMPTS = 2
    const val BACKOFF_MS = 500L
  }

  override suspend fun guideFor(request: DiscoveryRequest): MonumentGuide {
    // Decoding and scaling are CPU-bound, so they run off the caller's dispatcher.
    val photo =
        withContext(Dispatchers.Default) {
          imagePreparer.prepare(request.image.bytes, request.image.mimeType)
        }

    val verdict = withRetries { imageScreen.screen(photo) }
    if (verdict.injectionSuspected) {
      throw RejectedImageException(RejectionReason.InjectionSuspected)
    }
    if (!verdict.isMonument) throw MonumentNotIdentifiedException()

    val location = request.location ?: locationProvider.currentCoordinates()
    val prompt = PromptBuilder.guidePrompt(request.preferences, location)
    return withRetries { ResponseParser.parseGuide(geminiClient.generate(prompt, photo)) }
  }

  /**
   * Runs [block], retrying transient failures and malformed replies. Attempts are counted across
   * both kinds of failure.
   */
  private suspend fun <T> withRetries(block: suspend () -> T): T {
    var attempt = 1
    while (true) {
      try {
        return block()
      } catch (e: TransientException) {
        if (attempt >= MAX_TRANSIENT_ATTEMPTS) throw e
      } catch (e: ParseException) {
        if (attempt >= MAX_PARSE_ATTEMPTS) throw e
      }
      pause(BACKOFF_MS * attempt)
      attempt++
    }
  }
}
