// Co-authored-by: Codex <codex@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.ai

import com.github.roamswent.roam.model.location.Coordinates
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
  fun returnsGeneratedPrimitivesAndUsesThePreferencesSentToGemini() = runBlocking {
    val json =
        validGuide.dropLast(1) +
            ""","overview":"Une tour en fer.","timeline":[{"year":1889,"event":"Inaugurée."}],
               "sections":[{"topic":"Architecture","title":"Architecture","content":"Une charpente."},
               {"topic":"Nature","title":"Nature","content":"Près de jardins."}]}"""
    val gemini = FakeGeminiClient(reply(json))
    val guide =
        repository(FakeImageScreen(verdict()), gemini)
            .guideFor(request().copy(preferences = UserPreferences("fr", listOf(" Architecture "))))

    assertTrue(gemini.prompts.single().contains("""["Architecture"]"""))
    assertEquals(1, guide.interestCount)
    assertEquals("Une tour en fer.", guide.overview)
    assertEquals(listOf(GuideMilestone(1889, "Inaugurée.")), guide.timeline)
    assertEquals(listOf(GuideSection("Architecture", "Une charpente.")), guide.sections)
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

    assertFalse(gemini.prompts.single().contains("The photo was taken near"))
  }

  @Test
  fun returnedGuideKeepsMonumentLocationSeparateFromObservedLocation() = runBlocking {
    val observed = Coordinates(46.5191, 6.5668)
    val withMonument =
        validGuide.replace(
            "\"language\":\"fr\"}",
            "\"language\":\"fr\",\"monumentLatitude\":48.8584,\"monumentLongitude\":2.2945}",
        )
    val guide =
        repository(FakeImageScreen(verdict()), FakeGeminiClient(reply(withMonument)))
            .guideFor(request(observed))

    assertEquals(observed, guide.location)
    assertEquals(Coordinates(48.8584, 2.2945), guide.monumentLocation)
  }

  @Test
  fun returnedGuideHasNoMonumentLocationWhenAiGivesNone() = runBlocking {
    val guide =
        repository(FakeImageScreen(verdict()), FakeGeminiClient(reply(validGuide)))
            .guideFor(request(Coordinates(46.5191, 6.5668)))

    assertNull(guide.monumentLocation)
  }

  @Test
  fun neverAsksTheDeviceWhenDeviceLocationIsDisabled() = runBlocking {
    val gemini = FakeGeminiClient(reply(validGuide))
    val provider = FakeLocationProvider(Coordinates(48.85837, 2.29448))
    val guide =
        repository(FakeImageScreen(verdict()), gemini, provider)
            .guideFor(request(useDeviceLocation = false))

    assertEquals(0, provider.calls)
    assertNull(guide.location)
    assertFalse(gemini.prompts.single().contains("The photo was taken near"))
  }

  @Test
  fun usesTheGivenLocationWhenDeviceLocationIsDisabled() = runBlocking {
    val gemini = FakeGeminiClient(reply(validGuide))
    val photoLocation = Coordinates(48.85837, 2.29448)
    val provider = FakeLocationProvider(Coordinates(1.0, 2.0))
    val guide =
        repository(FakeImageScreen(verdict()), gemini, provider)
            .guideFor(request(photoLocation, useDeviceLocation = false))

    assertEquals(0, provider.calls)
    assertEquals(photoLocation, guide.location)
    assertTrue(gemini.prompts.single().contains("latitude 48.858, longitude 2.294"))
  }

  @Test
  fun returnedGuideCarriesRequestLocationWithoutAskingProvider() = runBlocking {
    val location = Coordinates(48.85837, 2.29448)
    val provider = FakeLocationProvider(Coordinates(1.0, 2.0))
    val guide =
        repository(FakeImageScreen(verdict()), FakeGeminiClient(reply(validGuide)), provider)
            .guideFor(request(location))

    assertEquals(location, guide.location)
    assertEquals(0, provider.calls)
  }

  @Test
  fun returnedGuideCarriesProviderLocationResolvedOnce() = runBlocking {
    val location = Coordinates(48.85837, 2.29448)
    val provider = FakeLocationProvider(location)
    val gemini = FakeGeminiClient(reply("not json"), reply(validGuide))
    val guide = repository(FakeImageScreen(verdict()), gemini, provider).guideFor(request())

    assertEquals(location, guide.location)
    assertEquals(1, provider.calls)
  }

  @Test
  fun returnedGuideCarriesThePhotoSentToTheAi() = runBlocking {
    val gemini = FakeGeminiClient(reply(validGuide))
    val raw = request().image

    val guide = repository(FakeImageScreen(verdict()), gemini).guideFor(request())

    val photo = checkNotNull(guide.photo)
    assertEquals(gemini.images.single(), photo)
    assertEquals("image/jpeg", photo.mimeType)
    assertTrue(photo.bytes.size < raw.bytes.size)
  }

  @Test
  fun returnedGuideHasNoLocationWhenUnavailable() = runBlocking {
    val guide =
        repository(FakeImageScreen(verdict()), FakeGeminiClient(reply(validGuide)))
            .guideFor(request())

    assertNull(guide.location)
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

  private fun request(location: Coordinates? = null, useDeviceLocation: Boolean = true) =
      DiscoveryRequest(ImagePayload(fixture(), "image/jpeg"), location, prefs, useDeviceLocation)

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
