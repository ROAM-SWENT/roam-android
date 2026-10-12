package com.github.roamswent.roam.model.ai

import com.github.roamswent.roam.model.location.Coordinates
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GuideRepositoryImplTest {

  private val validGuide =
      """
      {"monumentName":"Eiffel Tower","confidence":0.92,"history":"Built in 1889.",
       "facts":["It grew taller in summer."],"culture":"A symbol of Paris.","language":"fr"}
      """
          .trimIndent()

  private val pauses = mutableListOf<Long>()

  @Test
  fun returnsGuideForIdentifiedMonument() = runBlocking {
    val gemini = FakeGeminiClient(reply(validGuide))
    val guide = repository(FakeImageScreen(verdict()), gemini).guideFor(request())

    assertEquals("Eiffel Tower", guide.monumentName)
    assertEquals("fr", guide.language)
    assertTrue(gemini.prompts.single().contains("Write the text fields in fr."))
  }

  @Test
  fun usesRequestLocationAsCoarsenedPromptDetail() = runBlocking {
    val gemini = FakeGeminiClient(reply(validGuide))
    val location = Coordinates(48.85837, 2.29448)
    repository(FakeImageScreen(verdict()), gemini).guideFor(request(location))

    val prompt = gemini.prompts.single()
    assertTrue(prompt.contains("latitude 48.858, longitude 2.294"))
    assertFalse(prompt.contains("48.85837"))
  }

  @Test
  fun fallsBackToLocationProviderWhenRequestHasNoLocation() = runBlocking {
    val gemini = FakeGeminiClient(reply(validGuide))
    val provider = FakeLocationProvider(Coordinates(48.85837, 2.29448))
    repository(FakeImageScreen(verdict()), gemini, provider).guideFor(request())

    assertTrue(gemini.prompts.single().contains("latitude 48.858"))
  }

  @Test
  fun omitsLocationWhenNeitherRequestNorProviderHasIt() = runBlocking {
    val gemini = FakeGeminiClient(reply(validGuide))
    repository(FakeImageScreen(verdict()), gemini).guideFor(request())

    assertFalse(gemini.prompts.single().contains("latitude"))
  }

  @Test
  fun rejectsPhotoWithInjectedInstructionsBeforeAskingForGuide() = runBlocking {
    val gemini = FakeGeminiClient()
    val screen = FakeImageScreen(verdict(injectionSuspected = true))

    val error =
        assertThrows(RejectedImageException::class.java) {
          runBlocking { repository(screen, gemini).guideFor(request()) }
        }
    assertEquals(RejectionReason.InjectionSuspected, error.reason)
    assertEquals(0, gemini.prompts.size)
  }

  @Test
  fun throwsNotIdentifiedWhenScreenFindsNoMonument() = runBlocking {
    val gemini = FakeGeminiClient()
    assertThrows(MonumentNotIdentifiedException::class.java) {
      runBlocking {
        repository(FakeImageScreen(verdict(isMonument = false)), gemini).guideFor(request())
      }
    }
    assertEquals(0, gemini.prompts.size)
  }

  @Test
  fun rejectsInvalidPhotoBeforeAnyModelCall() = runBlocking {
    val screen = FakeImageScreen()
    val notAnImage = "not a photo".toByteArray()

    assertThrows(RejectedImageException::class.java) {
      runBlocking {
        repository(screen, FakeGeminiClient())
            .guideFor(DiscoveryRequest(ImagePayload(notAnImage, "image/jpeg"), null, prefs))
      }
    }
    assertEquals(0, screen.calls)
  }

  @Test
  fun retriesTransientGuideErrorsThenSucceeds() = runBlocking {
    val gemini =
        FakeGeminiClient(
            failWith(TransientException("rate limited")),
            failWith(TransientException("timeout")),
            reply(validGuide),
        )

    repository(FakeImageScreen(verdict()), gemini).guideFor(request())

    assertEquals(3, gemini.prompts.size)
    assertEquals(listOf(500L, 1000L), pauses)
  }

  @Test
  fun givesUpAfterMaxTransientAttempts() = runBlocking {
    val gemini =
        FakeGeminiClient(
            failWith(TransientException("1")),
            failWith(TransientException("2")),
            failWith(TransientException("3")),
        )

    assertThrows(TransientException::class.java) {
      runBlocking { repository(FakeImageScreen(verdict()), gemini).guideFor(request()) }
    }
    assertEquals(GuideRepositoryImpl.MAX_TRANSIENT_ATTEMPTS, gemini.prompts.size)
  }

  @Test
  fun retriesMalformedGuideOnce() = runBlocking {
    val gemini = FakeGeminiClient(reply("not json"), reply(validGuide))

    val guide = repository(FakeImageScreen(verdict()), gemini).guideFor(request())

    assertEquals("Eiffel Tower", guide.monumentName)
    assertEquals(2, gemini.prompts.size)
  }

  @Test
  fun givesUpAfterSecondMalformedGuide() = runBlocking {
    val gemini = FakeGeminiClient(reply("not json"), reply("still not json"))

    assertThrows(ParseException::class.java) {
      runBlocking { repository(FakeImageScreen(verdict()), gemini).guideFor(request()) }
    }
    assertEquals(GuideRepositoryImpl.MAX_PARSE_ATTEMPTS, gemini.prompts.size)
  }

  @Test
  fun doesNotRetryBlockedResponses() = runBlocking {
    val gemini = FakeGeminiClient(failWith(BlockedException("safety")))

    assertThrows(BlockedException::class.java) {
      runBlocking { repository(FakeImageScreen(verdict()), gemini).guideFor(request()) }
    }
    assertEquals(1, gemini.prompts.size)
  }

  @Test
  fun doesNotRetryWhenGuideReportsNoMonument() = runBlocking {
    val noMonument = validGuide.replace("\"Eiffel Tower\"", "null")
    val gemini = FakeGeminiClient(reply(noMonument))

    assertThrows(MonumentNotIdentifiedException::class.java) {
      runBlocking { repository(FakeImageScreen(verdict()), gemini).guideFor(request()) }
    }
    assertEquals(1, gemini.prompts.size)
  }

  @Test
  fun retriesTransientScreenErrors() = runBlocking {
    val screen =
        FakeImageScreen(
            { throw TransientException("rate limited") },
            verdict(),
        )
    val gemini = FakeGeminiClient(reply(validGuide))

    repository(screen, gemini).guideFor(request())

    assertEquals(2, screen.calls)
    assertEquals(listOf(500L), pauses)
  }

  private val prefs = UserPreferences("fr")

  private fun request(location: Coordinates? = null) =
      DiscoveryRequest(ImagePayload(fixture(), "image/jpeg"), location, prefs)

  private fun repository(
      screen: ImageScreen,
      gemini: GeminiClient,
      location: FakeLocationProvider = FakeLocationProvider(null),
  ) =
      GuideRepositoryImpl(
          imagePreparer = ImagePreparer(),
          imageScreen = screen,
          geminiClient = gemini,
          locationProvider = location,
          pause = { pauses += it },
      )

  private fun fixture(): ByteArray =
      javaClass.getResourceAsStream("/fixtures/eiffel_landscape_2000x1333.jpg")!!.use {
        it.readBytes()
      }
}
