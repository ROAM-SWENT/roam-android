package com.github.roamswent.roam.model.ai

/** Pre-screen run by Gemini: sends the screen prompt with the photo and parses the verdict. */
class GeminiImageScreen(private val geminiClient: GeminiClient) : ImageScreen {

  override suspend fun screen(image: ImagePayload): ScreenVerdict =
      ResponseParser.parseVerdict(geminiClient.generate(PromptBuilder.screenPrompt(), image))
}
