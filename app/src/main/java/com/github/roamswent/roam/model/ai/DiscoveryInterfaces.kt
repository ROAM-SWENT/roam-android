package com.github.roamswent.roam.model.ai

/** The only entry point the ViewModel will use. Hides validation, screening, and Gemini. */
interface MonumentGuideRepository {

  /**
   * Identifies the monument in [request] and returns its guide.
   *
   * @throws DiscoveryException when no guide can be produced.
   */
  suspend fun guideFor(request: DiscoveryRequest): MonumentGuide
}

/** Transport to Gemini. Returns the model's raw JSON text, no parsing. */
interface GeminiClient {

  /**
   * Sends [prompt] together with [image] and returns the raw response text.
   *
   * @throws DiscoveryException when the request fails.
   */
  suspend fun generate(prompt: String, image: ImagePayload): String
}

/** Cloud pre-screen that checks the photo for non-monuments and injected instructions. */
interface ImageScreen {

  /** @throws DiscoveryException when the screen cannot run. */
  suspend fun screen(image: ImagePayload): ScreenVerdict
}
