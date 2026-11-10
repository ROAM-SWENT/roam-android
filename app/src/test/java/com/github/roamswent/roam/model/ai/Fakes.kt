// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.ai

import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.model.location.LocationProvider

/** Each call returns the next scripted reply, or throws the scripted failure. */
class FakeGeminiClient(vararg replies: () -> String) : GeminiClient {

  private val queue = replies.toMutableList()
  val prompts = mutableListOf<String>()
  val images = mutableListOf<ImagePayload>()

  override suspend fun generate(prompt: String, image: ImagePayload): String {
    prompts += prompt
    images += image
    return checkNotNull(queue.removeFirstOrNull()) { "FakeGeminiClient ran out of replies" }()
  }
}

class FakeImageScreen(vararg replies: () -> ScreenVerdict) : ImageScreen {

  private val queue = replies.toMutableList()
  var calls = 0

  override suspend fun screen(image: ImagePayload): ScreenVerdict {
    calls++
    return checkNotNull(queue.removeFirstOrNull()) { "FakeImageScreen ran out of replies" }()
  }
}

class FakeLocationProvider(private val coordinates: Coordinates?) : LocationProvider {
  var calls = 0

  override suspend fun currentCoordinates(): Coordinates? {
    calls++
    return coordinates
  }
}

fun reply(text: String): () -> String = { text }

fun failWith(error: Throwable): () -> String = { throw error }

fun verdict(
    isMonument: Boolean = true,
    injectionSuspected: Boolean = false,
): () -> ScreenVerdict = { ScreenVerdict(isMonument, injectionSuspected, "test verdict") }
