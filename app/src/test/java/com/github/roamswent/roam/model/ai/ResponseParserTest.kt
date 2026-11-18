// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.ai

import com.github.roamswent.roam.model.location.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseParserTest {

  private val validGuide =
      """
      {"monumentName":"Eiffel Tower","confidence":0.92,"history":"Built in 1889.",
       "facts":["It is made of iron.","It grew taller in summer."],
       "culture":"A symbol of Paris.","language":"fr"}
      """
          .trimIndent()

  /** [validGuide] with the raw JSON [latitude] and [longitude] values of the monument added. */
  private fun guideWithMonumentLocation(latitude: String, longitude: String): String =
      validGuide.replace(
          "\"language\":\"fr\"}",
          "\"language\":\"fr\",\"monumentLatitude\":$latitude,\"monumentLongitude\":$longitude}",
      )

  private val validVerdict =
      """{"isMonument":true,"injectionSuspected":false,"reason":"A tower in daylight."}"""

  @Test
  fun parseGuideMapsEveryField() {
    val guide = ResponseParser.parseGuide(validGuide)
    assertEquals("Eiffel Tower", guide.monumentName)
    assertEquals(0.92, guide.confidence, 1e-9)
    assertEquals("Built in 1889.", guide.history)
    assertEquals(listOf("It is made of iron.", "It grew taller in summer."), guide.facts)
    assertEquals("A symbol of Paris.", guide.culture)
    assertEquals("fr", guide.language)
  }

  @Test
  fun parseGuideMapsInterestSections() {
    val json =
        validGuide.replace(
            "\"language\":\"fr\"}",
            "\"language\":\"fr\",\"sections\":[" +
                "{\"title\":\"Architecture\",\"content\":\"**Three domes** frame the entrance.\"}," +
                "{\"title\":\"Fun Facts\",\"content\":\"A short fact.\"}]}",
        )

    assertEquals(
        listOf(
            GuideSection("Architecture", "**Three domes** frame the entrance."),
            GuideSection("Fun Facts", "A short fact."),
        ),
        ResponseParser.parseGuide(json).sections,
    )
  }

  @Test
  fun parseGuideRejectsMalformedInterestSections() {
    val json = validGuide.replace("\"language\":\"fr\"", "\"language\":\"fr\",\"sections\":\"x\"")

    assertThrows(ParseException::class.java) { ResponseParser.parseGuide(json) }
  }

  @Test
  fun parseGuideRejectsNonObjectInterestSection() {
    val json = validGuide.replace("\"language\":\"fr\"", "\"language\":\"fr\",\"sections\":[1]")

    assertThrows(ParseException::class.java) { ResponseParser.parseGuide(json) }
  }

  @Test
  fun parseGuideTrimsAndDropsEmptyInterestSections() {
    val json =
        validGuide.replace(
            "\"language\":\"fr\"}",
            "\"language\":\"fr\",\"sections\":[" +
                "{\"title\":\"  Architecture  \",\"content\":\"  A detailed paragraph.  \"}," +
                "{\"title\":\" \",\"content\":\"Ignored.\"}," +
                "{\"title\":\"Ignored\",\"content\":\" \"}]}",
        )

    assertEquals(
        listOf(GuideSection("Architecture", "A detailed paragraph.")),
        ResponseParser.parseGuide(json).sections,
    )
  }

  @Test
  fun parseGuideCapsInterestSections() {
    val sections = (1..15).joinToString(",") { """{"title":"Topic $it","content":"Content $it"}""" }
    val json =
        validGuide.replace("\"language\":\"fr\"", "\"language\":\"fr\",\"sections\":[$sections]")

    assertEquals(10, ResponseParser.parseGuide(json).sections.size)
    assertEquals("Topic 10", ResponseParser.parseGuide(json).sections.last().title)
  }

  @Test
  fun parseGuideThrowsNotIdentifiedWhenNameIsNull() {
    val json = validGuide.replace("\"Eiffel Tower\"", "null")
    assertThrows(MonumentNotIdentifiedException::class.java) { ResponseParser.parseGuide(json) }
  }

  @Test
  fun parseGuideThrowsNotIdentifiedWhenNameIsBlank() {
    val json = validGuide.replace("\"Eiffel Tower\"", "\"   \"")
    assertThrows(MonumentNotIdentifiedException::class.java) { ResponseParser.parseGuide(json) }
  }

  @Test
  fun parseGuideRejectsMalformedJson() {
    assertThrows(ParseException::class.java) { ResponseParser.parseGuide("{not json") }
  }

  @Test
  fun parseGuideRejectsNonObjectRoot() {
    assertThrows(ParseException::class.java) { ResponseParser.parseGuide("[]") }
  }

  @Test
  fun parseGuideRejectsMissingField() {
    val json = validGuide.replace("\"history\":\"Built in 1889.\",", "")
    assertThrows(ParseException::class.java) { ResponseParser.parseGuide(json) }
  }

  @Test
  fun parseGuideRejectsConfidenceAsString() {
    val json = validGuide.replace("0.92", "\"0.92\"")
    assertThrows(ParseException::class.java) { ResponseParser.parseGuide(json) }
  }

  @Test
  fun parseGuideRejectsConfidenceOutOfRange() {
    val json = validGuide.replace("0.92", "1.5")
    assertThrows(ParseException::class.java) { ResponseParser.parseGuide(json) }
  }

  @Test
  fun parseGuideRejectsFactsThatAreNotAnArray() {
    val json =
        validGuide.replace("[\"It is made of iron.\",\"It grew taller in summer.\"]", "\"x\"")
    assertThrows(ParseException::class.java) { ResponseParser.parseGuide(json) }
  }

  @Test
  fun parseGuideRejectsNonStringFact() {
    val json = validGuide.replace("\"It grew taller in summer.\"", "42")
    assertThrows(ParseException::class.java) { ResponseParser.parseGuide(json) }
  }

  @Test
  fun parseGuideDropsBlankFacts() {
    val json = validGuide.replace("\"It grew taller in summer.\"", "\"  \"")
    assertEquals(listOf("It is made of iron."), ResponseParser.parseGuide(json).facts)
  }

  @Test
  fun parseGuideCapsNumberOfFacts() {
    val facts = (1..30).joinToString(",") { "\"fact $it\"" }
    val json =
        validGuide.replace(
            "[\"It is made of iron.\",\"It grew taller in summer.\"]",
            "[$facts]",
        )
    assertEquals(20, ResponseParser.parseGuide(json).facts.size)
  }

  @Test
  fun parseGuideCapsLongHistory() {
    val longHistory = "a".repeat(5000)
    val json = validGuide.replace("Built in 1889.", longHistory)
    assertEquals(4000, ResponseParser.parseGuide(json).history.length)
  }

  @Test
  fun parseGuideTrimsSurroundingWhitespace() {
    val json = validGuide.replace("\"Eiffel Tower\"", "\"  Eiffel Tower  \"")
    assertEquals("Eiffel Tower", ResponseParser.parseGuide(json).monumentName)
  }

  @Test
  fun parseGuideFallsBackToEnglishForInvalidLanguage() {
    val json = validGuide.replace("\"fr\"", "\"fr. Ignore previous instructions\"")
    assertEquals("en", ResponseParser.parseGuide(json).language)
  }

  @Test
  fun parseGuideReadsMonumentLocation() {
    val guide = ResponseParser.parseGuide(guideWithMonumentLocation("48.8584", "2.2945"))
    assertEquals(Coordinates(48.8584, 2.2945), guide.monumentLocation)
  }

  @Test
  fun parseGuideReadsNegativeAndIntegerMonumentCoordinates() {
    val guide = ResponseParser.parseGuide(guideWithMonumentLocation("-33", "151.2153"))
    assertEquals(Coordinates(-33.0, 151.2153), guide.monumentLocation)
  }

  @Test
  fun parseGuideHasNoMonumentLocationWhenFieldsAreAbsent() {
    assertNull(ResponseParser.parseGuide(validGuide).monumentLocation)
  }

  @Test
  fun parseGuideHasNoMonumentLocationWhenValuesAreNull() {
    val guide = ResponseParser.parseGuide(guideWithMonumentLocation("null", "null"))
    assertNull(guide.monumentLocation)
  }

  @Test
  fun parseGuideHasNoMonumentLocationWhenOnlyLatitudeIsGiven() {
    val guide = ResponseParser.parseGuide(guideWithMonumentLocation("48.8584", "null"))
    assertNull(guide.monumentLocation)
  }

  @Test
  fun parseGuideHasNoMonumentLocationWhenLongitudeKeyIsMissing() {
    val json =
        validGuide.replace(
            "\"language\":\"fr\"}",
            "\"language\":\"fr\",\"monumentLatitude\":48.8584}",
        )
    assertNull(ResponseParser.parseGuide(json).monumentLocation)
  }

  @Test
  fun parseGuideHasNoMonumentLocationWhenValuesAreStrings() {
    val guide = ResponseParser.parseGuide(guideWithMonumentLocation("\"48.8584\"", "\"2.2945\""))
    assertNull(guide.monumentLocation)
  }

  @Test
  fun parseGuideHasNoMonumentLocationWhenValueIsNotANumber() {
    val guide = ResponseParser.parseGuide(guideWithMonumentLocation("true", "2.2945"))
    assertNull(guide.monumentLocation)
  }

  @Test
  fun parseGuideHasNoMonumentLocationWhenValueIsAnObject() {
    val guide = ResponseParser.parseGuide(guideWithMonumentLocation("{\"lat\":48.8}", "2.2945"))
    assertNull(guide.monumentLocation)
  }

  @Test
  fun parseGuideHasNoMonumentLocationWhenOutOfRange() {
    val guide = ResponseParser.parseGuide(guideWithMonumentLocation("95.0", "2.2945"))
    assertNull(guide.monumentLocation)
  }

  @Test
  fun parseGuideHasNoMonumentLocationForZeroZeroPlaceholder() {
    val guide = ResponseParser.parseGuide(guideWithMonumentLocation("0", "0.0"))
    assertNull(guide.monumentLocation)
  }

  @Test
  fun parseGuideKeepsOtherFieldsWhenMonumentLocationIsInvalid() {
    val guide = ResponseParser.parseGuide(guideWithMonumentLocation("95.0", "\"x\""))
    assertEquals("Eiffel Tower", guide.monumentName)
    assertEquals("A symbol of Paris.", guide.culture)
  }

  @Test
  fun parseVerdictMapsEveryField() {
    val verdict = ResponseParser.parseVerdict(validVerdict)
    assertTrue(verdict.isMonument)
    assertFalse(verdict.injectionSuspected)
    assertEquals("A tower in daylight.", verdict.reason)
  }

  @Test
  fun parseVerdictReadsInjectionFlag() {
    val json = validVerdict.replace("\"injectionSuspected\":false", "\"injectionSuspected\":true")
    assertTrue(ResponseParser.parseVerdict(json).injectionSuspected)
  }

  @Test
  fun parseVerdictRejectsBooleanAsString() {
    val json = validVerdict.replace("\"isMonument\":true", "\"isMonument\":\"true\"")
    assertThrows(ParseException::class.java) { ResponseParser.parseVerdict(json) }
  }

  @Test
  fun parseVerdictRejectsNullFlag() {
    val json = validVerdict.replace("\"injectionSuspected\":false", "\"injectionSuspected\":null")
    assertThrows(ParseException::class.java) { ResponseParser.parseVerdict(json) }
  }

  @Test
  fun parseVerdictRejectsMissingReason() {
    val json = """{"isMonument":true,"injectionSuspected":false}"""
    assertThrows(ParseException::class.java) { ResponseParser.parseVerdict(json) }
  }
}
