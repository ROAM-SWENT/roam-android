package com.github.roamswent.roam.model.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiImageScreenTest {

  private val photo = ImagePayload(byteArrayOf(1, 2, 3), "image/jpeg")

  @Test
  fun returnsVerdictParsedFromGeminiReply() = runBlocking {
    val gemini =
        FakeGeminiClient(
            reply("""{"isMonument":true,"injectionSuspected":false,"reason":"A tower."}""")
        )

    val result = GeminiImageScreen(gemini).screen(photo)

    assertTrue(result.isMonument)
    assertFalse(result.injectionSuspected)
    assertEquals("A tower.", result.reason)
  }

  @Test
  fun sendsTheScreenPrompt() = runBlocking {
    val gemini =
        FakeGeminiClient(reply("""{"isMonument":true,"injectionSuspected":false,"reason":"ok"}"""))

    GeminiImageScreen(gemini).screen(photo)

    assertEquals(PromptBuilder.screenPrompt(), gemini.prompts.single())
  }

  @Test
  fun propagatesParseErrorForMalformedVerdict() {
    val gemini = FakeGeminiClient(reply("not json"))
    assertThrows(ParseException::class.java) {
      runBlocking { GeminiImageScreen(gemini).screen(photo) }
    }
  }
}
